package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.util.CouchDb
import ca.mcgill.science.tepid.server.util.query
import ca.mcgill.science.tepid.utils.WithLogging
import java.util.*
import javax.ws.rs.*
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
    fun listJobs(@PathParam("queue") queue: String, @QueryParam("limit") @DefaultValue("13") limit: Int, @QueryParam("from") @DefaultValue("0") from: Long): Collection<PrintJob> {
        val data = CouchDb.getViewRows<PrintJob>("jobsByQueueAndTime") {
            query(
                    "descending" to true,
                    "startkey" to "[\"$queue\",%7B%7D]",
                    "endkey" to "[\"$queue\",$from]",
                    "limit" to limit
            )
        }

        val out = TreeSet<PrintJob>()
        return if (limit < 0 || limit >= out.size) {
            data
        } else {
            ArrayList(out).subList(0, limit)
        }
    }

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

        queues.forEach forQueue@ { q ->
            val name = q.name ?: return@forQueue
            q.destinations.forEach forDest@ {
                val isUp = destinations[it]?.up ?: return@forDest
                out.put(name, isUp || out[name] ?: false)
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


    //TODO broke the screensaver?
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("office-hours/on-duty/{timeSlot}")
    fun onDuty(@PathParam("timeSlot") timeSlot: String): List<*> {
        //return new OfficeHours().onDuty(timeSlot);
        return emptyList<Any>()
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("office-hours/checked-in")
    fun checkedIn(): List<String> {
        return emptyList()
    }

    private companion object : WithLogging()
}
