package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.postJson
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.JsonNodeFactory

class SessionMonitor : Runnable {

    override fun run() {
        log.info("Removing expired sessions from database.")
        try {
            val sessions = CouchDb.getViewRows<FullSession>("sessions")
            val nf = JsonNodeFactory.instance
            val root = nf.objectNode()
            val docs = root.putArray("docs")
            sessions.filter {
                !it.isValid()
            }.forEach {
                val node = nf.objectNode()
                        .put("_id", it._id)
                        .put("_rev", it._rev)
                        .put("_deleted", true)
                docs.add(node)
            }
            log.info("Removal successful") // todo delete later?
            if (docs.size() > 0)
                CouchDb.path("_bulk_docs").postJson(root)
        } catch (e: Exception) {
            log.error("General failure", e)
        }
    }

    private companion object : WithLogging()

}
