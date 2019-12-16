package ca.mcgill.science.tepid.server.printing.loadbalancers

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.printing.QueueManager
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.*

abstract class LoadBalancer(queueManager: QueueManager) {
    @JvmField
    protected val queueManager = queueManager
    @JvmField
    protected val log = logger("Queue - " + queueManager.printQueue.name)
    @JvmField
    protected val destinations: MutableList<FullDestination>
    @JvmField
    protected var allDown = true

    init {
        destinations = ArrayList(queueManager.printQueue.destinations.size)
        refreshDestinations()
        log.trace {
            logMessage(
                "Initialized loadbalancer",
                "destinationCount" to destinations.size,
                "allDown" to allDown
            )
        }
    }

    fun refreshDestinations() {
        allDown = true
        destinations.clear() // clear out the old Destination objects
        for (d in queueManager.printQueue.destinations) {
            val dest = db.getDestination(d)
            destinations.add(dest) // replace with shiny new Destination objects
            val up = dest.up
            if (up) allDown = false
            log.trace { logMessage("Loadbalancer checking status", "dest" to dest.name, "getUp" to up) }
        }
        // maybe we should be concerned about the efficiency of a db query for every dest in the queue on every print job...
    }

    data class LoadBalancerResults(var destination: String, var eta: Long)

    abstract fun processJob(j: PrintJob): LoadBalancerResults?

    companion object {

        @JvmStatic
        protected val db = DB

        /*
         * Registry
         */
        private val loadBalancerFactories: MutableMap<String, (QueueManager) -> LoadBalancer> =
            HashMap()

        @JvmStatic
        fun registerLoadBalancer(name: String, loadBalancerFactory: (QueueManager) -> LoadBalancer) {
            loadBalancerFactories[name] = loadBalancerFactory
        }

        fun getLoadBalancerFactories(): Set<Map.Entry<String, (QueueManager) -> LoadBalancer>> {
            return loadBalancerFactories.entries
        }

        @JvmStatic
        fun getLoadBalancerFactory(name: String): (QueueManager) -> LoadBalancer {
            return loadBalancerFactories.getOrDefault(name, ::FiftyFifty)
        }
    }
}