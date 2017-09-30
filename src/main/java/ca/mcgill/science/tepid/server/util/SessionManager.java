package ca.mcgill.science.tepid.server.util;

import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.User;
import ca.mcgill.science.tepid.common.Utils;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.server.rest.Sessions;
import in.waffl.q.Promise;
import in.waffl.q.Q;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class SessionManager {

    private static SessionManager instance;
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    public static synchronized SessionManager getInstance() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    private static final WebTarget couchdb = CouchClient.getTepidWebTarget();

    private static class UserResultSet extends ViewResultSet<String, User> {
    }

    private SessionManager() {

    }

    public Session start(User user, int expiration) {
        Session s = new Session(Utils.newSessionId(), user, expiration);
        couchdb.path(s.getId()).request().put(Entity.entity(s, MediaType.APPLICATION_JSON));
        return s;
    }

    public
    @Nullable
    Session get(String id) {
        Session s = null;
        try {
            s = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(Session.class);
        } catch (Exception e) {
        }
        if (s != null && s.getExpiration().getTime() > System.currentTimeMillis()) {
            return s;
        } else {
            return null;
        }
    }

    /**
     * Check if session exists and isn't expired
     *
     * @param s sessionId
     * @return true for valid, false otherwise
     */
    public boolean valid(String s) {
        return this.get(s) != null;
    }

    public void end(String s) {
        Session over = couchdb.path(s).request(MediaType.APPLICATION_JSON).get(Session.class);
        couchdb.path(over.getId()).queryParam("rev", over.getRev()).request().delete(String.class);
        logger.debug("Ending session for {}.", over.getUser().longUser);
    }

    /**
     * Authenticate user is necessary
     *
     * @param sam short user
     * @param pw  password
     * @return authenticated user
     */
    public
    @Nullable
    User authenticate(String sam, String pw) {
        User dbUser = getSam(sam);
        if (dbUser != null && dbUser.authType != null && dbUser.authType.equals("local")) {
            if (BCrypt.checkpw(pw, dbUser.password)) {
                return dbUser;
            } else {
                return null;
            }
        } else {
            return Ldap.authenticate(sam, pw);
        }
    }

    /*
     * TODO compare method above against method below (from the old repo)
    public User authenticate(String sam, String pw) {
        User dbUser = null;
        try {
            if (sam.contains("@")) {
                UserResultSet results = couchdb.path("_design").path("main").path("_view").path("byLongUser").queryParam("key", "\""+sam.replace("@", "%40")+"\"").request(MediaType.APPLICATION_JSON).get(UserResultSet.class);
                if (!results.rows.isEmpty()) dbUser = results.rows.get(0).value;
            } else {
                dbUser = couchdb.path("u" + sam).request(MediaType.APPLICATION_JSON).get(User.class);
            }
        } catch (Exception e) {}
        if (dbUser != null && dbUser.authType != null && dbUser.authType.equals("local")) {
            if (BCrypt.checkpw(pw, dbUser.password)) {
                return dbUser;
            } else {
                return null;
            }
        } else {
            return Ldap.authenticate(sam, pw);
        }
    }
    */

    /**
     * Retrieve user from Ldap if available, otherwise retrieves from cache
     *
     * @param sam short user
     * @param pw  password
     * @return user if found
     * @see #queryUserCache(String)
     */
    public
    @Nullable
    User queryUser(String sam, String pw) {
        if (Ldap.LDAP_ENABLED) return Ldap.queryUser(sam, pw);
        return queryUserCache(sam);
    }

    private
    @Nullable
    User getSam(String sam) {
        try {
            if (sam.contains("@")) {
                UserResultSet results = couchdb.path("_design").path("main").path("_view").path("byLongUser").queryParam("key", "\"" + sam.replace("@", "%40") + "\"").request(MediaType.APPLICATION_JSON).get(UserResultSet.class);
                if (!results.rows.isEmpty()) return results.rows.get(0).value;
            } else {
                return couchdb.path("u" + sam).request(MediaType.APPLICATION_JSON).get(User.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Get user if exists and sets salutation
     *
     * @param sam shortId
     * @return user if exists
     */
    public
    @Nullable
    User queryUserCache(String sam) {
        User dbUser = getSam(sam);
        if (dbUser == null) return null;
        dbUser.salutation = dbUser.nick == null ?
                ((dbUser.preferredName != null && !dbUser.preferredName.isEmpty())
                        ? dbUser.preferredName.get(dbUser.preferredName.size() - 1) : dbUser.givenName) : dbUser.nick;
        return dbUser;
    }

    /**
     * Sends list of matching {@link User}s based on current query
     *
     * @param like  prefix
     * @param limit max list size
     * @return list of matching users
     */
    public Promise<List<User>> autoSuggest(String like, int limit) {
        if (!Ldap.LDAP_ENABLED) {
            Q<List<User>> emptyPromise = Q.defer();
            emptyPromise.resolve(Arrays.asList(new User[0]));
            return emptyPromise.promise;
        }
        return Ldap.autoSuggest(like, limit);
    }

    /**
     * Retrieves user role
     *
     * @param u user to check
     * @return String for role
     */
    public String getRole(User u) {
        if (u == null) return null;
        Calendar cal = Calendar.getInstance();
        String[] elderGroups = {"***REMOVED***"},
                userGroups = {"***REMOVED***", "***REMOVED***" + cal.get(Calendar.YEAR) + (cal.get(Calendar.MONTH) < 8 ? "W" : "F")},
                ctferGroups = {"***REMOVED***", "***REMOVED***"};
        if (u.authType == null || !u.authType.equals("local")) {
            if (u.groups == null) return null;
            for (String g : elderGroups) {
                if (u.groups.contains(g)) return "elder";
            }
            for (String g : ctferGroups) {
                if (u.groups.contains(g)) return "ctfer";
            }
            for (String g : userGroups) {
                if (u.groups.contains(g)) return "user";
            }
            return null;
        } else {
            if (u.role != null && u.role.equals("admin")) {
                return "elder";
            } else {
                return "user";
            }
        }
    }

    /**
     * Sets exchange student status
     *
     * @param sam      shortId
     * @param exchange boolean for exchange status
     */
    public void setExchangeStudent(String sam, boolean exchange) {
        if (Ldap.LDAP_ENABLED) Ldap.setExchangeStudent(sam, exchange);
    }

}
