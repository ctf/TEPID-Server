package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.Destination
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.isSuccessful
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType

@Path("/destinations")
class Destinations {

    /**
     * @param destinations map of destinations
     * @return post result
     */
    @PUT
    @RolesAllowed(ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun putDestinations(destinations: Map<String, FullDestination>): String =
            DB.destinations.putDestinations(destinations)

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
        return DB.destinations.readAll()
                .mapNotNull {
                    val id = it._id ?: return@mapNotNull null
                    id to it.toDestination(session.role)
                }
                .toMap()
    }

    @POST
    @Path("/{dest}")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setStatus(@PathParam("dest") id: String, ticket: DestinationTicket, @Context crc: ContainerRequestContext): String {
        val session = crc.getSession()
        ticket.user = session.user.toUser()
        val successText = "$id marked as ${if (ticket.up) "up" else "down"}"
        val response = DB.destinations.updateDestinationWithResponse(id) {
            up = ticket.up
            this.ticket = if (ticket.up) null else ticket
            logger.info(
                    logMessage(
                            "destination status changed",
                            "status" to if (ticket.up) "up" else "down",
                            "reason" to ticket.reason,
                            "by" to ticket.user?.shortUser
                    )
            )
        }
        if (!response.isSuccessful)
            failNotFound("Could not find destination $id")
        return successText
    }

    // TODO: Response
    @DELETE
    @Path("/{dest}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteDestination(@PathParam("dest") destination: String): Unit =
            DB.destinations.deleteById(destination)

    private companion object : Logging
}
