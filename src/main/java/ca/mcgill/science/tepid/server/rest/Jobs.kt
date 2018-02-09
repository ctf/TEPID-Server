package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.printer.Printer
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStream
import java.util.*
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
        val (success, message) = Printer.print(id, input)
        if (!success)
            failBadRequest(message)
        return message
    }

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
    fun setJobRefunded(@PathParam("id") id: String, refunded: Boolean): Boolean {
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

    private companion object : WithLogging()

}
