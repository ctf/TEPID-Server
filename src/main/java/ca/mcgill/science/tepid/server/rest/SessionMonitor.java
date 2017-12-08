package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.List;

public class SessionMonitor implements Runnable {

    private static final WebTarget couchdb = WebTargetsKt.getCouchdb();
    private static final Logger logger = LoggerFactory.getLogger(SessionMonitor.class);

    private static class SessionResultSet extends ViewResultSet<String, Session> {
    }

    @Override
    public void run() {
        logger.info("Removing expired sessions from database.");
        try {
            List<Row<String, Session>> rows = couchdb.path("_design/main/_view").path("sessions").request(MediaType.APPLICATION_JSON).get(SessionResultSet.class).rows;
            JsonNodeFactory nf = JsonNodeFactory.instance;
            ObjectNode root = nf.objectNode();
            ArrayNode docs = root.putArray("docs");
            for (Row<String, Session> r : rows) {
                Session s = r.value;
                if (s.getExpiration().getTime() < System.currentTimeMillis()) {
                    ObjectNode o = nf.objectNode().put("_id", s.getId()).put("_rev", s.getRev()).put("_deleted", true);
                    docs.add(o);
                }
            }
            if (docs.size() > 0)
                couchdb.path("_bulk_docs").request().post(Entity.entity(root, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
