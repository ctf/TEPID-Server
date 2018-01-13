package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.util.CouchDb
import ca.mcgill.science.tepid.server.util.putJson
import ca.mcgill.science.tepid.server.util.query
import java.util.*

class JobMonitor : Runnable {

    init {
        println("Create JobMonitor")
    }

    override fun run() {
        try {
            val jobs = CouchDb.getViewRows<PrintJob>("oldJobs") {
                query("endkey" to System.currentTimeMillis() - 1800000)
            }

            jobs.forEach { j ->
                j.setFailed(Date(), "Timed out")
                val id = j._id
                CouchDb.path(id).putJson(j)
                val t = Jobs.processingThreads[id]
                try {
                    t?.interrupt()
                } catch (ignored: Exception) {
                }
                Jobs.processingThreads.remove(j.getId())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

}
