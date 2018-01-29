package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.gs.GS
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo


@Path("/jobs")
class Jobs {

    @GET
    @Path("/{sam}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("sam") sam: String, @Context req: ContainerRequestContext): Collection<PrintJob> {
        val session = req.getSession()
        if (session.role == USER && session.user.shortUser != sam) {
            return emptyList()
        }
        val data = CouchDb.getViewRows<PrintJob>("byUser") {
            query("key" to "\"$sam\"")
        }

        // todo why are we sorting stuff in java
        val out = TreeSet<PrintJob>()
        out.addAll(data)
        return out
    }

    @POST
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun newJob(j: PrintJob, @Context ctx: ContainerRequestContext): Response {
        val session = ctx.getSession()
        j.userIdentification = session.user.shortUser
        j.deleteDataOn = System.currentTimeMillis() + SessionManager.queryUserCache(j.userIdentification)!!.jobExpiration
        log.debug("Starting new print job ${j.name} for ${session.user.longUser}...")
        return CouchDb.target.postJson(j)
    }

    /**
     * Returns true if monochrome was detected,
     * or false if color was detected
     * Defaults to monochrome
     */
    private fun BufferedReader.isMonochrome(): Boolean {
        for (line in lines()) {
            if (line.contains("/ProcessColorModel /DeviceGray"))
                return true
            if (line.contains("/ProcessColorModel /DeviceCMYK"))
                return false
        }
        return true
    }

    @PUT
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{id}")
    fun addJobData(input: InputStream, @PathParam("id") id: String): String {
        log.debug("Receiving job data $id")
        val tmpPath = if (System.getProperty("os.name").startsWith("Windows"))
            "${System.getProperty("java.io.tmpdir")}\\tepid"
        else
            "/tmp/tepid"
        val tmpDir = File(tmpPath)
        if (!tmpDir.exists() && !tmpDir.mkdirs())
            return "Failed to create tmp path $tmpPath"
        try {
            // todo test and validate
            //write compressed job to disk
            val tmpXz = File("${tmpDir.absolutePath}/$id.ps.xz")
            tmpXz.copyFrom(input)
            //let db know we have received data
            CouchDb.updateWithResponse<PrintJob>(id) {
                file = tmpXz.absolutePath
                log.info("Updating job $id with path $file")
                received = System.currentTimeMillis()
            }
            val processing = object : Thread("Job Processing for $id") {
                override fun run() {
                    try {
                        //decompress data
                        val tmp = File.createTempFile("tepid", ".ps")
                        val decompress = XZInputStream(FileInputStream(tmpXz))
                        tmp.copyFrom(decompress)

                        // Detect PostScript monochrome instruction
                        val br = BufferedReader(FileReader(tmp.absolutePath))
                        val now = System.currentTimeMillis()
                        val psMonochrome = br.isMonochrome()
                        log.trace("Detected ${if (psMonochrome) "monochrome" else "colour"} for job $id in ${System.currentTimeMillis() - now} ms")
                        //count pages
                        val inkCov = GS.inkCoverage(tmp)
                        val color = if (psMonochrome) 0
                        else inkCov.filter { !it.monochrome }.size

                        //update page count and status in db
                        var j2 = CouchDb.update<PrintJob>(id) {
                            pages = inkCov.size
                            colorPages = color
                            processed = System.currentTimeMillis()
                        }

                        if (j2 == null) {
                            failJob(id, "Could not update")
                            processingThreads.remove(id)
                            return
                        }

                        //check if user has color printing enabled
                        if (color > 0 && (SessionManager.queryUser(j2.userIdentification, null)?.colorPrinting != true)) {
                            failJob(id, "Color disabled")
                        } else {
                            //check if user has sufficient quota to print this job
                            if (Users().getQuota(j2.userIdentification) < inkCov.size - color + color * 3) {
                                failJob(id, "Insufficient quota")
                            } else {
                                //add job to the queue
                                j2 = QueueManager.assignDestination(id)
                                //todo check destination field
                                val dest = CouchDb.path(j2.destination!!).getJson<FullDestination>()
                                if (sendToSMB(tmp, dest)) {
                                    j2.printed = System.currentTimeMillis()
                                    CouchDb.path(id).putJson(j2)
                                    log.info("${j2._id} sent to destination")
                                } else {
                                    failJob(id, "Could not send to destination")
                                }
                            }
                        }
                        tmp.delete()
                    } catch (e: Exception) {
                        log.error("Failed to process job $id", e)
                        failJob(id, "Exception during processing")
                    } finally {
                        processingThreads.remove(id)
                    }
                }
            }
            processingThreads.put(id, processing)
            processing.start()
            log.debug("Job data for {} successfully uploaded.", id)
            return "Job data for $id successfully uploaded"
        } catch (e: IOException) {
            log.error("Failed to upload job $id, e")
            failJob(id, " Exception during upload")
        }

        return "Job data upload for $id FAILED"
    }

    fun failJob(id: String, error: String) {
        CouchDb.updateWithResponse<PrintJob>(id) {
            fail(error)
        }
        log.error("Job $id failed: $error.")
    }

