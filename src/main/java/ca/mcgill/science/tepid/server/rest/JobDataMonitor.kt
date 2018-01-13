package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.util.CouchDb
import ca.mcgill.science.tepid.server.util.putJson
import ca.mcgill.science.tepid.utils.WithLogging
import java.io.File

class JobDataMonitor : Runnable {

    init {
        println("Init JobDataMonitor KT")
    }

    override fun run() {
        log.info("Deleting expired job data.")
        val now = System.currentTimeMillis()
        try {
            CouchDb.getViewRows<PrintJob>("storedJobs").forEach { j ->
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

                    j.additionalProperties.remove("_attachments")
                    CouchDb.path(j._id).putJson(j)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        log.info("Deleting expired jobs completed in ${System.currentTimeMillis() - now} millis")
    }

    companion object : WithLogging()

}
