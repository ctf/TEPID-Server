package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.Utils
import ca.mcgill.science.tepid.models.data.ViewResultSet
import ca.mcgill.science.tepid.server.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.util.WithLogging
import ca.mcgill.science.tepid.server.util.couchdb
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.InputStream
import java.util.*
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
import javax.ws.rs.client.Entity
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo

@Path("/queues")
class Queues {

    @PUT
    @RolesAllowed("elder")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putQueues(queues: List<PrintQueue>): String {
        val root = JsonNodeFactory.instance.objectNode()
        root.putArray("docs").addAll(ObjectMapper().convertValue(queues, ArrayNode::class.java))
        queues.forEach { log.info("Added new queue {}.", it.name) }
        return couchdb.path("_bulk_docs").request().post(Entity.entity(root, MediaType.APPLICATION_JSON)).readEntity(String::class.java)
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getQueues(): List<PrintQueue> {
        val rows = couchdb.path("_design/main/_view").path("queues")
                .request(MediaType.APPLICATION_JSON).get(QueueResultSet::class.java).rows
        return rows.map { it.value }
    }

    @JsonInclude(Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class QueueResultSet(var rows: List<Row>) {
        data class Row(var value: PrintQueue)
    }

    @GET
    @Path("/{queue}")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("queue") queue: String, @QueryParam("limit") @DefaultValue("-1") limit: Int): Collection<PrintJob> {
        //TODO limit param no longer user, should be replaced by from param in client
        // this should get all jobs in "queue" from the past 2 days
        val from = Date().time - 1000 * 60 * 60 * 24 * 2 // from 2 days ago
        val tgt = couchdb
                .path("_design/temp/_view")
                .path("JobsByQueueAndTime")
                .queryParam("descending", true)
                .queryParam("startkey", "[\"$queue\",%7B%7D]")
                .queryParam("endkey", "[\"$queue\",$from]")
        val rows = tgt.request(MediaType.APPLICATION_JSON).get(JobResultSet::class.java).rows
        return rows.map { it.value }
    }

    @GET
    @Path("/{queue}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getJob(@PathParam("queue") queue: String, @PathParam("id") id: String): PrintJob? {
        val j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob::class.java)
        return if (!j.queueName.equals(queue, ignoreCase = true)) null else j
    }

    @DELETE
    @Path("/{queue}")
    @RolesAllowed("elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("queue") queue: String): String {
        val rev = couchdb.path(queue).request(MediaType.APPLICATION_JSON).get().readEntity(ObjectNode::class.java).get("_rev").asText()
        log.info("Deleted queue {}", queue)
        return couchdb.path(queue).queryParam("rev", rev).request().delete()
                .readEntity(String::class.java)
    }

    @GET
    @Path("/{queue}/{id}/{file}")
    fun getAttachment(@PathParam("queue") queue: String, @PathParam("id") id: String, @PathParam("file") file: String): Response {
        try {
            val j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob::class.java)
            if (j == null || !j.queueName.equals(queue, ignoreCase = true)) {
                return Response.status(404).entity("Could not find job $id in queue $queue").type(MediaType.TEXT_PLAIN).build()
            }
            val resp = couchdb.path(id).path(file).request().get()
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
        var target = couchdb.path("_changes").queryParam("filter", "main/byQueue")
                .queryParam("queue", queue)
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
        var target = couchdb.path("_changes").queryParam("filter", "main/byQueue")
        val qp = uriInfo.queryParameters
        if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"))
        var since = Utils.intValue(qp.getFirst("since"), -1)
        if (since < 0) {
            since = couchdb.path("_changes").queryParam("filter", "main/byQueue").queryParam("since", 0).request().get(ObjectNode::class.java).get("last_seq").asInt()
        }
        target = target.queryParam("since", since)
        return target.request().get(String::class.java)
    }

    private class JobResultSet : ViewResultSet<List<String>, PrintJob>()

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
