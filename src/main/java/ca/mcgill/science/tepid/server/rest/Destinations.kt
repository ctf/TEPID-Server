package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.Destination
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.deleteRev
import ca.mcgill.science.tepid.server.db.isSuccessful
import ca.mcgill.science.tepid.server.db.postJson
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.utils.WithLogging
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType

@Path("/destinations")
class Destinations {

    /**
     * Put destination map to CouchDb
     *
     * @param destinations map of destinations
     * @return post result
     */
    @PUT
    @RolesAllowed(ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putDestinations(destinations: Map<String, FullDestination>): String =
            CouchDb.putArray("docs", destinations.values).postJson("_bulk_docs")

    /**
     * Retrieves map of room names as [String] and their details in [Destination]
     *
     * @param ctx context
     * @return Map of data
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(USER, CTFER, ELDER)
    fun getDestinations(@Context ctx: ContainerRequestContext): Map<String, Destination> {
        val session = ctx.getSession()
        return CouchDb.getViewRows<FullDestination>("destinations")
                .map { it.toDestination(session.role) }
                .mapNotNull {
                    val id = it._id ?: return@mapNotNull null
                    id to it
                }
                .toMap()
    }

    @POST
    @Path("/{dest}")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setStatus(@PathParam("dest") id: String, ticket: DestinationTicket, @Context crc: ContainerRequestContext): String {
        val session = crc.getSession()
        ticket.user = session.user.toUser()
        val successText = "$id marked as ${if (ticket.up) "up" else "down"}"
        val response = CouchDb.updateWithResponse<FullDestination>(id) {
            up = ticket.up
            this.ticket = if (ticket.up) null else ticket
            log.info("Destination $successText.")
        }
        if (!response.isSuccessful)
            failNotFound("Could not find destination $id")
        return successText
    }


    @DELETE
    @Path("/{dest}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteQueue(@PathParam("dest") destination: String): String =
            CouchDb.path(destination).deleteRev()

    private companion object : WithLogging()

}