    //	public static boolean sendToSMB(File f, Destination destination) {
    //		if (destination.getPath() == null || destination.getPath().trim().isEmpty()) {
    //			//this is a dummy destination
    //			try {
    //				Thread.sleep(4000);
    //			} catch (InterruptedException e) {
    //			}
    //			return true;
    //		}
    //		System.setProperty("jcifs.smb.client.useNTSmbs", "false");
    //		try {
    //			String userName = destination.getUsername(), password = destination.getPassword();
    //			SmbFileOutputStream sfos = new SmbFileOutputStream(new SmbFile("smb://" + destination.getPath(), new NtlmPasswordAuthentication(destination.getDomain(), userName, password)));
    //			Files.copy(f.toPath(), sfos);
    //			sfos.close();
    //		} catch (IOException e) {
    //			return false;
    //		}
    //		return true;
    //	}

    @GET
    @Path("/job/{id}/_changes")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getChanges(@PathParam("id") id: String, @Context uriInfo: UriInfo, @Suspended ar: AsyncResponse, @Context ctx: ContainerRequestContext) {
        val session = ctx.getSession()
        val j = CouchDb.path(id).getJson<PrintJob>()
        if (session.role == USER && session.user.shortUser != j.userIdentification) {
            ar.resume(Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").type(MediaType.TEXT_PLAIN).build())
        }

        var target = CouchDb.path("_changes")
                .query("filter" to "main/byJob",
                        "job" to id)
        val qp = uriInfo.queryParameters
        if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"))
        if (qp.containsKey("since")) target = target.queryParam("since", qp.getFirst("since"))
        //TODO find a way to make this truly asynchronous
        val changes = target.request().get(String::class.java)
        if (!ar.isDone && !ar.isCancelled) {
            try {
                ar.resume(changes)
            } catch (e: Exception) {
                log.error("Failed to emit job _changes for $id: ${e.message}")
            }
        }
    }

    @GET
    @Path("/job/{id}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("id") id: String, @Context uriInfo: UriInfo, @Context ctx: ContainerRequestContext): PrintJob {
        val session = ctx.getSession()
        log.trace("Queried job $id")
        val j = CouchDb.path(id).getJson<PrintJob>()
        if (session.role == USER && session.user.shortUser != j.userIdentification)
            failUnauthorized("You cannot access this resource")
        return j
    }

    @PUT
    @Path("/job/{id}/refunded")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun setJobRefunded(@PathParam("id") id: String, @Context ctx: ContainerRequestContext, refunded: Boolean): Boolean {
        val result = CouchDb.update<PrintJob>(id) {
            isRefunded = refunded
            log.debug("Refunded job $id")
        }
        return result != null
    }

    @POST
    @Path("/job/{id}/reprint")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    fun reprintJob(@PathParam("id") id: String, @Context ctx: ContainerRequestContext): String {
        val session = ctx.getSession()
        val j = CouchDb.path(id).getJson<PrintJob>()
        val file = Utils.existingFile(j.file) ?: failInternal("Data for this job no longer exists")
        if (session.role == USER && session.user.shortUser != j.userIdentification)
            failUnauthorized("You cannot reprint someone else's job")
        val reprint = PrintJob()
        reprint.name = j.name
        reprint.originalHost = "REPRINT"
        reprint.queueName = j.queueName
        reprint.userIdentification = j.userIdentification
        reprint.deleteDataOn = System.currentTimeMillis() + (SessionManager.queryUserCache(j.userIdentification)?.jobExpiration
                ?: 20000)
        log.debug("Reprinted ${reprint.name}")
        val response = CouchDb.target.postJson(reprint)
        if (!response.isSuccessful)
            throw WebApplicationException(response)
        val content = response.readEntity(ObjectNode::class.java)
        val newId = content.get("id")?.asText() ?: failInternal("Failed to retrieve new id")
        Utils.startCaughtThread("Reprint $id") { addJobData(FileInputStream(file), newId) }
        log.debug("Reprinted job $id, new id $newId.")
        return "Reprinted $id new id $newId"
    }

    companion object : WithLogging() {

        val processingThreads: MutableMap<String, Thread> = ConcurrentHashMap()

        fun sendToSMB(f: File, destination: FullDestination): Boolean {
            if (!f.isFile) {
                log.error("File does not exist at ${f.absolutePath}")
                return false
            }

            log.trace("Sending file ${f.name} ${f.length()} to ${destination.name}")
            if (destination.path?.isBlank() != false) {
                //this is a dummy destination
                try {
                    Thread.sleep(4000)
                } catch (e: InterruptedException) {
                }
                log.info("Sent ${f.name} to ${destination.name}")
                return true
            }
            try {
                val p = ProcessBuilder("smbclient", "//" + destination.path, destination.password, "-c",
                        "print " + f.absolutePath, "-U", destination.domain + "\\" + destination.username, "-mSMB3").start()
                p.waitFor()
            } catch (e: IOException) {
                log.error("File ${f.name} failed with ${e.message}")
                return false
            } catch (e: InterruptedException) {
                log.error("File ${f.name} interrupted ${e.message}")
                return false
            }
            log.trace("File ${f.name} sent to ${destination.name}")
            return true
        }
    }

}