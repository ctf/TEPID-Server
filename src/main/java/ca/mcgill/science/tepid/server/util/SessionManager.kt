package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.common.Session
import ca.mcgill.science.tepid.common.User
import ca.mcgill.science.tepid.common.Utils
import ca.mcgill.science.tepid.common.ViewResultSet
import org.mindrot.jbcrypt.BCrypt
import java.util.*
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType

class SessionManager private constructor() {

    private class UserResultSet : ViewResultSet<String, User>()

    fun start(user: User, expiration: Int): Session {
        val s = Session(Utils.newSessionId(), user, expiration.toLong())
        couchdb.path(s.id).request().put(Entity.entity(s, MediaType.APPLICATION_JSON))
        return s
    }

    operator fun get(id: String): Session? {
        var s: Session? = null
        try {
            s = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(Session::class.java)
        } catch (e: Exception) {
        }

        return if (s != null && s.expiration.time > System.currentTimeMillis()) {
            s
        } else {
            null
        }
    }

    /**
     * Check if session exists and isn't expired
     *
     * @param s sessionId
     * @return true for valid, false otherwise
     */
    fun valid(s: String): Boolean {
        return this[s] != null
    }

    fun end(s: String) {
        val over = couchdb.path(s).request(MediaType.APPLICATION_JSON).get(Session::class.java)
        couchdb.path(over.getId()).queryParam("rev", over.getRev()).request().delete(String::class.java)
        log.debug("Ending session for {}.", over.getUser().longUser)
    }

    /**
     * Authenticate user is necessary
     *
     * @param sam short user
     * @param pw  password
     * @return authenticated user
     */
    fun authenticate(sam: String, pw: String): User? {
        val dbUser = getSam(sam)
        return if (dbUser != null && dbUser.authType != null && dbUser.authType == "local") {
            if (BCrypt.checkpw(pw, dbUser.password)) {
                dbUser
            } else {
                null
            }
        } else {
            Ldap.authenticate(sam, pw)
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
     * @see .queryUserCache
     */
    fun queryUser(sam: String, pw: String): User? {
        return if (Config.LDAP_ENABLED) Ldap.queryUser(sam, pw) else queryUserCache(sam)
    }

    private fun getSam(sam: String): User? {
        try {
            if (sam.contains("@")) {
                val results = couchdb.path("_design").path("main").path("_view").path("byLongUser").queryParam("key", "\"" + sam.replace("@", "%40") + "\"").request(MediaType.APPLICATION_JSON).get(UserResultSet::class.java)
                if (!results.rows.isEmpty()) return results.rows.get(0).value
            } else {
                return couchdb.path("u" + sam).request(MediaType.APPLICATION_JSON).get(User::class.java)
            }
        } catch (ignored: Exception) {
        }

        return null
    }

    /**
     * Get user if exists and sets salutation
     *
     * @param sam shortId
     * @return user if exists
     */
    fun queryUserCache(sam: String): User? {
        val dbUser = getSam(sam) ?: return null
        dbUser.salutation = if (dbUser.nick == null)
            if (dbUser.preferredName != null && !dbUser.preferredName.isEmpty())
                dbUser.preferredName[dbUser.preferredName.size - 1]
            else
                dbUser.givenName
        else
            dbUser.nick
        return dbUser
    }

    /**
     * Sends list of matching [User]s based on current query
     *
     * @param like  prefix
     * @param limit max list size
     * @return list of matching users
     */
    fun autoSuggest(like: String, limit: Int): Promise<List<User>> {
        if (!Config.LDAP_ENABLED) {
            val emptyPromise = Q.defer<List<User>>()
            emptyPromise.resolve(Arrays.asList(*arrayOfNulls(0)))
            return emptyPromise.promise
        }
        return Ldap.autoSuggest(like, limit)
    }

    /**
     * Retrieves user role
     *
     * @param u user to check
     * @return String for role
     */
    fun getRole(u: User?): String? {
        if (u == null) return null
        val cal = Calendar.getInstance()
        val elderGroups = arrayOf("***REMOVED***")
        val userGroups = arrayOf("***REMOVED***", "***REMOVED***" + cal.get(Calendar.YEAR) + if (cal.get(Calendar.MONTH) < 8) "W" else "F")
        val ctferGroups = arrayOf("***REMOVED***", "***REMOVED***")
        if (u.authType == null || u.authType != "local") {
            if (u.groups == null) return null
            for (g in elderGroups) {
                if (u.groups.contains(g)) return "elder"
            }
            for (g in ctferGroups) {
                if (u.groups.contains(g)) return "ctfer"
            }
            for (g in userGroups) {
                if (u.groups.contains(g)) return "user"
            }
            return null
        } else {
            return if (u.role != null && u.role == "admin") {
                "elder"
            } else {
                "user"
            }
        }
    }

    /**
     * Sets exchange student status
     *
     * @param sam      shortId
     * @param exchange boolean for exchange status
     */
    fun setExchangeStudent(sam: String, exchange: Boolean) {
        if (Config.LDAP_ENABLED) Ldap.setExchangeStudent(sam, exchange)
    }

    companion object : WithLogging() {

        private var instanceImpl: SessionManager? = null

        @Synchronized
        fun getInstance(): SessionManager {
            if (instanceImpl == null) instanceImpl = SessionManager()
            return instanceImpl!!
        }

    }

}
