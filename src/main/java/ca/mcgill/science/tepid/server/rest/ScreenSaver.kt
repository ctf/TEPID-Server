package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.Destination
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.Order
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.utils.WithLogging
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType


@Path("/screensaver")
class ScreenSaver {

    /**
     * GETs a list of queues
     *
     * @return A list of the PrintQueue
     */
    @GET
    @Path("queues")
    @Produces(MediaType.APPLICATION_JSON)
    fun getQueues(): List<PrintQueue> = DB.getQueues()

    /**
     * @param queue The name of the queue to retrieve from
     * @param limit The number of PrintJob to return
     * @return A list of PrintJob as JSON
     */
    @GET
    @Path("queues/{queue}")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("queue") queue: String,
                 @QueryParam("limit") @DefaultValue("13") limit: Int,
                 @QueryParam("from") @DefaultValue("0") from: Long): Collection<PrintJob> =
            DB.getJobsByQueue(queue, maxAge = Date().time - from, sortOrder = Order.DESCENDING)

    /**
     * Gets the Up status for each Queue.
     * Returns a HashMap<String></String>, Boolean> mapping the Queue name to the up status.
     * The Up status is determined by whether at least one of the printers associated with the Queue is working.
     * It will automatically look up which Destinations are associated with the Queue
     *
     * @return The statuses of all the queues
     */
    @GET
    @Path("queues/status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getStatus(): Map<String, Boolean> {
        val destinations = DB.getDestinations().map { it._id to it }.toMap()

        val queues = DB.getQueues()

        val out = mutableMapOf<String, Boolean>()

        queues.forEach forQueue@ { q ->
            val name = q.name ?: return@forQueue
            q.destinations.forEach forDest@ {
                val isUp = destinations[it]?.up ?: return@forDest
                out[name] = isUp || out[name] ?: false
            }
        }

        return out
    }

    /**
     * GETs the data for the marquee
     *
     * @return a list of the marquee messages
     */
    @GET
    @Path("marquee")
    @Produces(MediaType.APPLICATION_JSON)
    fun getMarquee(): List<MarqueeData> = DB.getMarquees()

    /**
     * Note that this is an exact replica of [Destinations.getDestinations]
     * but with no authorization necessary
     *
     * Why we have this, I don't know
     */
    @GET
    @Path("destinations")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDestinations(@Context ctx: ContainerRequestContext): Map<String, Destination> {
        return DB.getDestinations()
                .mapNotNull {
                    val id = it._id ?: return@mapNotNull null
                    id to it.toDestination()
                }
                .toMap()
    }

    @GET
    @Path("/user/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getUserInfo(@PathParam("username") username: String): String {
        return AuthenticationManager.queryUser(username, null)?.nick
                ?: failNotFound("No nick associated with $username")
    }

    private companion object : WithLogging()
}
