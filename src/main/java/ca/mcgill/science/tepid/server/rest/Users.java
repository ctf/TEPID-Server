package ca.mcgill.science.tepid.server.rest;


import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.User;
import ca.mcgill.science.tepid.server.util.SessionManager;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.waffl.q.Promise;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Path("/users")
public class Users {

    private static final WebTarget couchdb = WebTargetsKt.getCouchdb();
    private static final Logger logger = LoggerFactory.getLogger(Users.class);

    @GET
    @Path("/{sam}")
    @RolesAllowed({"user", "ctfer", "elder"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryLdap(@PathParam("sam") String shortUser, @QueryParam("pw") String pw, @Context ContainerRequestContext crc, @Context UriInfo uriInfo) {
        Session session = (Session) crc.getProperty("session");
        User user = SessionManager.getInstance().queryUser(shortUser, pw);
        //TODO security wise, should the second check not happen before the first?
        if (user == null) {
            logger.warn("Could not find user {}.", shortUser);
            throw new NotFoundException(Response.status(404).entity("Could not find user " + shortUser).type(MediaType.TEXT_PLAIN).build());
        }
        if (session.getRole().equals("user") && !session.getUser().shortUser.equals(user.shortUser)) {
            logger.warn("Unauthorized attempt to lookup {} by user {}.", shortUser, session.getUser().longUser);
            return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").type(MediaType.TEXT_PLAIN).build();
        }
        try {
            if (!user.shortUser.equals(shortUser) && !uriInfo.getQueryParameters().containsKey("noRedirect")) {
                return Response.seeOther(new URI("users/" + user.shortUser)).build();
            }
        } catch (URISyntaxException ignored) {
        }
        return Response.ok(user).build();
    }

    @PUT
    @Path("/{sam}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createLocalAdmin(@PathParam("sam") String shortUser, User newAdmin, @Context ContainerRequestContext req, @Context UriInfo uriInfo) {
        if (this.getAdminConfigured()) {
            logger.warn("Unauthorized attempt to add a local admin by {}.", ((Session) req.getProperty("session")).getUser().longUser);
            return Response.status(Response.Status.UNAUTHORIZED).entity("Local admin already exists").type(MediaType.TEXT_PLAIN).build();
        }
        String hashedPw = BCrypt.hashpw(newAdmin.password, BCrypt.gensalt());
        newAdmin.password = hashedPw;
        newAdmin.role = "admin";
        newAdmin.authType = "local";
        newAdmin.activeSince = new Date();
        newAdmin.displayName = newAdmin.givenName + " " + newAdmin.lastName;
        newAdmin.salutation = newAdmin.givenName;
        newAdmin.longUser = newAdmin.email;
        String res = couchdb.path("u" + shortUser).request(MediaType.APPLICATION_JSON).put(Entity.entity(newAdmin, MediaType.APPLICATION_JSON)).readEntity(String.class);
        logger.info("Added local admin {}.", newAdmin.shortUser);
        return Response.ok(res).build();
    }

    @PUT
    @Path("/{sam}/exchange")
    @RolesAllowed({"ctfer", "elder"})
    @Consumes(MediaType.APPLICATION_JSON)
    public void setExchange(@PathParam("sam") String sam, boolean exchange) {
        SessionManager.getInstance().setExchangeStudent(sam, exchange);
    }

    @PUT
    @Path("/{sam}/nick")
    @RolesAllowed({"user", "ctfer", "elder"})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setNick(@PathParam("sam") String sam, String nick, @Context ContainerRequestContext req) {
        Session session = (Session) req.getProperty("session");
        User user = SessionManager.getInstance().queryUser(sam, null);
        if (session.getRole().equals("user") && !session.getUser().shortUser.equals(user.shortUser)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot change this resource").type(MediaType.TEXT_PLAIN).build();
        }
        user.nick = nick.isEmpty() ? null : nick;
        String res = couchdb.path("u" + user.shortUser).request(MediaType.APPLICATION_JSON).put(Entity.entity(user, MediaType.APPLICATION_JSON)).readEntity(String.class);
        System.out.println("Nick for " + user.shortUser + " set to " + nick);
        return Response.ok(res).build();
    }

    @PUT
    @Path("/{sam}/jobExpiration")
    @RolesAllowed({"user", "ctfer", "elder"})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setJobExpiration(@PathParam("sam") String sam, long jobExpiration, @Context ContainerRequestContext req) {
        Session session = (Session) req.getProperty("session");
        User user = SessionManager.getInstance().queryUser(sam, null);
        if (session.getRole().equals("user") && !session.getUser().shortUser.equals(user.shortUser)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot change this resource").type(MediaType.TEXT_PLAIN).build();
        }
        user.jobExpiration = jobExpiration;
        String res = couchdb.path("u" + user.shortUser).request(MediaType.APPLICATION_JSON).put(Entity.entity(user, MediaType.APPLICATION_JSON)).readEntity(String.class);
        System.out.println("Job expiration for " + user.shortUser + " set to " + jobExpiration);
        return Response.ok(res).build();
    }

    @PUT
    @Path("/{sam}/color")
    @RolesAllowed({"user", "ctfer", "elder"})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setColor(@PathParam("sam") String sam, boolean color, @Context ContainerRequestContext req) {
        Session session = (Session) req.getProperty("session");
        User user = SessionManager.getInstance().queryUser(sam, null);
        if (session.getRole().equals("user") && !session.getUser().shortUser.equals(user.shortUser)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot change this resource").type(MediaType.TEXT_PLAIN).build();
        }
        user.colorPrinting = color;
        String res = couchdb.path("u" + user.shortUser).request(MediaType.APPLICATION_JSON).put(Entity.entity(user, MediaType.APPLICATION_JSON)).readEntity(String.class);
        return Response.ok(res).build();
    }

    @GET
    @Path("/{sam}/quota")
    @RolesAllowed({"user", "ctfer", "elder"})
    @Produces(MediaType.APPLICATION_JSON)
    public int getQuota(@PathParam("sam") String shortUser, @Context ContainerRequestContext req) {
        Session session = (Session) req.getProperty("session");
        if (session.getRole().equals("user") && !session.getUser().shortUser.equals(shortUser)) {
            return -1;
        }
        return getQuota(shortUser);
    }

    public int getQuota(String shortUser) {
        int totalPrinted = 0;
        Date earliestJob = null;
        try {
            JsonNode rows = couchdb.path("_design/main/_view").path("totalPrinted").queryParam("key", "\"" + shortUser + "\"").request(MediaType.APPLICATION_JSON).get(ObjectNode.class).get("rows");
            totalPrinted = rows.get(0).get("value").get("sum").asInt(0);
            long ej = rows.get(0).get("value").get("earliestJob").asLong(0);
            earliestJob = new Date(ej);
        } catch (Exception e) {
//			e.printStackTrace();
        }
        User user = SessionManager.getInstance().queryUser(shortUser, null);
        if (user == null || SessionManager.getInstance().getRole(user) == null) return 0;

        //todo verify
        if (earliestJob == null) return 1000; // init to 1000 for new users
        Calendar d1 = Calendar.getInstance(),
                d2 = Calendar.getInstance();
        d1.setTime(earliestJob);
        int m1 = d1.get(Calendar.MONTH) + 1,
                y1 = d1.get(Calendar.YEAR),
                m2 = d2.get(Calendar.MONTH) + 1,
                y2 = d2.get(Calendar.YEAR);
        return (y2 - y1) * 1000 + ((m2 > 8 && (y1 != y2 || m1 < 8)) ? 1500 : 1000) - totalPrinted;
    }

    @GET
    @Path("/autosuggest/{like}")
    @RolesAllowed({"ctfer", "elder"})
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> ldapAutoSuggest(@PathParam("like") String like, @QueryParam("limit") int limit) {
        Promise<List<User>> resultsPromise = SessionManager.getInstance().autoSuggest(like, limit);
        return resultsPromise.getResult(20_000);
    }

    @GET
    @Path("/configured")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean getAdminConfigured() {
        try {
            JsonNode rows = couchdb.path("_design/main/_view").path("localAdmins").request(MediaType.APPLICATION_JSON).get(ObjectNode.class).get("rows");
            return rows.size() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
