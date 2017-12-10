package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.Destination
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.ViewResultSet
import ca.mcgill.science.tepid.server.util.WithLogging
import ca.mcgill.science.tepid.server.util.couchdb
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
import javax.ws.rs.client.Entity
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/destinations")
class Destinations {

    /**
     * Put destination map to CouchDb
     *
     * @param destinations map of destinations
     * @return post result
     */
    @PUT
    @RolesAllowed("elder")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putDestinations(destinations: Map<String, Destination>): String {
        val root = JsonNodeFactory.instance.objectNode()
        root.putArray("docs")
                .addAll(ObjectMapper().convertValue(destinations.values, ArrayNode::class.java))
        destinations.values.forEach { log.info("Added new destination {}", it.name) }
        return couchdb.path("_bulk_docs").request().post(Entity.entity(root, MediaType.APPLICATION_JSON)).readEntity(String::class.java)
    }

    /**
     * Retrieves map of room names as [String] and their details in [Destination]
     *
     * @param ctx context
     * @return Map of data
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("elder", "ctfer", "user")
    fun getDestinations(@Context ctx: ContainerRequestContext): Map<String, Destination> {
        val session = ctx.getProperty("session") as Session
        val rows = couchdb.path("_design/main/_view").path("destinations")
                .request(MediaType.APPLICATION_JSON).get(DestinationResultSet::class.java)

        // todo, figure out why we do this
        fun Destination.update() = apply {
            if (session.role != "elder") {
                domain = null
                username = null
                password = null
            }
        }

        return rows.getValues().map(Destination::update).map { it._id to it }.toMap()
    }

    @POST
    @Path("/{dest}")
    @RolesAllowed("ctfer", "elder")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setStatus(@PathParam("dest") id: String, ticket: DestinationTicket, @Context crc: ContainerRequestContext): Response {
        val session = crc.getProperty("session") as Session
        ticket.user = session.user
        var dest: Destination? = null
        try {
            dest = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(Destination::class.java)
        } catch (ignored: Exception) {
        }

        if (dest == null) return Response.status(404).entity("Could not find destination " + id).build()

        dest.up = ticket.up
        dest.ticket = if (ticket.up) null else ticket

        couchdb.path(id).request().put(Entity.entity(dest, MediaType.APPLICATION_JSON))
        log.info("Destination {} marked as {}.", id, if (ticket.up) "up" else "down")
        return Response.ok(id + " marked as " + if (ticket.up) "up" else "down").build()
    }


    @DELETE
    @Path("/{dest}")
    @RolesAllowed("elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("dest") destination: String): String {
        val rev = couchdb.path(destination).request(MediaType.APPLICATION_JSON).get()
                .readEntity(ObjectNode::class.java).get("_rev").asText()
        log.info("Deleted queue {}.", destination)
        return couchdb.path(destination).queryParam("rev", rev).request().delete()
                .readEntity(String::class.java)
    }

    private class DestinationResultSet : ViewResultSet<String, Destination>()

    companion object : WithLogging()

}
