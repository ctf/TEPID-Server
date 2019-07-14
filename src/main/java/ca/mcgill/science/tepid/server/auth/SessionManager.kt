package ca.mcgill.science.tepid.server.auth

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.bindings.LOCAL
import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.isSuccessful
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
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
    private val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")

    private val random = SecureRandom()

    fun start(user: FullUser, expiration: Int): FullSession {
        val session = FullSession(user = user, expiration = System.currentTimeMillis() + expiration * HOUR_IN_MILLIS)
        val id = BigInteger(130, random).toString(32)
        session._id = id
        session.role = AuthenticationFilter.getCtfRole(session.user)
        log.trace("Starting session {\"id\":\"$id\", \"shortUser\":\"${user.shortUser}\",\"duration\":\"${expiration * HOUR_IN_MILLIS}\", \"expiration\":\"${session.expiration}\"}")
        val out = DB.putSession(session)
        log.trace(out)
        return session
    }

    operator fun get(token: String): FullSession? {
        val session = DB.getSessionOrNull(token) ?: return null
        if (isValid(session)) return session
        log.trace("Deleting session token {\"token\":\"$token\", \"expiration\":\"${session.expiration}\", \"now\":\"${System.currentTimeMillis()}\",\"sessionRole\":\"${session.role}\", \"userRole\":\"${queryUserDb(session.user.shortUser)?.role}\"}")
        DB.deleteSession(token)
        return null
    }


    /**
     * Check if session isn't expired and has the cached role
     *
     * @param session the fullSession to be tested
     * @return true for valid, false otherwise
     */
    fun isValid(session: FullSession): Boolean {
        if (!session.isUnexpired()) return false
        if (session.role != queryUserDb(session.user.shortUser)?.role) return false
        return true
    }

    fun end(token: String) {
        //todo test
        DB.deleteSession(token)
    }

    /**
     * Invalidates all of the sessions belonging to a certain user.
     *
     * @param shortUser the shortUser
     */
    fun invalidateSessions(shortUser: String) {
        DB.getSessionIdsForUser(shortUser).forEach{ SessionManager.end(it) }
    }

    /**
     * Authenticates user as appropriate:
     * first with local auth (if applicable), then against LDAP (if enabled)
     *
     * @param sam short user
     * @param pw  password
     * @return authenticated user, or null if auth failure
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        val dbUser = queryUserDb(sam)
        log.trace("Db data found for $sam")
        return when {
            dbUser?.authType == LOCAL -> if (BCrypt.checkpw(pw, dbUser.password)) dbUser else null
            Config.LDAP_ENABLED -> {
                var ldapUser = Ldap.authenticate(sam, pw)
                if (ldapUser != null) {
                    ldapUser = mergeUsers(ldapUser, dbUser)
                    updateDbWithUser(ldapUser)
                }
                ldapUser
            }
            else -> null
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
        log.trace("Querying user: {\"sam\":\"$sam\"}")

        val dbUser = queryUserDb(sam)

        if (dbUser != null) return dbUser

        if (Config.LDAP_ENABLED) {
            if (!sam.matches(shortUserRegex)) return null // cannot query without short user
            val ldapUser = Ldap.queryUserLdap(sam, pw) ?: return null

            updateDbWithUser(ldapUser)

            log.trace("Found user from ldap {\"sam\":\"$sam\", \"longUser\":\"${ldapUser.longUser}\"}")
            return ldapUser
        }
        //finally
        return null
    }

    /**
     * Merge users from LDAP and DB for their corresponding authorities
     * Returns a new users (does not mutate either input
     */
    fun mergeUsers(ldapUser: FullUser, dbUser: FullUser?): FullUser {
        // ensure that short users actually match before attempting any merge
        val ldapShortUser = ldapUser.shortUser
                ?: throw RuntimeException("LDAP user does not have a short user. Maybe this will help {\"ldapUser\":\"$ldapUser,\"dbUser\":\"$dbUser\"}")
        if (dbUser == null) return ldapUser
        if (ldapShortUser != dbUser.shortUser) throw RuntimeException("Attempt to merge to different users {\"ldapUser\":\"$ldapUser,\"dbUser\":\"$dbUser\"}")
        // proceed with data merge
        val newUser = ldapUser.copy()
        newUser.withDbData(dbUser)
        newUser.studentId = if (ldapUser.studentId != -1) ldapUser.studentId else dbUser.studentId
        newUser.preferredName = dbUser.preferredName
        newUser.nick = dbUser.nick
        newUser.colorPrinting = dbUser.colorPrinting
        newUser.jobExpiration = dbUser.jobExpiration
        newUser.updateUserNameInformation()
        return newUser
    }

    /**
     * Uploads a [user] to the DB,
     * with logging for failures
     */
    fun updateDbWithUser(user: FullUser) {
        val shortUser = user.shortUser ?:  return log.error("Cannot update user, shortUser is null {\"user\": \"$user\"}")
        log.trace("Update db instance {\"user\":\"$shortUser\"}\n")
        try {
            val response = DB.putUser(user)
            if (response.isSuccessful) {
                log.trace("Updated User {\"user\": \"$shortUser\"}")
            } else {
                log.error("Updating DB with user failed: {\"user\": \"$shortUser\",\"response\":\"$response\"}")
            }
        } catch (e1: Exception) {
            log.error("Error updating DB with user: {\"user\": \"$shortUser\"}", e1)
        }
    }


    /**
     * Retrieve a [FullUser] directly from the database when supplied with either a
     * short user, long user, or student id
     */
    fun queryUserDb(sam: String?): FullUser? {
        sam ?: return null
        val dbUser = DB.getUserOrNull(sam)
        dbUser?._id ?: return null
        log.trace("Found db user {\"sam\":\"$sam\",\"db_id\":\"${dbUser._id}\", \"dislayName\":\"${dbUser.displayName}\"}")
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
     * Sets exchange student status.
     * Also updates user information from LDAP.
     * This refreshes the groups and courses of a user,
     * which allows for thier role to change
     *
     * @param sam      shortUser
     * @param exchange boolean for exchange status
     * @return updated status of the user; false if anything goes wrong
     */
    fun setExchangeStudent(sam: String, exchange: Boolean): Boolean {
        if (Config.LDAP_ENABLED) {
            log.info("Setting exchange status {\"sam\":\"$sam\", \"exchange_status\":\"$exchange\"}")
            val success = Ldap.setExchangeStudent(sam, exchange)
            val dbUser = queryUserDb(sam)
            val ldapUser = Ldap.queryUserLdap(sam, null) ?: return false
            val mergedUser = mergeUsers(ldapUser, dbUser)
            updateDbWithUser(mergedUser)
            return success
        } else return false
    }

    fun refreshUser(sam: String): FullUser {
        val dbUser = queryUserDb(sam)
        if (dbUser == null){
            log.info("Could not fetch user from DB {\"sam\":\"$sam\"}")
            return queryUser(sam, null) ?: throw RuntimeException("Could not fetch user from anywhere {\"sam\":\"$sam\"}")
        }
        if (Config.LDAP_ENABLED) {
            val ldapUser = Ldap.queryUserLdap(sam, null) ?: throw RuntimeException("Could not fetch user from LDAP {\"sam\":\"$sam\"}")
            val refreshedUser = mergeUsers(ldapUser, dbUser)
            if (dbUser.role != refreshedUser.role) {
                invalidateSessions(sam)
            }
            updateDbWithUser(refreshedUser)
            return refreshedUser
        } else {
            return dbUser
        }
    }
}
