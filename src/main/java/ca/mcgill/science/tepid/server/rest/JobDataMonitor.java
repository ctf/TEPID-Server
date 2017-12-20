package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.models.data.ViewResultMap;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.List;

public class JobDataMonitor implements Runnable {

    private final WebTarget couchdb = WebTargetsKt.getCouchdbOld();
    private static final Logger logger = LoggerFactory.getLogger(JobDataMonitor.class);

    private static class JobResultSet extends ViewResultMap<String, PrintJob> {
    }

    @Override
    public void run() {
        logger.info("Deleting expired job data.");
        try {
            List<PrintJob> jobs = couchdb.path("_design/main/_view").path("storedJobs")
                    .request(MediaType.APPLICATION_JSON).get(JobResultSet.class).getValues();
            jobs.forEach(j -> {
                if (j.getDeleteDataOn() < System.currentTimeMillis()) {
                    if (j.getFile() != null) {
                        try {
                            File f = new File(j.getFile());
                            if (f.exists())
                                if (!f.delete())
                                    System.out.println("Failed to delete file");
                        } catch (Exception ignored) {
                        }
                        j.setFile(null);
                    }
                    if (j.getAdditionalProperties().containsKey("_attachments")) {
                        j.getAdditionalProperties().remove("_attachments");
                    }
                    // todo verify; originally request(MediaType.TEXT_PLAIN)
                    couchdb.path(j.getId()).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
