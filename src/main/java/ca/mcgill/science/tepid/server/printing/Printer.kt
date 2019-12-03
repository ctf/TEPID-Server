package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.enums.PrintError
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.copyFrom
import ca.mcgill.science.tepid.server.util.logError
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Handler that manages all job requests
 */
object Printer : Logging {

    class PrintException(message: String) : RuntimeException(message) {
        constructor(printError: PrintError) : this(printError.display)
    }

    private val executor: ExecutorService = ThreadPoolExecutor(
        5, 30, 10, TimeUnit.MINUTES,
        ArrayBlockingQueue<Runnable>(300, true)
    )
    private val runningTasks: MutableMap<String, Future<*>> = ConcurrentHashMap()
    private val lock: Any = Any()

    /**
     * Run an task in the service
     * Upon completion, it will remove itself from [runningTasks]
     */
    private fun submit(id: String, action: () -> Unit) {
        logger.info(logMessage("submitting task", "id" to id))
        val future = executor.submit {
            try {
                action()
            } finally {
                cancel(id)
            }
        }
        runningTasks[id] = future
    }

    private fun cancel(id: String) {
        logger.warn(logMessage("cancelling task", "id" to id))
        try {
            val future = runningTasks.remove(id)
            if (future?.isDone == false)
                future.cancel(true)
        } catch (e: Exception) {
            logger.logError("failed to cancel job", e, "id" to id)
        }
    }

    private val tmpPath: String by lazy {
        if (System.getProperty("os.name").startsWith("Windows"))
            "${System.getProperty("java.io.tmpdir")}\\tepid"
        else
            "/tmp/tepid"
    }

    /**
     * Attempts to start printing the given job
     * Returns a pair of success, message
     * Note that a successful response merely indicates that the thread was properly created
     * Anything handled by the thread will not be reflected here
     */
    fun print(id: String, stream: InputStream, debug: Boolean = Config.DEBUG): Pair<Boolean, String> {

        logger.debug(logMessage("receiving job data", "id" to id))
        val tmpDir = File(tmpPath)
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            logger.error(logMessage("failed to create tmp path", "path" to tmpPath, "id" to id))
            return false to "Failed to create tmp path"
        }

        val tmpXz = File("${tmpDir.absolutePath}/$id.ps.xz")

