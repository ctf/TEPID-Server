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

object SessionManager : WithLogging() {

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
        return if (s != null && s.expiration.time > System.currentTimeMillis()) s else null
    }

    /**
     * Check if session exists and isn't expired
     *
     * @param s sessionId
     * @return true for valid, false otherwise
     */
    fun valid(s: String): Boolean = this[s] != null

    fun end(s: String) {
        val over = couchdb.path(s).request(MediaType.APPLICATION_JSON).get(Session::class.java)
        couchdb.path(over.id).queryParam("rev", over.rev).request().delete(String::class.java)
        log.debug("Ending session for {}.", over.user.longUser)
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
        return if (dbUser?.authType != null && dbUser.authType == "local") {
            if (BCrypt.checkpw(pw, dbUser.password)) dbUser else null
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
    fun queryUser(sam: String, pw: String?): User? {
        return if (Config.LDAP_ENABLED) Ldap.queryUser(sam, pw) else queryUserCache(sam)
    }

    private fun getSam(sam: String): User? {
        try {
            if (sam.contains("@")) {
                val results = couchdb.path("_design").path("main").path("_view").path("byLongUser").queryParam("key", "\"" + sam.replace("@", "%40") + "\"").request(MediaType.APPLICATION_JSON).get(UserResultSet::class.java)
                if (!results.rows.isEmpty()) return results.rows[0].value
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

    private val elderGroups = arrayOf("***REMOVED***")
    private val userGroups: Array<String>
        get() {
            val cal = Calendar.getInstance()
            return arrayOf("***REMOVED***", "***REMOVED***" + cal.get(Calendar.YEAR) + if (cal.get(Calendar.MONTH) < 8) "W" else "F")
        }
    private val ctferGroups = arrayOf("***REMOVED***", "***REMOVED***")

    /**
     * Retrieves user role
     *
     * @param u user to check
     * @return String for role
     */
    fun getRole(u: User?): String? {
        if (u == null) return null
        if (u.authType == null || u.authType != "local") {
            val g = u.groups?.toSet() ?: return null
            if (elderGroups.any(g::contains)) return "elder"
            if (ctferGroups.any(g::contains)) return "ctfer"
            if (userGroups.any(g::contains)) return "user"
            return null
        } else {
            return if (u.role != null && u.role == "admin") "elder" else "user"
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

}
