package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import javax.ws.rs.core.Response

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

enum class Order {
    ASCENDING, DESCENDING
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
        DbUserLayer

interface DbDestinationLayer {

    fun getDestinations(): Map<Id, FullDestination>

    fun putDestinations(destinations: Map<Id, FullDestination>)

    fun updateDestination(id: Id, updater: FullDestination.() -> Unit): Response

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

    fun getJobChanges(id: Id): ChangeDelta

    fun getJobFile(id: Id, file: String): Response

}

interface DbQueueLayer {

    fun getQueues(): List<PrintQueue>

    fun putQueues(queues: Collection<PrintQueue>): Response

}

interface DbMarqueeLayer {

    fun getMarquees(): List<MarqueeData>

}

interface DbSessionLayer {

    fun putSession(session: FullSession): Response

    fun getSessionOrNull(id: Id): FullSession?

    fun deleteSession(id: Id): String

}

interface DbUserLayer {

    fun putUser(user: FullUser): Response

    fun getUserOrNull(sam: Sam): FullUser?

    fun isAdminConfigured(): Boolean

    // TODO delete? Or at least use shortUser from user
    fun putAdmin(shortUser: String, user: FullUser): Response

    fun getTotalPrintedCount(shortUser: String): Int
}

