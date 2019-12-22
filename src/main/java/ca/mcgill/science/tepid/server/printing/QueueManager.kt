package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.FullDestination
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

open class QueueManager protected constructor(var printQueue: PrintQueue, var destinations: MutableList<FullDestination>) {
    protected val log: KotlinLogger
    protected val loadBalancer: LoadBalancer

    init {
        // log = LogManager.getLogger("Queue - " + printQueue.name)
        log = logger("Queue - " + printQueue.name)
        log.trace { logMessage("Instantiate queue manager", "queueName" to printQueue.name) }


        log.trace {
            logMessage(
                "Initialized loadbalancer",
                "destinationCount" to destinations.size
            )
        }

        loadBalancer = getLoadBalancerFactory(printQueue.loadBalancer!!)(this)
    }

    fun refreshDestinations() {
        destinations.clear() // clear out the old Destination objects
        for (d in printQueue.destinations) {
            val dest = db.getDestination(d)
            destinations.add(dest) // replace with shiny new Destination objects
            val up = dest.up
            log.trace { logMessage("Loadbalancer checking status", "dest" to dest.name, "getUp" to up) }
        }
        // maybe we should be concerned about the efficiency of a db query for every dest in the queue on every print job...
    }

    fun assignDestination(id: String): PrintJob? {
        refreshDestinations()
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
                val printQueue = db.getQueue(id)
                val QM = QueueManager(printQueue, mutableListOf())
                QM.refreshDestinations()
                return QM
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