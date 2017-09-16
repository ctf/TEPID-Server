package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.server.rest.CheckIn.CheckedInResult;
import org.glassfish.jersey.jackson.JacksonFeature;
import shared.Config;
import shared.ConfigKeys;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

public class MoveLateCheckOuts implements Runnable {
	private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
	private final WebTarget couchdb = client.target("http://admin:" + Config.getSetting(ConfigKeys.COUCHDB_PASSWORD) + "@localhost:5984/tepid");
	
	public void run() {
		WebTarget tgtStudent = couchdb.path("_design/main/_view").path("checkin");
		List<CheckedInResult.Row> rows = tgtStudent.request(MediaType.APPLICATION_JSON).get(CheckedInResult.class).rows;
		CheckedIn checkedIn = rows.get(0).value;
		for (Map.Entry<String, String[]> entry : checkedIn.getCurrentCheckIn().entrySet()) {
			if (CheckInUtils.userCheckedIn(entry.getKey(), checkedIn) && CheckInUtils.userShouldCheckOut(entry.getKey(), checkedIn)) {
				checkedIn.getCurrentCheckIn().remove(entry.getKey());
				checkedIn.getLateCheckOuts().put(entry.getKey(), entry.getValue());
			}
		}
		couchdb.path(checkedIn.getId()).request().put(Entity.entity(checkedIn, MediaType.APPLICATION_JSON));
	}
}