        try {
            // todo test and validate
            // write compressed job to disk
            // adding filepath before upload ensures that the file can get deleted even if the job fails during upload
            DB.printJobs.updateJob(id) {
                file = tmpXz.absolutePath
                logger.info(logMessage("updating job with path", "id" to id, "path" to file))
            }
            tmpXz.copyFrom(stream)
            // let db know we have received data
            DB.printJobs.updateJobWithResponse(id) {
                received = System.currentTimeMillis()
                logger.info(logMessage("job file received", "id" to id, "at" to received))
            }

            submit(id, validateAndSend(tmpXz, id, debug))

            logger.trace(logMessage("returning true for job", "id" to id))
            return true to "Successfully created request $id"
        } catch (e: Exception) {
            logger.error(logMessage("job failed", "id" to id, "error" to e))
            failJob(id, "Failed to process")

            try {
                if (!tmpXz.delete()) {
                    throw IOException("Failed to delete file in cleanup of failed reception")
                }
                DB.printJobs.updateJob(id) {
                    file = null
                }
            } catch (e: Exception) {
                logger.logError("failed to delete file", e, "id" to id)
            }

            return false to "Failed to process"
        }
    }

    fun validateAndSend(tmpXz: File, id: String, debug: Boolean): () -> Unit {
        return {

            /*
                 * Note that this is a runnable that will be submitted to the executor service
                 * This block does not run in the same thread!
                 */

            // Generates a random file name with our prefix and suffix
            val tmp = File.createTempFile("tepid", ".ps")
            try {
                // decompress data
                val decompress = XZInputStream(FileInputStream(tmpXz))
                tmp.copyFrom(decompress)

                val now = System.currentTimeMillis()

                // count pages
                val psInfo = Gs.psInfo(tmp)
                logger.trace(
                    logMessage(
                        "detecting color for job",
                        "color" to if (psInfo.isColor) "color" else "monochrome",
                        "id" to id,
                        "processingTime" to "${System.currentTimeMillis() - now} ms"
                    )
                )
                logger.trace(
                    logMessage(
                        "detecting job pages",
                        "id" to id,
                        "pages" to psInfo.pages,
                        "colorPages" to psInfo.colorPages
                    )
                )

                var j2: PrintJob = updatePagecount(id, psInfo)
                val userIdentification = j2.userIdentification ?: throw PrintException("Could not retrieve userIdentification {\"job\":\"${j2.getId()}\", \"userIdentification\":\"${j2.userIdentification}\"}")
                val user = AuthenticationManager.queryUser(userIdentification)
                    ?: throw PrintException("Could not retrieve user {\"job\":\"${j2.getId()}\"}")

                validateColorAvailable(user, j2, psInfo)

                validateAvailableQuota(user, j2, psInfo)

                validateJobSize(j2)

                // add job to the queue
                logger.trace(logMessage("trying to assign destination", "job" to j2.getId()))
                j2 = QueueManager.assignDestination(j2) ?: throw RuntimeException("TODO REFACTORING WORK IN QueueManager.assignDestination")
                // todo check destination field
                val destination = j2.destination
                        ?: throw PrintException(PrintError.INVALID_DESTINATION)

                val dest = DB.destinations.read(destination)
                if (sendToSMB(tmp, dest, debug)) {
                    DB.printJobs.updateJob(id) {
                        printed = System.currentTimeMillis()
                    }
                    logger.info(logMessage("sent job to destination", "id" to j2._id))
                } else {
                    throw PrintException("Could not send to destination")
                }
            } catch (e: Exception) {
                logger.logError("job failed", e, "id" to id)
                val msg = (e as? PrintException)?.message
                    ?: "Failed to process"
                failJob(id, msg)
            } finally {
                tmp.delete()
                logger.trace(logMessage("successfully deleted tmp file", "id" to id, "file" to tmp.absoluteFile))
            }
        }
    }

    // update page count and status in db
    private fun updatePagecount(id: String, psInfo: PsData): PrintJob {
        try {
            return DB.printJobs.update(id) {
                this.pages = psInfo.pages
                this.colorPages = psInfo.colorPages
                this.processed = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            throw PrintException(logError("Could not update", e))
        }
    }

    // check if user has color printing enabled
    private fun validateColorAvailable(user: FullUser, job: PrintJob, psInfo: PsData) {
        logger.trace(logMessage("testing for color for job", "id" to job.getId()))
        if (psInfo.colorPages > 0 && !user.colorPrinting)
            throw PrintException(PrintError.COLOR_DISABLED)
    }

    // check if user has sufficient quota to print this job
    private fun validateAvailableQuota(user: FullUser, job: PrintJob, psInfo: PsData) {
        logger.trace(logMessage("testing for quota for job", "id" to job.getId()))
        if (QuotaCounter.getQuotaData(user).quota < psInfo.pages + psInfo.colorPages * 2)
            throw PrintException(PrintError.INSUFFICIENT_QUOTA)
    }

    // check if job is below max pages
    fun validateJobSize(j2: PrintJob) {
        logger.trace(logMessage("testing for job length", "id" to j2.getId()))
        if (
            Config.MAX_PAGES_PER_JOB > 0 && // valid max pages per job
            j2.pages > Config.MAX_PAGES_PER_JOB
        ) {
            throw PrintException(PrintError.TOO_MANY_PAGES)
        }
    }

    internal fun sendToSMB(f: File, destination: FullDestination, debug: Boolean): Boolean {
        if (!f.isFile) {
            logger.error("File does not exist at ${f.absolutePath}")
            return false
        }

        logger.trace(
            logMessage(
                "sending file",
                "name" to f.name,
                "length" to f.length(),
                "destination" to destination.name
            )
        )
        if (debug || destination.path?.isBlank() != false) {
            // this is a dummy destination
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
            }
            logger.info(logMessage("sent dummy job", "name" to f.name, "destination" to destination.name))
            return true
        }
        try {
            val p = ProcessBuilder(
                "smbclient", "//${destination.path}", destination.password, "-c",
                "print ${f.absolutePath}", "-U", destination.domain + "\\${destination.username}", "-mSMB3"
            ).start()
            p.waitFor()
        } catch (e: Exception) {
            logger.error("File ${f.name} failed", e)
            return false
        }
        logger.trace("File ${f.name} sent to ${destination.name}")
        return true
    }

    /**
     * Update job db and cancel executor
     */
    private fun failJob(id: String, error: String) {
        DB.printJobs.updateJobWithResponse(id) {
            fail(error)
        }
        cancel(id)
    }

    fun clearOldJobs() {
        synchronized(lock) {
            try {
                val jobs = DB.printJobs.getOldJobs()
                jobs.forEach { j ->
                    DB.printJobs.updateJob(j.getId()) {
                        fail("Timed out")
                        val id = _id ?: return@updateJob // TODO: if ID is null, how did we get here?
                        cancel(id)
                    }
                }
            } catch (e: Exception) {
                logger.error("General failure", e)
            }
        }
    }
}