package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.Order
import ca.mcgill.science.tepid.server.db.remapExceptions
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.util.failBadRequest
import ca.mcgill.science.tepid.server.util.toIdentifiedCollection
import org.apache.logging.log4j.kotlin.Logging
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Path("/queues")
class Queues {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getQueues(): Map<String, PrintQueue> =
        remapExceptions { DB.queues.readAll() }.toIdentifiedCollection()

    @POST
    @RolesAllowed(ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun newQueue(queue: PrintQueue): PutResponse =
        remapExceptions { DB.queues.create(queue) }

    @GET
    @Path("/{queue}")
    @RolesAllowed(ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun getQueue(@PathParam("queue") id: String): PrintQueue =
        remapExceptions { DB.queues.read(id) }

    @PUT
    @Path("/{queue}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun putQueue(@PathParam("queue") id: String, queue: PrintQueue): PutResponse =
        remapExceptions { DB.queues.put(queue.apply { this._id = id }) }

    @DELETE
    @Path("/{queue}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("queue") id: String): Unit =
        remapExceptions { DB.queues.deleteById(id) }

    @GET
    @Path("/{queue}/jobs")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("queue") queue: String, @QueryParam("limit") @DefaultValue("-1") limit: Int): Collection<PrintJob> {
        // TODO limit param no longer user, should be replaced by from param in client
        // this should get all jobs in "queue" from the past 2 days
        val twoDaysMs = 1000 * 60 * 60 * 24 * 2L
        return remapExceptions { DB.printJobs.getJobsByQueue(queue, maxAge = twoDaysMs, sortOrder = Order.DESCENDING) }
    }

    @GET
    @Path("/{queue}/jobs/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("queue") queue: String, @PathParam("id") id: String): PrintJob {
        val j = remapExceptions { DB.printJobs.read(id) }
        if (!j.queueId.equals(queue, ignoreCase = true))
            failBadRequest("Job queue does not match $queue")
        return j
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/loadbalancers")
    fun getLoadBalancers(): List<String> {
        return LoadBalancer.getLoadBalancerFactories().map { e -> e.key }
    }

    private companion object : Logging
}
