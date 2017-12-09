package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.common.*
import ca.mcgill.science.tepid.common.ViewResultSet.Row
import ca.mcgill.science.tepid.server.util.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty

import javax.ws.rs.*
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import java.util.*

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
        val tgt = couchdb
                .path("_design/main/_view").path("jobsByQueueAndTime")
                .queryParam("startkey", "[\"$queue\",%7B%7D]")
                .queryParam("endkey", "[\"$queue\",$from]")
                .queryParam("descending", true)
                .queryParam("limit", limit)
        //		System.out.println(tgt.getUri());
        val rows = tgt
                .request(MediaType.APPLICATION_JSON)
                .get(JobResultSet::class.java)
                .rows
        val out = TreeSet(Comparator<PrintJob> { j1, j2 ->
            var p1: Date? = j1.processed
            var p2: Date? = j2.processed
            if (j1.failed != null) p1 = j1.started
            if (j2.failed != null) p2 = j2.started
            if (p1 == null && p2 == null) return@Comparator j1.started.compareTo(j2.started)
            if (p1 == null) return@Comparator -1
            if (p2 == null) return@Comparator 1
            if (p2.compareTo(p1) == 0) j2.id.compareTo(j1.id) else p2.compareTo(p1)
        })
        return if (limit < 0 || limit >= out.size) {
            rows.map { it.value }
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
        val rowDestinations = couchdb    //gets a list of the destinations
                .path("_design/main/_view").path("destinations")
                .request(MediaType.APPLICATION_JSON)
                .get(DestinationResultSet::class.java)
                .rows

        val rowQueues = couchdb        //gets a list of the queues
                .path("_design/main/_view").path("queues")
                .request(MediaType.APPLICATION_JSON)
                .get(QueueResultSet::class.java)
                .rows

        val destinations = rowDestinations.map { it.value.id to it.value }.toMap()

        val out = mutableMapOf<String, Boolean>()

        rowQueues.forEach { q ->
            q.value.destinations.forEach {
                out.put(q.value.name,
                        destinations[it]?.isUp ?: false || out[q.value.name] ?: false)
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
    fun getMarquee(): List<MarqueeData> {
        val tgt = couchdb
                .path("_design/marquee/_view").path("all")
        val rows = tgt
                .request(MediaType.APPLICATION_JSON)
                .get(MarqueeDataResultSet::class.java)
                .rows
        return rows.map { it.value }

    }


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
