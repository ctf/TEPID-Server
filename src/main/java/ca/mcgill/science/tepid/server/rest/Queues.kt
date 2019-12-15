package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.Order
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.util.failBadRequest
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import java.io.InputStream
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/queues")
class Queues {

    @PUT
    @RolesAllowed(ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putQueues(queues: List<PrintQueue>): Response {
        val response = DB.queues.putQueues(queues)
        queues.forEach { logger.info(logMessage("added new queue", "name" to it.name)) }
        return response
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getQueues(): List<PrintQueue> = DB.queues.readAll()

    @GET
    @Path("/{queue}/jobs")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("queue") queue: String, @QueryParam("limit") @DefaultValue("-1") limit: Int): Collection<PrintJob> {
        // TODO limit param no longer user, should be replaced by from param in client
        // this should get all jobs in "queue" from the past 2 days
        val twoDaysMs = 1000 * 60 * 60 * 24 * 2L
        return DB.printJobs.getJobsByQueue(queue, maxAge = twoDaysMs, sortOrder = Order.DESCENDING)
    }

    @GET
    @Path("/{queue}/jobs/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("queue") queue: String, @PathParam("id") id: String): PrintJob {
        val j = DB.printJobs.read(id)
        if (!j.queueId.equals(queue, ignoreCase = true))
            failBadRequest("Job queue does not match $queue")
        return j
    }

    @DELETE
    @Path("/{queue}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("queue") queue: String): String =
            DB.queues.deleteQueue(queue)

    @GET
    @Path("/{queue}/jobs/{id}/{file}")
    fun getAttachment(@PathParam("queue") queue: String, @PathParam("id") id: String, @PathParam("file") file: String): InputStream {
        try {
            val j = DB.printJobs.read(id)
            if (!j.queueId.equals(queue, ignoreCase = true))
                failNotFound("Could not find job $id in queue $queue")
            return DB.printJobs.getJobFile(id, file)!!
        } catch (ignored: Exception) {
        }
        failNotFound("Could not find $file for job $id")
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/loadbalancers")
    fun getLoadBalancers(): List<String> {
        return LoadBalancer.getLoadBalancerFactories().map { e -> e.key }
    }

    private companion object : Logging
}
