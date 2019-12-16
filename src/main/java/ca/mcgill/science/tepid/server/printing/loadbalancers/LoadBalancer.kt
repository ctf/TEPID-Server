package ca.mcgill.science.tepid.server.printing.loadbalancers

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.printing.QueueManager
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.function.Function

abstract class LoadBalancer(queueManager: QueueManager) {
    @JvmField
    protected val queueManager = queueManager
    @JvmField
    protected val log: Logger
    @JvmField
    protected val destinations: MutableList<FullDestination>
    @JvmField
    protected var allDown = true
    fun refreshDestinations() {
        allDown = true
        destinations.clear() // clear out the old Destination objects
        for (d in queueManager.printQueue.destinations) {
            val dest = db.getDestination(d)
            destinations.add(dest) // replace with shiny new Destination objects
            val up = dest.up
            if (up) allDown = false
            log.trace("Checking status {\'dest\':\'{}\', \'getUp\':\'{}\'}", dest.name, up)
        }
        // maybe we should be concerned about the efficiency of a db query for every dest in the queue on every print job...
    }

    data class LoadBalancerResults(var destination: String, var eta: Long)

    abstract fun processJob(j: PrintJob?): LoadBalancerResults?

    companion object {

        @JvmField
        protected val db = DB
        /*
     * Registry
     */
        private val loadBalancerFactories: MutableMap<String, Function<QueueManager, out LoadBalancer>> =
            HashMap()

        @JvmStatic
        fun registerLoadBalancer(
            name: String,
            loadBalancerFactory: Function<QueueManager, out LoadBalancer>
        ) {
            loadBalancerFactories[name] = loadBalancerFactory
        }

        fun getLoadBalancerFactories(): Set<Map.Entry<String, Function<QueueManager, out LoadBalancer>>> {
            return loadBalancerFactories.entries
        }

        @JvmStatic
        fun getLoadBalancerFactory(name: String): Function<QueueManager, out LoadBalancer> {
            return loadBalancerFactories.getOrDefault(
                name,
                Function<QueueManager, LoadBalancer> { qm: QueueManager? -> FiftyFifty(qm) }
            )
        }
    }

    init {
        log = LogManager.getLogger("Queue - " + queueManager.printQueue.name)
        destinations = ArrayList(queueManager.printQueue.destinations.size)
        refreshDestinations()
        log.trace("Initialized with {}; allDown {}", destinations.size, allDown)
    }
}