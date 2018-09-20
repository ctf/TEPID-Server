package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.printing.Printer

class JobMonitor : Runnable {

    override fun run() {
        Printer.clearOldJobs()
    }

}
