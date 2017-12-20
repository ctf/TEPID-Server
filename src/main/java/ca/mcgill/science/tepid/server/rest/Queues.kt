package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.ViewResultMap
import ca.mcgill.science.tepid.server.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.util.*
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
    @RolesAllowed("elder")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putQueues(queues: List<PrintQueue>): String {
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
        return CouchDb.getViewRows("_design/temp/_view", "JobsByQueueAndTime") {
            query("descending" to true,
                    "startkey" to "[\"$queue\",%7B%7D]",
                    "endkey" to "[\"$queue\",$from]")
        }
    }

    @GET
    @Path("/{queue}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("queue") queue: String, @PathParam("id") id: String): PrintJob? {
        val j = CouchDb.path(id).getJson<PrintJob>()
        return if (!j.queueName.equals(queue, ignoreCase = true)) null else j
    }

    @DELETE
    @Path("/{queue}")
    @RolesAllowed("elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("queue") queue: String) =
            CouchDb.path(queue).deleteRev()

    @GET
    @Path("/{queue}/{id}/{file}")
    fun getAttachment(@PathParam("queue") queue: String, @PathParam("id") id: String, @PathParam("file") file: String): Response {
        try {
            val j = CouchDb.path(id).getJson<PrintJob>()
            //todo check if null ever happens
            if (j == null || !j.queueName.equals(queue, ignoreCase = true)) {
                return Response.status(404).entity("Could not find job $id in queue $queue").type(MediaType.TEXT_PLAIN).build()
            }
            val resp = CouchDb.path(id, file).request().get()
            if (resp.status == 200) {
                return Response.ok(resp.readEntity(InputStream::class.java), resp.getMediaType()).build()
            }
        } catch (e: Exception) {
        }

        return Response.status(404).entity("Could not find $file for job $id").type(MediaType.TEXT_PLAIN).build()
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
            ar.resume(changes)
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

    companion object : WithLogging()
}
