package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.db.*
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.utils.WithLogging
import java.io.InputStream
import java.util.*
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo

@Path("/queues")
class Queues {

    private val changeTarget
        get() = CouchDb.path("_changes").query("filter" to "main/byQueue")

    @PUT
    @RolesAllowed(ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putQueues(queues: List<PrintQueue>): Response {
        val root = CouchDb.putArray("docs", queues)
        queues.forEach { log.info("Added new queue {}.", it.name) }
        return CouchDb.path("_bulk_docs").postJson(root)
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getQueues(): List<PrintQueue> = CouchDb.getViewRows("queues")

    @GET
    @Path("/{queue}")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("queue") queue: String, @QueryParam("limit") @DefaultValue("-1") limit: Int): Collection<PrintJob> {
        //TODO limit param no longer user, should be replaced by from param in client
        // this should get all jobs in "queue" from the past 2 days
        val from = Date().time - 1000 * 60 * 60 * 24 * 2 // from 2 days ago
        return CouchDb.getViewRows("_design/main/_view", "jobsByQueueAndTime") {
            query("descending" to true,
                    "startkey" to "[\"$queue\",%7B%7D]",
                    "endkey" to "[\"$queue\",$from]")
        }
    }

    @GET
    @Path("/{queue}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("queue") queue: String, @PathParam("id") id: String): PrintJob {
        val j = CouchDb.path(id).getJson<PrintJob>()
        if (!j.queueName.equals(queue, ignoreCase = true))
            failBadRequest("Job queue does not match $queue")
        return j
    }

    @DELETE
    @Path("/{queue}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("queue") queue: String): String =
            CouchDb.path(queue).deleteRev()

    @GET
    @Path("/{queue}/{id}/{file}")
    fun getAttachment(@PathParam("queue") queue: String, @PathParam("id") id: String, @PathParam("file") file: String): InputStream {
        try {
            val j = CouchDb.path(id).getJson<PrintJob>()
            if (!j.queueName.equals(queue, ignoreCase = true))
                failNotFound("Could not find job $id in queue $queue")
            val resp = CouchDb.path(id, file).request().get()
            if (resp.isSuccessful)
                return resp.readEntity(InputStream::class.java)
        } catch (ignored: Exception) {
        }
        failNotFound("Could not find $file for job $id")
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{queue}/_changes")
    fun getChanges(@PathParam("queue") queue: String, @Context uriInfo: UriInfo, @Suspended ar: AsyncResponse) {
        var target = changeTarget.query("queue" to queue)
        val qp = uriInfo.queryParameters
        if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"))
        if (qp.containsKey("since")) target = target.queryParam("since", qp.getFirst("since"))
        val changes = target.request().get(String::class.java)
        if (!ar.isDone && !ar.isCancelled) {
            log.info("Emitting changes of length ${changes.length}")
            try {
                ar.resume(changes)
            } catch (e: Exception) {
                log.error("Failed to emit queue _changes ${e.message}")
            }
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/_changes")
    fun getChanges(@Context uriInfo: UriInfo): String {
        var target = changeTarget
        val qp = uriInfo.queryParameters
        if (qp.containsKey("feed")) target = target.query("feed" to qp.getFirst("feed"))
        var since = qp.getFirst("since").toIntOrNull() ?: -1
        if (since < 0) {
            since = changeTarget.query("since" to 0).getObject().get("last_seq").asInt()
        }
        target = target.queryParam("since", since)
        return target.getString()
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/loadbalancers")
    fun getLoadBalancers(): List<String> {
        return LoadBalancer.loadBalancers
                .filter { LoadBalancer::class.java.isAssignableFrom(it) }
                .map { it.simpleName }
    }

    private companion object : WithLogging()
}
