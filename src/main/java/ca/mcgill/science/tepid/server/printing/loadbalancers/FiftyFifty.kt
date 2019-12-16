package ca.mcgill.science.tepid.server.printing.loadbalancers

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.printing.QueueManager

class FiftyFifty(qm: QueueManager?) : LoadBalancer(qm!!) {
    private var currentDest = 0

    companion object {
        const val name = "fiftyfifty"

        init {
            registerLoadBalancer(name, ::FiftyFifty)
        }
    }

    /**
     * Cycle through available [Destination] and sends job to the first one available
     *
     * @param j PrintJob
     * @return result
     */
    override fun processJob(j: PrintJob?): LoadBalancerResults? {
        refreshDestinations()
        if (allDown) {
            log.warn("Rejecting job {} as all {} printers are down", j!!.getId(), destinations.size)
            return null
        }
        do currentDest = (currentDest + 1) % destinations.size while (!destinations[currentDest].up)
        val destinationId = destinations[currentDest].getId()
        val dest = db.getDestination(destinationId)
        log.trace(
            "Load balancer sending job to destination {\'LoadBalancer\':\'{}\', \'job\':\'{}\', \'destination\':\'{}\'} ",
            queueManager.printQueue.name,
            j!!._id,
            dest.name
        )
        return LoadBalancerResults(destinationId, getEta(j, dest))
    }

    /**
     * Retrieve estimated eta based on existing queue
     *
     * @param j current print job
     * @param d destination for print
     * @return long for estimation
     */
    private fun getEta(j: PrintJob?, d: FullDestination): Long {
        var eta = Math.max(queueManager.getEta(d.getId()), System.currentTimeMillis())
        log.trace("current max: $eta")
        eta += Math.round(j!!.pages / d.ppm.toDouble() * 60.0 * 1000.0)
        log.debug("new eta (" + d.ppm + "ppm): " + eta)
        return eta
    }
}