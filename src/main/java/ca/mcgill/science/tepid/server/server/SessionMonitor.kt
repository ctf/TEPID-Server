package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.util.logAnnounce
import org.apache.logging.log4j.kotlin.Logging

class SessionMonitor : Runnable {

    override fun run() {
        logger.info("removing expired sessions from database")
        try {
            var numberRemoved = 0
            val sessions = DB.sessions.getAllSessions()
            sessions.filter {
                !it.isUnexpired()
            }.forEach {
                val id = it._id ?: return@forEach
                DB.sessions.deleteSession(id)
                numberRemoved += 1
            }
            logger.info(logAnnounce("removed sessions successfully", "count" to numberRemoved))
        } catch (e: Exception) {
            logger.error("general failure removing expired sessions", e)
        }
    }

    private companion object : Logging
}
