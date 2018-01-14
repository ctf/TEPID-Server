package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.server.gs.GS
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.utils.WithLogging
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
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("sam") sam: String, @Context req: ContainerRequestContext): Collection<PrintJob> {
        val session = req.getProperty("session") as Session
        if (session.role == "user" && session.user.shortUser != sam) {
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
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun newJob(j: PrintJob, @Context ctx: ContainerRequestContext): String {
        val session = ctx.getSession(log) ?: return "Bad session" // todo change output
        j.userIdentification = (session).user.shortUser
        j.deleteDataOn = System.currentTimeMillis() + SessionManager.queryUserCache(j.userIdentification)!!.jobExpiration
        log.debug("Starting new print job ${j.name} for ${session.user.longUser}...")
        return CouchDb.target.postJson(j)
    }

    @PUT
    @RolesAllowed("user", "ctfer", "elder")
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
            CouchDb.update<PrintJob>(id) {
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
                        val br = BufferedReader(FileReader(tmp))
                        var psMonochrome = false
                        br.lines().forEach {
                            if (it.contains("/ProcessColorModel /DeviceGray")) {
                                psMonochrome = true
                                return@forEach
                            }
                            if (it.contains("/ProcessColorModel /DeviceCMYK")) {
                                psMonochrome = false
                                return@forEach
                            }
                        }

                        //count pages
                        val inkCov = GS.inkCoverage(tmp)
                        val color = if (psMonochrome) 0
                        else inkCov.filter { !it.monochrome }.size

                        //update page count and status in db
                        var j2 = CouchDb.update<PrintJob>(id) {
                            pages = inkCov.size
                            colorPages = color
                            processed = System.currentTimeMillis()
                            log.info("Setting stats for job $id: $pages pages, $colorPages color")
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
                    }

                    processingThreads.remove(id)
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
        CouchDb.update<PrintJob>(id) {
            setFailed(error)
        }
        log.debug("Job $id failed: $error.")
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
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun getChanges(@PathParam("id") id: String, @Context uriInfo: UriInfo, @Suspended ar: AsyncResponse, @Context ctx: ContainerRequestContext) {
        val session = ctx.getSession(log) ?: return
        val j = CouchDb.jsonFromId<PrintJob>(id)
        if (session.role == "user" && session.user.shortUser != j.userIdentification) {
            ar.resume(Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").type(MediaType.TEXT_PLAIN).build())
        }

        var target = CouchDb.path("changes")
                .query("filter" to "main/byJob",
                        "job" to id)
        val qp = uriInfo.queryParameters
        if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"))
        if (qp.containsKey("since")) target = target.queryParam("since", qp.getFirst("since"))
        //TODO find a way to make this truly asynchronous
        val changes = target.request().get(String::class.java)
        if (!ar.isDone && !ar.isCancelled) {
            ar.resume(changes)
        }
    }

    @GET
    @Path("/job/{id}")
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("id") id: String, @Context uriInfo: UriInfo, @Context ctx: ContainerRequestContext): Response {
        val session = ctx.getSession(log) ?: return INVALID_SESSION_RESPONSE
        val j = CouchDb.jsonFromId<PrintJob>(id)
        return if (session.role == "user" && session.user.shortUser != j.userIdentification) unauthorizedResponse("You cannot access this resource")
        else Response.ok(j).build()
    }

    @PUT
    @Path("/job/{id}/refunded")
    @RolesAllowed("ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun setJobRefunded(@PathParam("id") id: String, @Context ctx: ContainerRequestContext, refunded: Boolean): Response {
        ctx.getSession(log) ?: return INVALID_SESSION_RESPONSE // todo check if  validation is necessary
        CouchDb.update<PrintJob>(id) {
            isRefunded = refunded
            log.debug("Refunded job $id")
        }
        return Response.ok().build()
    }

    @POST
    @Path("/job/{id}/reprint")
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.TEXT_PLAIN)
    fun reprintJob(@PathParam("id") id: String, @Context ctx: ContainerRequestContext): Response {
        val session = ctx.getSession(log) ?: return INVALID_SESSION_RESPONSE
        val j = CouchDb.path(id).getJson<PrintJob>()
        val file = Utils.existingFile(j.file) ?: return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Data for this job no longer exists").type(MediaType.TEXT_PLAIN).build()
        if (session.role == "user" && session.user.shortUser != j.userIdentification)
            return unauthorizedResponse("You cannot reprint someone else's job")
        val reprint = PrintJob()
        reprint.name = j.name
        reprint.originalHost = "REPRINT"
        reprint.queueName = j.queueName
        reprint.userIdentification = j.userIdentification
        reprint.deleteDataOn = System.currentTimeMillis() + SessionManager.queryUserCache(j.userIdentification)!!.jobExpiration
        println(reprint)
        val newId = CouchDb.target.postJsonGetObj(reprint).get("id").asText()
        Utils.startCaughtThread("Reprint $id") { addJobData(FileInputStream(file), newId) }
        log.debug("Reprinted job {}, new id {}.", id, newId)
        return Response.ok("Reprinted $id new id $newId").build()
    }

    companion object : WithLogging() {

        val processingThreads: MutableMap<String, Thread> = ConcurrentHashMap()

        fun sendToSMB(f: File, destination: FullDestination): Boolean {
            if (destination.path?.trim { it <= ' ' }?.isNotEmpty() != false) {
                //this is a dummy destination
                try {
                    Thread.sleep(4000)
                } catch (e: InterruptedException) {
                }

                return true
            }
            System.setProperty("jcifs.smb.client.useNTSmbs", "false")
            try {
                val p = ProcessBuilder("smbclient", "//" + destination.path, destination.password, "-c",
                        "print " + f.absolutePath, "-U", destination.domain + "\\" + destination.username).start()
                p.waitFor()
            } catch (e: IOException) {
                return false
            } catch (e: InterruptedException) {
                return false
            }

            return true
        }
    }

}
