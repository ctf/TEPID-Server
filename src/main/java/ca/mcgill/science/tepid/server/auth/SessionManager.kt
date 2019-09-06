package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.WithLogging
import java.math.BigInteger
import java.security.SecureRandom

/**
 * SessionManager is responsible for managing sessions and dealing with the underlying authentication.
 * It is analogous to PAM, in that everything which needs authentication or user querying goes through this.
 * For managing sessions, it can start, resume, and end sessions
 * For user querying, it first checks the DB cache. The cache is updated every time a query to the underlying authentication is made.
 */

object SessionManager : WithLogging() {
    private const val HOUR_IN_MILLIS = 60 * 60 * 1000

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
        log.trace("Deleting session token {\"token\":\"$token\", \"expiration\":\"${session.expiration}\", \"now\":\"${System.currentTimeMillis()}\",\"sessionRole\":\"${session.role}\", \"userRole\":\"${AuthenticationManager.queryUserDb(session.user.shortUser)?.role}\"}")
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
        if (session.role != AuthenticationManager.queryUserDb(session.user.shortUser)?.role) return false
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
        DB.getSessionIdsForUser(shortUser).forEach { end(it) }
    }
}
