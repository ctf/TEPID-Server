package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging

class SessionMonitor : Runnable {

    override fun run() {
        logger.info("removing expired sessions from database")
        try {
            var numberRemoved = 0
            val sessions = DB.getAllSessions()
            sessions.filter {
                !it.isUnexpired()
            }.forEach {
                val id = it._id ?: return@forEach
                DB.deleteSession(id)
                numberRemoved += 1
            }
            logger.info(logMessage("removed sessions successfully", "count" to numberRemoved))
        } catch (e: Exception) {
            logger.error("general failure removing expired sessions", e)
        }
    }

    private companion object : Logging
}
