package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import java.io.InputStream
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo

lateinit var DB: DbLayer

// TODO deleteDestination should return Response instead of String
// TODO, all outputs returning response should likely return models that can then be wrapped inside a response
// TODO outputs should be nullable

/**
 * Ids are unique string keys in dbs
 */
typealias Id = String

/**
 * String representing a:
 * - short user
 * - long user
 * - student id
 */
typealias Sam = String
typealias ShortUser = String

enum class Order {
    ASCENDING {
        override fun <T : Comparable<T>> sort(iterable: Iterable<T>): List<T> =
                iterable.sorted()
    }, DESCENDING {
        override fun <T : Comparable<T>> sort(iterable: Iterable<T>): List<T> =
                iterable.sortedDescending()
    };

    abstract fun <T : Comparable<T>> sort(iterable: Iterable<T>): List<T>
}

/**
 * Abstraction layer for all db transactions.
 * Inputs and outputs must be pure JVM models that are separated from db models.
 * Note that layers are categorized based on particular models to help with delegation patterns.
 * Each method in each of the included interfaces should still be unique with respect to all other methods.
 */
interface DbLayer :
        DbDestinationLayer,
        DbJobLayer,
        DbQueueLayer,
        DbMarqueeLayer,
        DbSessionLayer,
        DbUserLayer {
    fun <T: Comparable<T>> List<T>.sortAs(order: Order): List<T> = order.sort(this)
}

interface DbDestinationLayer {

    fun getDestination(id: Id): FullDestination

    fun getDestinations(): List<FullDestination>

    fun putDestinations(destinations: Map<Id, FullDestination>): String

    fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response

    /**
     * Returns a string result representing a response entity
     */
    fun deleteDestination(id: Id): String
}

interface DbJobLayer {

    fun getJob(id: Id): PrintJob

    /**
     * Get jobs by [queue]
     * If [maxAge] > 0, then only jobs created before (now - [maxAge])ms will be included
     * If [limit] > 0, then only [limit] jobs will be provided
     */
    fun getJobsByQueue(queue: String,
                       maxAge: Long = -1,
                       sortOrder: Order = Order.DESCENDING,
                       limit: Int = -1): List<PrintJob>

    fun getJobsByUser(sam: Sam, sortOrder: Order = Order.DESCENDING): List<PrintJob>

    /**
     * Updates the job, and returns the new job if successful
     */
    fun updateJob(id: Id, updater: PrintJob.() -> Unit): PrintJob?

    fun postJob(job: PrintJob): Response

    fun getJobChanges(id: Id, uriInfo: UriInfo): ChangeDelta

    fun getJobFile(id: Id, file: String): InputStream?

    /**
     * Returns earliest job in ms
     * Defaults to -1 if not found
     */
    fun getEarliestJobTime(shortUser: ShortUser): Long
}

interface DbQueueLayer {

    fun getQueues(): List<PrintQueue>

    fun putQueues(queues: Collection<PrintQueue>): Response

    fun deleteQueue(id: Id): String

}

interface DbMarqueeLayer {

    fun getMarquees(): List<MarqueeData>

}

interface DbSessionLayer {

    fun putSession(session: FullSession): Response

    fun getSessionOrNull(id: Id): FullSession?

    fun getSessionIdsForUser(shortUser: ShortUser): List<Id>

    fun deleteSession(id: Id): String

}

interface DbUserLayer {

    fun putUser(user: FullUser): Response

    fun getUserOrNull(sam: Sam): FullUser?

    fun isAdminConfigured(): Boolean

    fun getTotalPrintedCount(shortUser: ShortUser): Int
}

