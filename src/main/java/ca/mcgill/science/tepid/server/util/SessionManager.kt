package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.bindings.LOCAL
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.utils.WithLogging
import org.mindrot.jbcrypt.BCrypt
import java.math.BigInteger
import java.security.SecureRandom

object SessionManager : WithLogging() {

    private const val HOUR_IN_MILLIS = 60 * 60 * 1000

    private val random = SecureRandom()

    fun start(user: FullUser, expiration: Int): FullSession {
        val session = FullSession(user = user, expiration = System.currentTimeMillis() + expiration * HOUR_IN_MILLIS)
        val id = BigInteger(130, random).toString(32)
        session._id = id
        log.trace("Creating session $session")
        val out = CouchDb.path(id).putJson(session)
        println(out)
        return session
    }

    operator fun get(token: String): FullSession? {
        log.trace("Get session $token")
        val session = CouchDb.path(token).getJsonOrNull<FullSession>() ?: return null
        if (session.isValid()) return session
        CouchDb.path(token).deleteRev()
        return null
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
     * Authenticate user if necessary
     *
     * @param sam short user
     * @param pw  password
     * @return authenticated user
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        val dbUser = Ldap.queryUserDb(sam)
        log.trace("Db data for $sam")
        return if (dbUser?.authType == LOCAL) {
            if (BCrypt.checkpw(pw, dbUser.password)) dbUser else null
        } else {
            Ldap.authenticate(sam, pw)
        }
    }

    /**
     * Retrieve user from Ldap if available, otherwise retrieves from db
     *
     * @param sam short user
     * @param pw  password
     * @return user if found
     * @see [Ldap.queryUserDb]
     */
    fun queryUser(sam: String?, pw: String?): FullUser? =
            if (Config.LDAP_ENABLED) Ldap.queryUser(sam, pw) else Ldap.queryUserDb(sam)

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
