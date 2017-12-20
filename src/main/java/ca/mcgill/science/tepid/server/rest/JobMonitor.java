package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.models.data.ViewResultMap;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.Date;
import java.util.List;

public class JobMonitor implements Runnable {

    private static final WebTarget couchdb = WebTargetsKt.getCouchdbOld();

    private static class JobResultSet extends ViewResultMap<String, PrintJob> {
    }

    @Override
    public void run() {
        try {
            List<PrintJob> jobs = couchdb.path("_design/main/_view").path("oldJobs")
                    .queryParam("endkey", System.currentTimeMillis() - 1_800_000).request(MediaType.APPLICATION_JSON).get(JobResultSet.class).getValues();
            jobs.forEach(j -> {
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
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
