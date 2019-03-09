package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.JsonNodeFactory

class SessionMonitor : Runnable {

    override fun run() {
        log.info("Removing expired sessions from database.")
        try {
            var numberRemoved = 0
            val sessions = CouchDb.getViewRows<FullSession>("sessions")
            val nf = JsonNodeFactory.instance
            val root = nf.objectNode()
            val docs = root.putArray("docs")
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
