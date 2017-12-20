package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.Destination
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.ViewResultMap
import ca.mcgill.science.tepid.server.util.*
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
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
    fun putDestinations(destinations: Map<String, Destination>) =
            CouchDb.putArray("docs", destinations.values).postJson("_bulk_docs")

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
        val rows = CouchDb.path("_design/main/_view", "destinations")
                .getJson<ViewResultMap<String, Destination>>()

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
        ticket.user = session.user.toUser()
        val successText = "$id marked as ${if (ticket.up) "up" else "down"}"
        val success = CouchDb.tryUpdate<Destination>(id) {
            up = ticket.up
            this.ticket = if (ticket.up) null else ticket
            log.info("Destination $successText.")
        }
        return if (!success) Response.Status.NOT_FOUND.text("Could not find destination $id")
        else Response.ok(successText).build()
    }


    @DELETE
    @Path("/{dest}")
    @RolesAllowed("elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("dest") destination: String) =
            CouchDb.path(destination).deleteRev()

    companion object : WithLogging()

}
