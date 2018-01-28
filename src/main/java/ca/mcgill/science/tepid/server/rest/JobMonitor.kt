package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.util.CouchDb
import ca.mcgill.science.tepid.server.util.putJson
import ca.mcgill.science.tepid.server.util.query
import ca.mcgill.science.tepid.utils.WithLogging

class JobMonitor : Runnable {

    override fun run() {
        try {
            val jobs = CouchDb.getViewRows<PrintJob>("oldJobs") {
                query("endkey" to System.currentTimeMillis() - 1800000)
            }

            jobs.forEach { j ->
                j.fail("Timed out")
                val id = j._id ?: return@forEach
                CouchDb.path(id).putJson(j)
                val t = Jobs.processingThreads[id]
                try {
                    t?.interrupt()
                } catch (ignored: Exception) {
                }
                Jobs.processingThreads.remove(id)
            }
        } catch (e: Exception) {
            log.error("General failure", e)
        }

    }

    private companion object : WithLogging()

}
