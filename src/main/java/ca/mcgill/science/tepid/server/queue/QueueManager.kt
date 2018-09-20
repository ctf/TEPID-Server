package ca.mcgill.science.tepid.server.queue

import ca.allanwang.kotlin.lazyResettable
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.util.*
import org.apache.logging.log4j.LogManager

data class QueueResult(val destination: String, val eta: Long)

class QueueManager private constructor(private val queue: PrintQueue,
                                       logic: String? = null) {

    /**
     * Name identifying the manager
     * This will be referenced from [PrintJob.queueName]
     * to figure out which queue manager will be used
     */
    val name: String = queue.name!!

    private val log = LogManager.getLogger("Queue - $name")

    private val lock = Any()

    /**
     * The handler that will delegate destinations
     */
    var logic: QueueLogic = QueueLogic.create(logic)
        set(value) {
            synchronized(lock) {
                if (field.name == value.name)
                    return // already using this logic
                field = value
                queue.loadBalancer = value.name // todo save to db
                /*
                 * Update relevant fields here
                 * Different logic sets may keep track of different data
                 * Something needs to be done to pass on the information
                 */
            }
        }

    /**
     * The list of destinations available for the manager
     */
    private val destinations: List<FullDestination> =
            queue.destinations.mapNotNull {
                CouchDb.path(it).getJsonOrNull<FullDestination>()
            }

    private val isAllDownDelegate = lazyResettable { destinations.none { it.up } }

    val isAllDown: Boolean by isAllDownDelegate

    /**
     * Queue the given print job and emit the updated print job
     * If everything was queued properly, the new job will be nonnull
     */
    fun queue(job: PrintJob): PrintJob? {
        val id = job._id

        if (id == null) {
            log.error("Received job with null id")
            return null
        }
        if (isAllDown) {
            log.error("All printers down")
            return null
        }
        val result = getDestination(job) ?: return null
        job.destination = result.destination
        job.eta = result.eta
        log.trace("Received job result $result")
        val response = CouchDb.path(id).putJson(job)
        if (!response.isSuccessful)
            log.error("Failed to put job: $response")
        // todo see if this retrieval is necessary, or if we can update the rev directly
        return CouchDb.path(id).getJson()
    }

    fun destinationChanged() {
        isAllDownDelegate.invalidate()
    }

    private fun getDestination(job: PrintJob): QueueResult? {
        synchronized(lock) {
            return logic.getResult(job, destinations)
        }
    }

    override fun equals(other: Any?): Boolean =
            other is QueueManager && name == other.name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String =
            "Queue $name; ${destinations.size} destinations; ${if (isAllDown) "down" else "up"}"

    companion object {
        fun create(name: String): QueueManager? {
            val queue = CouchDb.path("q$name").getJsonOrNull<PrintQueue>()
            queue?.name ?: return null
            return QueueManager(queue, queue.loadBalancer)
        }
    }
}