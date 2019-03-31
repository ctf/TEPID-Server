package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.WithLogging

class SessionMonitor : Runnable {

    override fun run() {
        log.info("Removing expired sessions from database.")
        try {
            var numberRemoved = 0
            val sessions = DB.getAllSessions()
            sessions.filter {
                !it.isValid()
            }.forEach {
                val id = it._id ?: return@forEach
                DB.deleteSession(id)
                numberRemoved += 1
            }
            log.info("Removed $numberRemoved sessions successfully")
        } catch (e: Exception) {
            log.error("General failure", e)
        }
    }

    private companion object : WithLogging()

}
