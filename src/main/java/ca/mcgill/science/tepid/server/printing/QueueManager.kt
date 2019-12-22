package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.enums.PrintError
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer.Companion.getLoadBalancerFactory
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer.LoadBalancerResults
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.KotlinLogger
import org.apache.logging.log4j.kotlin.logger
import java.util.*

open class QueueManager protected constructor(queue: PrintQueue) {
    private val log: KotlinLogger
    var printQueue: PrintQueue
    private val loadBalancer: LoadBalancer

    init {
        printQueue = queue
        // log = LogManager.getLogger("Queue - " + printQueue.name)
        log = logger("Queue - " + printQueue.name)
        log.trace { logMessage("Instantiate queue manager", "queueName" to printQueue.name) }
        loadBalancer = getLoadBalancerFactory(printQueue.loadBalancer!!)(this)
    }

    fun assignDestination(id: String): PrintJob? {
        return db.updateJob(id) {
            val results: LoadBalancerResults = loadBalancer.processJob(this) ?: run {
                this.fail(PrintError.INVALID_DESTINATION)
                log.info {
                    logMessage(
                        "LoadBalancer did not assign a destination",
                        "printJob" to this.getId(), "printqueue" to printQueue.getId()
                    )
                }
                return@updateJob
            }
            this.destination = results.destination
            this.eta = results.eta
            log.info { logMessage("Setting destination", "id" to this.getId(), "destination" to results.destination) }
        }
    }

    // TODO check use of args
    fun getEta(destination: String): Long {
        var maxEta: Long = 0
        try {
            maxEta = db.getEta(destination)
        } catch (ignored: Exception) {
        }
        return maxEta
    }

    companion object {
        val db = DB

        private val instances: MutableMap<String, QueueManager> =
            HashMap()

        private fun getFromDb(id: String): QueueManager {
            try {
                return QueueManager(db.getQueue(id))
            } catch (e: Exception) {
                throw RuntimeException("Could not instantiate queue manager", e)
            }
        }

        fun getInstance(queueId: String): QueueManager {
            synchronized(instances) { return instances.computeIfAbsent(queueId, ::getFromDb) }
        }

        fun assignDestination(job: PrintJob): PrintJob? {
            return job.queueId?.let { getInstance(it).assignDestination(job.getId()) }
        }
    }
}