package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.common.SignUp;
import ca.mcgill.science.tepid.common.User;
import ca.mcgill.science.tepid.server.rest.OfficeHours.SignUpResult;
import ca.mcgill.science.tepid.server.util.SessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.glassfish.jersey.jackson.JacksonFeature;
import shared.Config;
import shared.ConfigKeys;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BarcodeCheckInListener implements ServletContextListener {

    private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
    private final WebTarget couchdbBarcodes = client.target("http://admin:" + Config.getSetting(ConfigKeys.DB_PASSWORD) + "@tepid.science.mcgill.ca:5984/barcodes");
    private final WebTarget couchdb = client.target("http://admin:" + Config.getSetting(ConfigKeys.DB_PASSWORD) + "@localhost:5984/tepid");
    private static final boolean START = true;

    private Thread myThread = null;

    private class BarcodeListener implements Runnable {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                JsonNode change = couchdbBarcodes.path("_changes").queryParam("feed", "longpoll")
                        .queryParam("since", "now").queryParam("include_docs", "true")
                        .request(MediaType.APPLICATION_JSON).get(ObjectNode.class);
                //New barcode found; parse
                if (change.get("results").has(0)) {
                    String studentID = change.get("results").get(0).get("doc").get("code").toString().substring(2);
                    SessionManager sm = SessionManager.getInstance();
                    User user = sm.queryUser(studentID, null);
                    String givenName = user.givenName;
                    String shortUser = user.shortUser;
                    ArrayList<String> hours = new ArrayList<String>();
                    CheckedIn checkedIn = CheckInUtils.getCheckedIn();

                    if (checkIfUserHasOH(shortUser, hours) && !CheckInUtils.userCheckedIn(shortUser, checkedIn)) {
                        checkedIn.getCurrentCheckIn().put(shortUser, new String[]{givenName, hours.get(0), hours.get(1)});
                        couchdb.path(checkedIn.getId()).request().put(Entity.entity(checkedIn, MediaType.APPLICATION_JSON));
                    } else if (CheckInUtils.userCheckedIn(shortUser, checkedIn) && CheckInUtils.userShouldCheckOut(shortUser, checkedIn)) {
                        checkedIn.getCurrentCheckIn().remove(shortUser);
                        couchdb.path(checkedIn.getId()).request().put(Entity.entity(checkedIn, MediaType.APPLICATION_JSON));
                    }
                }
            }
        }
    }

    /**
     * Checks if user has at least one office hour slot
     *
     * @param shortUser of mcgill student
     * @param hours     list of hours to check
     * @return true if user currently has OH
     */
    public boolean checkIfUserHasOH(String shortUser, ArrayList<String> hours) {
        WebTarget tgt = couchdb.path("_design/main/_view").path("signupByName").queryParam("key",
                "\"" + shortUser + "\"");
        List<SignUpResult.Row> SignUpRow = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
        if (SignUpRow.isEmpty())
            return false;
        SignUp SignUp = SignUpRow.get(0).value;
        String dayOfWeek = new SimpleDateFormat("EEEE").format(new Date());
        //Check if user has OH today, and if so, check if slots have all passed
        return SignUp.getSlots().containsKey(dayOfWeek) && !CheckInUtils.timeHasPassed(SignUp.getSlots().get(dayOfWeek), START, hours);
    }

    /**
     * Attach listener to new thread
     *
     * @param sce ServletContextEvent
     */
    public void contextInitialized(ServletContextEvent sce) {
        if ((myThread == null) || (!myThread.isAlive())) {
            myThread = new Thread(new BarcodeListener());
            myThread.start();
        }
    }

    /**
     * Interrupt thread if exists
     *
     * @param sce ServletContextEvent
     */
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            myThread.interrupt();
        } catch (Exception ex) {
        }
    }
}
