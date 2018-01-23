package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.Utils
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.utils.WithLogging
import org.mindrot.jbcrypt.BCrypt
import retrofit2.http.Url
import java.net.URL
import java.net.URLEncoder
import java.util.*

object SessionManager : WithLogging() {

    private const val HOUR_IN_MILLIS = 60 * 60 * 1000

    fun start(user: FullUser, expiration: Int): Session {
        val session = Session(user = user, expiration = System.currentTimeMillis() + expiration * HOUR_IN_MILLIS)
        val id = Utils.newSessionId()
        session._id = id
        CouchDb.path(session._id).putJson(session)
        return session
    }

    operator fun get(id: String): Session? {
        val session = try {
            CouchDb.path(id).getJson<Session>()
        } catch (e: Exception) {
            log.error("Id retrieval failed", e)
            //todo check why we have a try catch here, and not elsewhere
            return null
        }
        return if (session.expiration > System.currentTimeMillis()) session else null
    }

    /**
     * Check if session exists and isn't expired
     *
     * @param s sessionId
     * @return true for valid, false otherwise
     */
    fun valid(s: String): Boolean = this[s] != null

    fun end(s: String) {
//       val over = CouchDb.path(s).getJson<Session>()
//        CouchDb.path(over._id).queryParam("rev", over._rev).request().delete(String::class.java)
//        log.debug("Ending session for {}.", over.user.longUser)
        CouchDb.path(s).deleteRev()

        //todo validate. This removes logging but is more efficient
    }

    /**
     * Authenticate user is necessary
     *
     * @param sam short user
     * @param pw  password
     * @return authenticated user
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        val dbUser = getSam(sam)
        log.trace("Db data for $sam")
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
                UserResultSet results = couchdbOld.path("_design").path("main").path("_view").path("byLongUser").queryParam("key", "\""+sam.replace("@", "%40")+"\"").request(MediaType.APPLICATION_JSON).get(UserResultSet.class);
                if (!results.rows.isEmpty()) dbUser = results.rows.get(0).value;
            } else {
                dbUser = couchdbOld.path("u" + sam).request(MediaType.APPLICATION_JSON).get(User.class);
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
     * @see [queryUserCache]
     */
    fun queryUser(sam: String?, pw: String?): FullUser? {
        return if (Config.LDAP_ENABLED) Ldap.queryUser(sam, pw) else queryUserCache(sam)
    }

    fun getSam(sam: String?): FullUser? {
        sam ?: return null

        try {
            if (sam.contains("@")) {
                log.trace("getSam by long user $sam")
                val results = CouchDb.getViewRows<FullUser>("byLongUser") {
                    query("key" to "\"$sam\"", "limit" to 1)
                }
                if (!results.isEmpty()) return results[0]
            } else {
                log.trace("getSam by short user $sam")
                return CouchDb.path("u$sam").getJson()
            }
        } catch (e: Exception) {
            log.error("Query error for $sam", e)
        }

        return null
    }

    /**
     * Get user if exists and sets salutation
     *
     * @param sam shortId
     * @return user if exists
     */
    fun queryUserCache(sam: String?): FullUser? {
        val dbUser = getSam(sam) ?: return null
        dbUser.salutation = if (dbUser.nick == null)
            if (!dbUser.preferredName.isEmpty())
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
    fun autoSuggest(like: String, limit: Int): Promise<List<FullUser>> {
        if (!Config.LDAP_ENABLED) {
            val emptyPromise = Q.defer<List<FullUser>>()
            emptyPromise.resolve(emptyList())
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
     * TODO return enum instead
     *
     * @param u user to check
     * @return String for role
     */
    fun getRole(u: FullUser?): String {
        if (u == null) return ""
        if (u.authType == null || u.authType != "local") {
            val g = u.groups.toSet()
            if (elderGroups.any(g::contains)) return "elder"
            if (ctferGroups.any(g::contains)) return "ctfer"
            if (userGroups.any(g::contains)) return "user"
            return ""
        } else {
            return if (u.role == "admin") "elder" else "user"
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
