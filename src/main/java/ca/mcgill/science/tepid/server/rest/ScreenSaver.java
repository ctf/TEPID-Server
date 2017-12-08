package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.*;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.ws.rs.*;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.*;

@Path("/screensaver")
public class ScreenSaver {
    private static final WebTarget couchdb = WebTargetsKt.getCouchdb();

    /**
     * GETs a list of queues
     *
     * @return A list of the PrintQueue
     */
    @GET
    @Path("queues")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PrintQueue> getQueues() {
        List<QueueResultSet.Row> rows = couchdb
                .path("_design/main/_view").path("queues")
                .request(MediaType.APPLICATION_JSON)
                .get(QueueResultSet.class)
                .rows;
        List<PrintQueue> out = new ArrayList<>();
        for (QueueResultSet.Row r : rows) {
            out.add(r.value);
        }
        return out;
    }

    @JsonInclude(Include.NON_NULL)            //TODO: comment this
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueueResultSet {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Row {
            @JsonProperty("value")
            PrintQueue value;
        }

        @JsonProperty("rows")
        List<Row> rows;
    }

    /**
     * @param queue The name of the queue to retrieve from
     * @param limit The number of PrintJob to return
     * @return A list of PrintJob as JSON
     */
    @GET
    @Path("queues/{queue}")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<PrintJob> listJobs(@PathParam("queue") String queue, @QueryParam("limit") @DefaultValue("13") int limit, @QueryParam("from") @DefaultValue("0") long from) {
        WebTarget tgt = couchdb
                .path("_design/main/_view").path("jobsByQueueAndTime")
                .queryParam("startkey", "[\"" + queue + "\",%7B%7D]")
                .queryParam("endkey", "[\"" + queue + "\"," + from + "]")
                .queryParam("descending", true)
                .queryParam("limit", limit);
//		System.out.println(tgt.getUri());
        List<Row<List<String>, PrintJob>> rows = tgt
                .request(MediaType.APPLICATION_JSON)
                .get(JobResultSet.class)
                .rows;
        Collection<PrintJob> out = new TreeSet<>(new Comparator<PrintJob>() {
            @Override
            public int compare(PrintJob j1, PrintJob j2) {
                Date p1 = j1.getProcessed(), p2 = j2.getProcessed();
                if (j1.getFailed() != null) p1 = j1.started;
                if (j2.getFailed() != null) p2 = j2.started;
                if (p1 == null && p2 == null) return j1.started.compareTo(j2.started);
                if (p1 == null) return -1;
                if (p2 == null) return 1;
                return p2.compareTo(p1) == 0 ? j2.getId().compareTo(j1.getId()) : p2.compareTo(p1);
            }
        });
        for (Row<List<String>, PrintJob> r : rows) {
            out.add(r.value);
        }
        if (limit < 0 || limit >= out.size()) {
            return out;
        } else {
            return new ArrayList<>(out).subList(0, limit);
        }
    }

    /**
     * Gets the Up status for each Queue.
     * Returns a HashMap<String, Boolean> mapping the Queue name to the up status.
     * The Up status is determined by whether at least one of the printers associated with the Queue is working.
     * It will automatically look up which Destinations are associated with the Queue
     *
     * @return The statuses of all the queues
     */
    @GET
    @Path("queues/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Boolean> getStatus() {
        List<Row<String, Destination>> rowDestinations = couchdb    //gets a list of the destinations
                .path("_design/main/_view").path("destinations")
                .request(MediaType.APPLICATION_JSON)
                .get(DestinationResultSet.class)
                .rows;

        List<QueueResultSet.Row> rowQueues = couchdb        //gets a list of the queues
                .path("_design/main/_view").path("queues")
                .request(MediaType.APPLICATION_JSON)
                .get(QueueResultSet.class)
                .rows;

        Map<String, Destination> destinations = new HashMap<>();
        for (Row<String, Destination> r : rowDestinations)                //creates a HashMap of the destinations by ID
        {
            destinations.put(r.value.getId(), r.value);
        }

        Map<String, Boolean> out = new HashMap<>();                //declares out
        for (QueueResultSet.Row q : rowQueues)                    //iterates over every queue
        {
            for (String d : q.value.destinations)                //then through every destination therein
            {
                if (out.containsKey(q.value.name))                //then checks whether there is already an entry in the output for the queue
                {
                    out.put(q.value.name, destinations.get(d).isUp() || out.get(q.value.name));    //if there is, it takes the OR of it and the existing value
                } else {
                    out.put(q.value.name, destinations.get(d).isUp());        //if not, it adds an entry for it
                }
            }
        }
        return out;
    }

    /**
     * GETs the data for the marquee
     *
     * @return a list of the marquee messages
     */
    @GET
    @Path("marquee")
    @Produces(MediaType.APPLICATION_JSON)
    public List<MarqueeData> getMarquee() {
        WebTarget tgt = couchdb
                .path("_design/marquee/_view").path("all");
        List<Row<String, MarqueeData>> rows = tgt
                .request(MediaType.APPLICATION_JSON)
                .get(MarqueeDataResultSet.class)
                .rows;
        List<MarqueeData> out = new ArrayList<>();
        for (Row<String, MarqueeData> r : rows) {
            out.add(r.value);
        }
        return (out);

    }


    //TODO broke the screensaver?
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("office-hours/on-duty/{timeSlot}")
    public List onDuty(@PathParam("timeSlot") String timeSlot) {
        //return new OfficeHours().onDuty(timeSlot);
        return new ArrayList();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("office-hours/checked-in")
    public List<String> checkedIn() {
        return new ArrayList<String>();
    }


    private static class JobResultSet extends ViewResultSet<List<String>, PrintJob> {
    }

    private static class DestinationResultSet extends ViewResultSet<String, Destination> {
    }

    private static class MarqueeDataResultSet extends ViewResultSet<String, MarqueeData> {
    }
}
