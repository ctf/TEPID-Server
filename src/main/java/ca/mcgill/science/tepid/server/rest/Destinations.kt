package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.Destination
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.remapExceptions
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.getSession
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
        return remapExceptions {
            DB.destinations.readAll()
                .mapNotNull {
                    val id = it._id ?: return@mapNotNull null
                    id to it.toDestination(session.role)
                }
                .toMap()
        }
    }

    @POST
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun newDestination(destination: FullDestination): PutResponse =
        remapExceptions { DB.destinations.put(destination) }

    @GET
    @Path("/{dest}")
    @RolesAllowed(USER, CTFER, ELDER)
    fun getDestination(@Context ctx: ContainerRequestContext, @PathParam("dest") id: String): Destination {
        val session = ctx.getSession()
        return remapExceptions { DB.destinations.read(id).toDestination(session.role) }
    }

    @PUT
    @Path("/{dest}")
    @RolesAllowed(ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun putDestination(@PathParam("dest") id: String, destination: FullDestination): PutResponse =
        remapExceptions { DB.destinations.put(destination) }

    @DELETE
    @Path("/{dest}")
    @RolesAllowed(ELDER)
    fun deleteDestination(@PathParam("dest") id: String): Unit =
        remapExceptions { DB.destinations.deleteById(id) }

    @POST
    @Path("/{dest}/ticket")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setTicket(@PathParam("dest") id: String, ticket: DestinationTicket, @Context crc: ContainerRequestContext): String {
        val session = crc.getSession()
        ticket.user = session.user.toUser()
        val successText = "$id marked as ${if (ticket.up) "up" else "down"}"

        try {
            DB.destinations.update(id) {
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
        } catch (e: Exception) {
            failNotFound("Could not find destination $id")
        }
        return successText
    }

    @DELETE
    @Path("/{dest}/ticket")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteTicket(@PathParam("dest") destination: String): Unit =
        remapExceptions { DB.destinations.deleteById(destination) }

    private companion object : Logging
}
