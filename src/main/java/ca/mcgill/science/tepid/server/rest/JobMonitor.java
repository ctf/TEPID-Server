package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.PrintJob;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.Date;
import java.util.List;

public class JobMonitor implements Runnable {

    private static final WebTarget couchdb = WebTargetsKt.getCouchdb();

    private static class JobResultSet extends ViewResultSet<String, PrintJob> {
    }

    @Override
    public void run() {
        try {
            List<Row<String, PrintJob>> rows = couchdb.path("_design/main/_view").path("oldJobs")
                    .queryParam("endkey", System.currentTimeMillis() - 1_800_000).request(MediaType.APPLICATION_JSON).get(JobResultSet.class).rows;
            for (Row<String, PrintJob> r : rows) {
                PrintJob j = r.value;
                j.setFailed(new Date(), "Timed out");
                couchdb.path(j.getId()).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
                if (Jobs.Companion.getProcessingThreads().containsKey(j.getId())) {
                    Thread t = Jobs.Companion.getProcessingThreads().get(j.getId());
                    try {
                        t.interrupt();
                    } catch (Exception ignored) {
                    }
                    Jobs.Companion.getProcessingThreads().remove(j.getId());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
