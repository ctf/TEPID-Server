package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.putJson
import ca.mcgill.science.tepid.utils.WithLogging
import java.io.File

class JobDataMonitor : Runnable {

    override fun run() {
        log.trace("Deleting expired job data.")
        val now = System.currentTimeMillis()
        try {
            DB.getStoredJobs().forEach { j ->
                if (j.deleteDataOn < System.currentTimeMillis()) {
                    val filePath = j.file
                    if (filePath != null) {
                        try {
                            val f = File(filePath)
                            if (f.exists() && !f.delete())
                                log.error("Failed to delete file")
                        } catch (ignored: Exception) {
                        }

                        j.file = null
                    }
                    val id = j._id ?: return
                    CouchDb.path(id).putJson(j)
                }
            }
        } catch (e: Exception) {
            log.error("General failure", e)
        }

        log.info("Deleting expired jobs completed in ${System.currentTimeMillis() - now} millis")
    }

    private companion object : WithLogging()

}
