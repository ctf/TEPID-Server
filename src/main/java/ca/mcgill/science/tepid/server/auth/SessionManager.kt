package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.Id
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import java.math.BigInteger
import java.security.SecureRandom

/**
 * SessionManager is responsible for managing sessions. It doesn't do authentication. Why would it do that?
 */

object SessionManager : Logging {
    private const val HOUR_IN_MILLIS = 60 * 60 * 1000

    private val random = SecureRandom()

    fun start(user: FullUser, expiration: Int): FullSession {
        val session = FullSession(user = user, expiration = System.currentTimeMillis() + expiration * HOUR_IN_MILLIS)
        val id = BigInteger(130, random).toString(32)
        session._id = id
        session.role = AuthenticationFilter.getCtfRole(session.user)
        logger.trace {
            logMessage(
                "starting session",
                "id" to id,
                "shortUser" to user.shortUser,
                "duration" to expiration * HOUR_IN_MILLIS,
                "expiration" to session.expiration
            )
        }
        DB.sessions.put(session)
        return session
    }

    operator fun get(token: String): FullSession? {
        val session = DB.sessions.readOrNull(token) ?: return null
        if (isValid(session)) return session
        logger.trace {
            logMessage(
                "deleting session token",
                "token" to token,
                "expiration" to session.expiration,
                "now" to System.currentTimeMillis()
            )
        }
        DB.sessions.deleteById(token)
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
        val shortUser = session.user.shortUser ?: return false
        if (session.role != AuthenticationManager.queryUserDb(shortUser)?.role) return false
        return true
    }

    fun end(token: String) {
        DB.sessions.deleteById(token)
    }

    /**
     * Invalidates all of the sessions belonging to a certain user.
     */
    fun invalidateSessions(id: Id) {
        DB.sessions.getSessionIdsForUser(id).forEach { end(it) }
    }
}
