package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.PrintJob;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.util.CouchClientKt;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import ca.mcgill.science.tepid.server.util.CouchClientKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class JobDataMonitor implements Runnable {

    private final WebTarget couchdb = CouchClientKt.getCouchdb();
    private static final Logger logger = LoggerFactory.getLogger(JobDataMonitor.class);

    private static class JobResultSet extends ViewResultSet<String, PrintJob> {
    }

    @Override
    public void run() {
    	logger.info("Deleting expired job data.");
        try {
            List<Row<String, PrintJob>> rows = couchdb.path("_design/main/_view").path("storedJobs").request(MediaType.APPLICATION_JSON).get(JobResultSet.class).rows;
            for (Row<String, PrintJob> r : rows) {
                PrintJob j = r.value;
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
                    couchdb.path(j.getId()).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
