package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.printer.Printer

class JobMonitor : Runnable {

    override fun run() {
        Printer.clearOldJobs()
    }

}
