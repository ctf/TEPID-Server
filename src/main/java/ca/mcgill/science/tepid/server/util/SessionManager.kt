package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.Utils
import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.utils.WithLogging
import org.mindrot.jbcrypt.BCrypt
import java.util.*

object SessionManager : WithLogging() {

    private const val HOUR_IN_MILLIS = 60 * 60 * 1000
    const val ADMIN = "admin"
    const val LOCAL = "local"

    fun start(user: FullUser, expiration: Int): Session {
        val session = Session(user = user, expiration = System.currentTimeMillis() + expiration * HOUR_IN_MILLIS)
        val id = Utils.newSessionId()
        session._id = id
        log.trace("Creating session $session")
        val out = CouchDb.path(id).putJson(session)
        println(out)
        return session
    }

    operator fun get(token: String): Session? {
        val session = CouchDb.path(token).getJsonOrNull<Session>() ?: return null
        return if (session.isValid()) session else null
    }

    /**
     * Check if session exists and isn't expired
     *
     * @param s sessionId
     * @return true for valid, false otherwise
     */
    fun valid(s: String): Boolean = this[s] != null

    fun end(s: String) {
        //todo test
        CouchDb.path(s).deleteRev()
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
        return if (dbUser?.authType == LOCAL) {
            if (BCrypt.checkpw(pw, dbUser.password)) dbUser else null
        } else {
            Ldap.authenticate(sam, pw)
        }
    }

    /**
     * Retrieve user from Ldap if available, otherwise retrieves from cache
     *
     * @param sam short user
     * @param pw  password
     * @return user if found
     * @see [queryUserCache]
     */
    fun queryUser(sam: String?, pw: String?): FullUser? =
            if (Config.LDAP_ENABLED) Ldap.queryUser(sam, pw) else queryUserCache(sam)

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
            log.error("Query error for $sam: ${e.message}")
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
        log.trace("Query user cache for $sam")
        val dbUser = getSam(sam)
        if (dbUser == null) {
            log.debug("User $sam does not exist in cache")
            return null
        }
        dbUser.salutation = if (dbUser.nick == null)
            if (!dbUser.preferredName.isEmpty())
                dbUser.preferredName[dbUser.preferredName.size - 1]
            else
                dbUser.givenName
        else
            dbUser.nick
        log.trace("Found db user (${dbUser._id}) ${dbUser.displayName} for $sam")
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
        if (u.authType == null || u.authType != LOCAL) {
            val g = u.groups.toSet()
            if (elderGroups.any(g::contains)) return ELDER
            if (ctferGroups.any(g::contains)) return CTFER
            if (userGroups.any(g::contains)) return USER
            return ""
        } else {
            return if (u.role == ADMIN) ELDER else USER
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