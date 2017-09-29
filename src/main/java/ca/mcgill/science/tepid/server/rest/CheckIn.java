package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.CheckedIn;
import ca.mcgill.science.tepid.common.EmailReasons;
import ca.mcgill.science.tepid.common.SignUp;
import ca.mcgill.science.tepid.server.rest.OfficeHours.SignUpResult;
import ca.mcgill.science.tepid.server.util.CouchClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/check-in")
public class CheckIn {
    private static final WebTarget couchdb = CouchClient.getTepidWebTarget();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ctfer", "elder"})
    public CheckedIn getCheckedInUsers() {
        WebTarget tgt = couchdb.path("_design/main/_view").path("checkin");
        List<CheckedInResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(CheckedInResult.class).rows;
        if (rows.isEmpty()) {
            CheckedIn checkedIn = new CheckedIn();
            checkedIn.setCurrentCheckIn(new HashMap<String, String[]>());
            checkedIn.setType("checkin");
            couchdb.request().post(Entity.entity(checkedIn, MediaType.APPLICATION_JSON));
            rows = tgt.request(MediaType.APPLICATION_JSON).get(CheckedInResult.class).rows;
        }
        return rows.get(0).value;
    }

    @JsonInclude(Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckedInResult {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Row {
            @JsonProperty("value")
            CheckedIn value;
        }

        @JsonProperty("rows")
        List<Row> rows;
    }

    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ctfer", "elder"})
    public SignUp getUserOH(@PathParam("name") String name) {
        WebTarget tgt = couchdb.path("_design/main/_view").path("signupByName").queryParam("key", "\"" + name + "\"");
        List<SignUpResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
        if (rows.isEmpty()) return null;
        else return rows.get(0).value;
    }

    @POST
    @Path("/{shortUser}/{date}")
    @RolesAllowed({"ctfer", "elder"})
    public void sendEmail(@PathParam("shortUser") String shortUser, @PathParam("date") String date, String emailBody) {
        sendEmail(shortUser + " " + date, emailBody);

        EmailReasons emailReasons = new EmailReasons();
        emailReasons.setBody(emailBody);
        emailReasons.setHeading(shortUser + " " + date);
        emailReasons.setType("emailReasons");
        couchdb.request().post(Entity.entity(emailReasons, MediaType.APPLICATION_JSON));
    }

    /**
     * Sends email
     *
     * @param heading subject
     * @param body    body
     */
    public static void sendEmail(String heading, String body) {
        System.out.println(body);
        Email email = new SimpleEmail();
        email.setHostName("smtp.googlemail.com");
        email.setSmtpPort(465);
        email.setAuthenticator(new DefaultAuthenticator("ctfcomputertaskforce", "zaq!xsw@"));
        email.setSSLOnConnect(true);
        try {
            email.setFrom("ctfcomputertaskforce@gmail.com");
            email.setSubject(heading);
            email.setMsg(body);
            email.addTo("ctfcomputertaskforce@gmail.com");
            email.send();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/_changes")
    public void getChanges(@Context UriInfo uriInfo, @Suspended AsyncResponse ar) {
        WebTarget target = couchdb.path("_changes").queryParam("filter", "main/checkIn");
        MultivaluedMap<String, String> qp = uriInfo.getQueryParameters();
        if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"));
        if (qp.containsKey("since")) target = target.queryParam("since", qp.getFirst("since"));
        String changes = target.request().get(String.class);
        if (!ar.isDone() && !ar.isCancelled()) {
            ar.resume(changes);
        }
    }

    @POST
    @RolesAllowed({"ctfer", "elder"})
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setSignUp(Map<String, String[]> checkedInUsers) {
        WebTarget tgt = couchdb.path("_design/main/_view").path("checkin");
        List<CheckedInResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(CheckedInResult.class).rows;
        CheckedIn checkedIn = rows.get(0).value;
        checkedIn.setCurrentCheckIn(checkedInUsers);
        String id = checkedIn.getId();
        couchdb.path(id).request().put(Entity.entity(checkedIn, MediaType.APPLICATION_JSON));
        return Response.ok("new list of checked in users: " + checkedInUsers).build();
    }
}
