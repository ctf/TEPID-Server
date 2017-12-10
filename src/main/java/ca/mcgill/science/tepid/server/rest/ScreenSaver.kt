package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.util.WithLogging
import ca.mcgill.science.tepid.server.util.couchdb
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
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

    /**
     * @param queue The name of the queue to retrieve from
     * @param limit The number of PrintJob to return
     * @return A list of PrintJob as JSON
     */
    @GET
    @Path("queues/{queue}")
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("queue") queue: String, @QueryParam("limit") @DefaultValue("13") limit: Int, @QueryParam("from") @DefaultValue("0") from: Long): Collection<PrintJob> {
        val data = couchdb
                .path("_design/main/_view").path("jobsByQueueAndTime")
                .queryParam("startkey", "[\"$queue\",%7B%7D]")
                .queryParam("endkey", "[\"$queue\",$from]")
                .queryParam("descending", true)
                .queryParam("limit", limit)
                .request(MediaType.APPLICATION_JSON)
                .get(JobResultSet::class.java)
                .getValues()
        val out = TreeSet(Comparator<PrintJob> { j1, j2 ->
            var p1: Date? = j1.processed
            var p2: Date? = j2.processed
            if (j1.failed != null) p1 = j1.started
            if (j2.failed != null) p2 = j2.started
            if (p1 == null && p2 == null) return@Comparator j1.started.compareTo(j2.started)
            if (p1 == null) return@Comparator -1
            if (p2 == null) return@Comparator 1
            if (p2.compareTo(p1) == 0) j2._id.compareTo(j1._id) else p2.compareTo(p1)
        })
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
        val destinations = couchdb    //gets a list of the destinations
                .path("_design/main/_view").path("destinations")
                .request(MediaType.APPLICATION_JSON)
                .get(DestinationResultSet::class.java).getValues()
                .map { it._id to it }.toMap()

        val queues = couchdb        //gets a list of the queues
                .path("_design/main/_view").path("queues")
                .request(MediaType.APPLICATION_JSON)
                .get(QueueResultSet::class.java)
                .rows.map { it.value }

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
            couchdb.path("_design/marquee/_view").path("all")
                    .request(MediaType.APPLICATION_JSON)
                    .get(MarqueeDataResultSet::class.java)
                    .getValues()


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


    private class JobResultSet : ViewResultSet<List<String>, PrintJob>()

    private class DestinationResultSet : ViewResultSet<String, Destination>()

    private class MarqueeDataResultSet : ViewResultSet<String, MarqueeData>()

    companion object : WithLogging()
}
