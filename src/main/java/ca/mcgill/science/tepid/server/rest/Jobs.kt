package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.remapExceptions
import ca.mcgill.science.tepid.server.printing.Printer
import ca.mcgill.science.tepid.server.util.Utils
import ca.mcgill.science.tepid.server.util.failBadRequest
import ca.mcgill.science.tepid.server.util.failInternal
import ca.mcgill.science.tepid.server.util.failUnauthorized
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.UriInfo

@Path("/jobs")
class Jobs {

    @POST
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun newJob(j: PrintJob, @Context ctx: ContainerRequestContext): PutResponse {
        val session = ctx.getSession()
        remapExceptions { DB.queues.read(j.queueId) }
        j.userIdentification = session.user.shortUser
        j.deleteDataOn = j.getJobExpiration()
        logger.debug(logMessage("starting new print job", "name" to j.name, "for" to session.user.longUser))
        return remapExceptions { DB.printJobs.create(j) }
    }

    @GET
    @Path("/{id}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("id") id: String, @Context uriInfo: UriInfo, @Context ctx: ContainerRequestContext): PrintJob {
        val session = ctx.getSession()
        logger.trace(logMessage("queried job", "id" to id))
        val j = DB.printJobs.read(id)
        if (session.role == USER && session.user.shortUser != j.userIdentification)
            failUnauthorized("You cannot access this resource")
        return j
    }

    @PUT
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/data")
    fun addJobData(input: InputStream, @PathParam("id") id: String): PutResponse {
        logger.debug(logMessage("receiving job data", "id" to id))
        val (success, message) = Printer.print(id, input)
        if (!success)
            failBadRequest(message)
        return PutResponse(true, id, "")
    }

    @PUT
    @Path("/{id}/refunded")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun setJobRefunded(@PathParam("id") id: String, refunded: Boolean): PutResponse {
        val result = DB.printJobs.update(id) {
            isRefunded = refunded
            logger.debug(logMessage("refunded job", "id" to id))
        }
        return PutResponse(result.isRefunded == refunded, result.getId(), result.getRev())
    }

    @POST
    @Path("/{id}/reprint")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    fun reprintJob(@PathParam("id") id: String, @Context ctx: ContainerRequestContext): String {
        val session = ctx.getSession()
        val j = DB.printJobs.read(id)
        val file = Utils.existingFile(j.file) ?: failInternal("Data for this job no longer exists")
        if (session.role == USER && session.user.shortUser != j.userIdentification)
            failUnauthorized("You cannot reprint someone else's job")
        val reprint = PrintJob(
            name = j.name,
            originalHost = "REPRINT",
            queueId = j.queueId,
            userIdentification = j.userIdentification,
            deleteDataOn = j.getJobExpiration()
        )
        logger.debug(logMessage("reprinted", "name" to reprint.name))
        val response = remapExceptions { DB.printJobs.create(reprint) }
        val newId = response.id
        Utils.startCaughtThread("Reprint $id", logger) {
            val (success, message) = Printer.print(newId, FileInputStream(file))
            if (!success)
                logger.error { "Failed to reprint job: $message" }
        }
        logger.debug(logMessage("reprinted job", "id" to id, "newId" to newId))
        return "Reprinted $id, new id $newId"
    }

    private fun PrintJob.getJobExpiration(): Long {
        val userId = userIdentification ?: return 0
        return System.currentTimeMillis() + (AuthenticationManager.queryUserDb(userId)?.jobExpiration
            ?: TimeUnit.DAYS.toMillis(7))
    }

    private companion object : Logging
}
