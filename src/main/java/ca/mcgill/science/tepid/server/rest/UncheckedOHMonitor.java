package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.server.rest.CheckIn.CheckedInResult;
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
	
	private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
	private final WebTarget couchdb = client.target("http://admin:" + Config.getSetting(ConfigKeys.COUCHDB_PASSWORD) + "@localhost:5984/tepid");
	
	public void run() {
		WebTarget tgt = couchdb.path("_design/main/_view").path("checkin");
		List<CheckedInResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(CheckedInResult.class).rows;
		CheckedIn checkedIn = rows.get(0).value;
		Set<Entry<String, String[]>> checkInList = checkedIn.getCurrentCheckIn().entrySet();
		String body = " ";
		for (Entry<String, String[]> member : checkInList) {
			body += member.getKey() + " " + member.getValue()[1] + " ~ " + member.getValue()[2] + "\r\n";
		}
		Calendar cal = Calendar.getInstance();
		CheckIn.sendEmail("Unchecked Out Members for " + cal.get(Calendar.MONTH) + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + cal.get(Calendar.YEAR),
				body);
	}
}
