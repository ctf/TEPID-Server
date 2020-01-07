package ca.mcgill.science.tepid.server.printing.loadbalancers

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

    protected fun allDown(): Boolean = queueManager.destinations.fold(true, { a, dest -> a && !dest.up })

    init {
        log.trace {
            logMessage(
                "Initialized loadbalancer",
                "destinationCount" to queueManager.destinations.size,
                "allDown" to allDown()
            )
        }
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