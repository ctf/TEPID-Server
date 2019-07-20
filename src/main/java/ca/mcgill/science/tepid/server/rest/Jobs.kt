package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.Order
import ca.mcgill.science.tepid.server.db.isSuccessful
import ca.mcgill.science.tepid.server.printing.Printer
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
import javax.ws.rs.container.ContainerRequestContext
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
        return DB.getJobsByUser(sam, Order.DESCENDING)
    }

    private fun PrintJob.getJobExpiration() =
            System.currentTimeMillis() + (SessionManager.queryUserDb(userIdentification)?.jobExpiration
                    ?: TimeUnit.DAYS.toMillis(7))

    @POST
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun newJob(j: PrintJob, @Context ctx: ContainerRequestContext): Response {
        val session = ctx.getSession()
        val queueNames: List<String> = DB.getQueues().mapNotNull { it.name }
        if (!queueNames.contains(j.queueName))
            failBadRequest("Invalid queue name ${j.queueName}")
        j.userIdentification = session.user.shortUser
        j.deleteDataOn = j.getJobExpiration()
        log.debug("Starting new print job ${j.name} for ${session.user.longUser}...")
        return DB.postJob(j)
    }

    @PUT
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    fun addJobData(input: InputStream, @PathParam("id") id: String): PutResponse {
        log.debug("Receiving job data $id")
        val (success, message) = Printer.print(id, input)
        if (!success)
            failBadRequest(message)
        return PutResponse(true, id, "")
    }

    @GET
    @Path("/job/{id}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("id") id: String, @Context uriInfo: UriInfo, @Context ctx: ContainerRequestContext): PrintJob {
        val session = ctx.getSession()
        log.trace("Queried job $id")
        val j = DB.getJob(id)
        if (session.role == USER && session.user.shortUser != j.userIdentification)
            failUnauthorized("You cannot access this resource")
        return j
    }

    @PUT
    @Path("/job/{id}/refunded")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun setJobRefunded(@PathParam("id") id: String, refunded: Boolean): PutResponse {
        val result = DB.updateJob(id) {
            isRefunded = refunded
            log.debug("Refunded job $id")
        } ?: failInternal("Could not modify refund status")
        return PutResponse(result.isRefunded == refunded, result.getId(), result.getRev())
    }

    @POST
    @Path("/job/{id}/reprint")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    fun reprintJob(@PathParam("id") id: String, @Context ctx: ContainerRequestContext): String {
        val session = ctx.getSession()
        val j = DB.getJob(id)
        val file = Utils.existingFile(j.file) ?: failInternal("Data for this job no longer exists")
        if (session.role == USER && session.user.shortUser != j.userIdentification)
            failUnauthorized("You cannot reprint someone else's job")
        val reprint = PrintJob(
                name = j.name,
                originalHost = "REPRINT",
                queueName = j.queueName,
                userIdentification = j.userIdentification,
                deleteDataOn = j.getJobExpiration()
        )
        log.debug("Reprinted ${reprint.name}")
        val response = DB.postJob(reprint)
        if (!response.isSuccessful)
            throw WebApplicationException(response)
        val content = response.entity as? ObjectNode ?: failInternal("Failed to retrieve new id")
        val newId = content.get("id")?.asText() ?: failInternal("Failed to retrieve new id")
        Utils.startCaughtThread("Reprint $id", log) {
            val (success, message) = Printer.print(newId, FileInputStream(file))
            if (!success)
                log.error("Failed to reprint job: $message")
        }
        log.debug("Reprinted job $id, new id $newId.")
        return "Reprinted $id, new id $newId"
    }

    private companion object : WithLogging()

}
