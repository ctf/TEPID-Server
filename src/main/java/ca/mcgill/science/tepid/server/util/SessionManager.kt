package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.bindings.LOCAL
import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import org.mindrot.jbcrypt.BCrypt
import java.math.BigInteger
import java.security.SecureRandom

/**
 * SessionManager is responsible for managing sessions and dealing with the underlying authentication.
 * It is analogous to PAM, in that everything which needs authentication or user querying goes through this.
 * For managing sessions, it can start, resume, and end sessions
 * For user querying, it first checks the DB cache. The cache is updated every time a query to the underlying authentication is made.
 * Since it also provides an interface with the underlying authentication, it also provides username autosuggestion and can set users as exchange.
 */

object SessionManager : WithLogging() {

    private const val HOUR_IN_MILLIS = 60 * 60 * 1000
    private val numRegex = Regex("[0-9]+")
    private val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")

    private val random = SecureRandom()

    fun start(user: FullUser, expiration: Int): FullSession {
        val session = FullSession(user = user, expiration = System.currentTimeMillis() + expiration * HOUR_IN_MILLIS)
        val id = BigInteger(130, random).toString(32)
        session._id = id
        log.trace("Creating session $id")
        val out = CouchDb.path(id).putJson(session)
        println(out)
        return session
    }

    operator fun get(token: String): FullSession? {
        val session = CouchDb.path(token).getJsonOrNull<FullSession>() ?: return null
        if (session.isValid()) return session
        log.trace("Session $token is invalid; now ${System.currentTimeMillis()} expiration ${session.expiration}; deleting")
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
        val dbUser = queryUserDb(sam)
        log.trace("Db data for $sam")
        if (dbUser?.authType == LOCAL) {
            return if (BCrypt.checkpw(pw, dbUser.password)) dbUser else null
        }
        else if (Config.LDAP_ENABLED) {
            var ldapUser =  Ldap.authenticate(sam, pw)
            if (ldapUser!=null){
                ldapUser = mergeUsers(ldapUser, dbUser)
                updateDbWithUser(ldapUser)
            }
            return ldapUser
        }
        else {
            return null
        }
    }

    /**
     * Retrieve user from DB if available, otherwise retrieves from LDAP
     *
     * @param sam short user
     * @param pw  password
     * @return user if found
     */
    fun queryUser(sam: String?, pw: String?): FullUser? {
        if (sam == null) return null
        log.trace("Querying user $sam")

        val dbUser = queryUserDb(sam)

        if (dbUser != null) return dbUser

        if (Config.LDAP_ENABLED) {
            if (!sam.matches(shortUserRegex)) return null // cannot query without short user
            var ldapUser = Ldap.queryUserLdap(sam, pw) ?: return null

            ldapUser = mergeUsers(ldapUser, dbUser)

            if (dbUser != ldapUser) {
                updateDbWithUser(ldapUser)
            } else {
                log.trace("Not updating dbUser; already matches ldap user")
            }

            log.trace("Found user from ldap $sam: ${ldapUser.longUser}")
            return ldapUser
        }
        //finally
        return null
    }

    /**
     * Update [ldapUser] with db data
     * [queryAsOwner] should be true if [ldapUser] was retrieved by the owner rather than a resource account
     */
    internal fun mergeUsers(ldapUser: FullUser, dbUser: FullUser?): FullUser {
        // ensure that short users actually match before attempting any merge
        val ldapShortUser = ldapUser.shortUser ?: throw RuntimeException ("LDAP user does not have a short user. Maybe this will help {\"ldapUser\":\"${ldapUser.toString()},\"dbUser\":\"${dbUser.toString()}\"}")
        if (dbUser == null) return ldapUser
        if (ldapShortUser != dbUser.shortUser) throw RuntimeException ("Attempt to merge to different users {\"ldapUser\":\"${ldapUser.toString()},\"dbUser\":\"${dbUser.toString()}\"}")
        // proceed with data merge
        val newUser = ldapUser.copy()
        newUser.withDbData(dbUser)
        newUser.studentId = if (ldapUser.studentId!=-1) ldapUser.studentId else dbUser.studentId
        newUser.preferredName = dbUser.preferredName
        newUser.nick = dbUser.nick
        newUser.colorPrinting = dbUser.colorPrinting
        newUser.jobExpiration = dbUser.jobExpiration
        return newUser
    }
    /**
     * Uploads a [user] to the DB,
     * with logging for failures
     */
    internal fun updateDbWithUser(user: FullUser) {
        log.trace("Update db instance")
        try {
            val response = CouchDb.path("u${user.shortUser}").putJson(user)
            if (response.isSuccessful) {
                val responseObj = response.readEntity(ObjectNode::class.java)
                val newRev = responseObj.get("_rev")?.asText()
                if (newRev != null && newRev.length > 3) {
                    user._rev = newRev
                    log.trace("New rev for ${user.shortUser}: $newRev")
                }
            } else {
                log.error("Response failed: $response")
            }
        } catch (e1: Exception) {
            log.error("Could not put ${user.shortUser} into db", e1)
        }
    }


    /**
     * Retrieve a [FullUser] directly from the database when supplied with either a
     * short user, long user, or student id
     */
    fun queryUserDb(sam: String?): FullUser? {
        sam ?: return null
        val dbUser = when {
            sam.contains(".") ->
                CouchDb
                    .path(CouchDb.CouchDbView.ByLongUser)
                    .queryParam("key", "\"${sam.substringBefore("@")}%40${Config.ACCOUNT_DOMAIN}\"")
                    .getViewRows<FullUser>()
                    .firstOrNull()
            sam.matches(numRegex) ->
                CouchDb
                    .path(CouchDb.CouchDbView.ByStudentId)
                    .queryParam("key", sam)
                    .getViewRows<FullUser>()
                    .firstOrNull()
            else -> CouchDb.path("u$sam").getJsonOrNull()
        }
        dbUser?._id ?: return null
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
