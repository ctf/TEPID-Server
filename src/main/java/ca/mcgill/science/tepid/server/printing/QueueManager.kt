package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.enums.PrintError
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer.Companion.getLoadBalancerFactory
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer.LoadBalancerResults
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*

class QueueManager private constructor(id: String?) {
    private val log: Logger
    var printQueue: PrintQueue
    private val loadBalancer: LoadBalancer

    init {
        try {
            printQueue = db.getQueue(id!!)
        } catch (e: Exception) {
            throw RuntimeException("Could not instantiate queue manager", e)
        }
        log = LogManager.getLogger("Queue - " + printQueue.name)
        log.trace("Instantiate queue manager {\'queueName\':\'{}\'}", printQueue.name)
        loadBalancer = getLoadBalancerFactory(printQueue.loadBalancer!!)(this)
    }

    fun assignDestination(id: String?): PrintJob? {
        return db.updateJob(id!!) {
            val results: LoadBalancerResults? = loadBalancer.processJob(this)
            if (results == null) {
                this.fail(PrintError.INVALID_DESTINATION)
                log.info(
                    "LoadBalancer did not assign a destination {\'PrintJob\':\'{}\', \'LoadBalancer\':\'{}\'}",
                    this.getId(),
                    printQueue.getId()
                )
            } else {
                this.destination = results.destination
                this.eta = results.eta
                log.info(this.getId() + " setting destination (" + results.destination + ")")
            }
        }
    }

    // TODO check use of args
    fun getEta(destination: String?): Long {
        var maxEta: Long = 0
        try {
            maxEta = db.getEta(destination!!)
        } catch (ignored: Exception) {
        }
        return maxEta
    }

    companion object {
        val db = DB

        private val instances: MutableMap<String?, QueueManager> =
            HashMap()

        fun getInstance(queueId: String?): QueueManager {
            synchronized(instances) { return instances.computeIfAbsent(queueId, ::QueueManager) }
        }

        fun assignDestination(job: PrintJob): PrintJob? {
            return getInstance(job.queueId).assignDestination(job.getId())
        }
    }
}