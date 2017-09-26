package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.server.rest.CheckIn.CheckedInResult;
import ca.mcgill.science.tepid.server.util.CouchClient;
import org.glassfish.jersey.jackson.JacksonFeature;
import shared.Config;
import shared.ConfigKeys;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.Calendar;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class UncheckedOHMonitor implements Runnable {

    private static final WebTarget couchdb = CouchClient.getTepidWebTarget();

    public void run() {
        WebTarget tgt = couchdb.path("_design/main/_view").path("checkin");
        List<CheckedInResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(CheckedInResult.class).rows;
        CheckedIn checkedIn = rows.get(0).value;
        Set<Entry<String, String[]>> checkInList = checkedIn.getCurrentCheckIn().entrySet();
        StringBuilder body = new StringBuilder(" ");
        for (Entry<String, String[]> member : checkInList) {
            body.append(member.getKey()).append(" ").append(member.getValue()[1]).append(" ~ ").append(member.getValue()[2]).append("\r\n");
        }
        Calendar cal = Calendar.getInstance();
        CheckIn.sendEmail("Unchecked Out Members for " + cal.get(Calendar.MONTH) + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + cal.get(Calendar.YEAR),
                body.toString());
    }
}
