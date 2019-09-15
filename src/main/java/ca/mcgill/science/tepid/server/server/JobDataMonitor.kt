package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.WithLogging
import java.io.File

class JobDataMonitor : Runnable {

    override fun run() {
        log.trace("Deleting expired job data.")
        val now = System.currentTimeMillis()
        try {
            DB.getStoredJobs().forEach { j ->

                val id = j._id ?: return@forEach
                DB.updateJob(id) {
                    if (deleteDataOn < System.currentTimeMillis()) {

                        val filePath = file
                        if (filePath != null) {
                            try {
                                val f = File(filePath)
                                if (f.exists() && !f.delete())
                                    log.error("Failed to delete file")
                            } catch (e: Exception) {
                                log.error("Failed to delete file: ${e.message}")
                            }
                            file = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("General failure", e)
        }

        log.info("Deleting expired jobs completed in ${System.currentTimeMillis() - now} millis")
    }

    private companion object : WithLogging()
}
