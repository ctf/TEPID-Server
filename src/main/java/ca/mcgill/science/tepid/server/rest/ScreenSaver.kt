package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.db.query
import ca.mcgill.science.tepid.utils.WithLogging
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
    fun getQueues(): List<PrintQueue> = CouchDb.getViewRows("queues")

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
            CouchDb.getViewRows<PrintJob>("jobsByQueueAndTime") {
                query(
                        "descending" to true,
                        "startkey" to "[\"$queue\",%7B%7D]",
                        "endkey" to "[\"$queue\",$from]",
                        "limit" to limit
                )
            }.sortedDescending()

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
        val destinations = CouchDb.getViewRows<FullDestination>("destinations")
                .map { it._id to it }.toMap()

        val queues = CouchDb.getViewRows<PrintQueue>("queues")

        val out = mutableMapOf<String, Boolean>()

        queues.forEach forQueue@{ q ->
            val name = q.name ?: return@forQueue
            q.destinations.forEach forDest@{
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
    fun getMarquee(): List<MarqueeData> =
            CouchDb.getViewRows("_design/marquee/_view", "all")

    /**
     * Note that this is an exact replica of [Destinations.getDestinations]
     * but with no authorization necessary
     *
     * Why we have this, I don't know
     */
    @GET
    @Path("destinations")
    fun getDestinations(@Context ctx: ContainerRequestContext): Map<String, Destination> {
        return CouchDb.getViewRows<FullDestination>("destinations")
                .map { it.toDestination() }
                .mapNotNull {
                    val id = it._id ?: return@mapNotNull null
                    id to it
                }
                .toMap()
    }

    @GET
    @Path("/user/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getUserInfo(@PathParam("username") username: String): NameUser {
        val user = SessionManager.queryUser(username, null)
                ?: failNotFound("Could not find user $username")
        return user.toNameUser()
    }

    private companion object : WithLogging()
}
