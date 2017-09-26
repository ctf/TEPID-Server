package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.server.rest.CheckIn.CheckedInResult;
import ca.mcgill.science.tepid.server.util.CouchClient;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

public class MoveLateCheckOuts implements Runnable {

	private static final WebTarget couchdb = CouchClient.getTepidWebTarget();

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
