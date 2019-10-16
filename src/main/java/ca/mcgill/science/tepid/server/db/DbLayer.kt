package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import java.io.InputStream
import javax.ws.rs.core.Response

var DB: DbLayer = Config.getDb()

// TODO deleteDestination should return Response instead of String
// TODO, all outputs returning response should likely return models that can then be wrapped inside a response
// TODO outputs should be nullable

/**
 * Ids are unique string keys in dbs
 */
typealias Id = String

enum class Order {
    ASCENDING {
        override fun <T : Comparable<T>> sort(iterable: Iterable<T>): List<T> =
            iterable.sorted()
    },
    DESCENDING {
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
    fun <T : Comparable<T>> List<T>.sortAs(order: Order): List<T> = order.sort(this)
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
    fun getJobsByQueue(
        queue: String,
        maxAge: Long = -1,
        sortOrder: Order = Order.DESCENDING,
        limit: Int = -1
    ): List<PrintJob>

    fun getJobsByUser(sam: PersonalIdentifier, sortOrder: Order = Order.DESCENDING): List<PrintJob>

    /**
     * Gets jobs which have a value for their file, implying the contents are still stored in TEPID
     */
    fun getStoredJobs(): List<PrintJob>

    /**
     * Updates the job, and returns the new job if successful
     */
    fun updateJob(id: Id, updater: PrintJob.() -> Unit): PrintJob?

    fun updateJobWithResponse(id: Id, updater: PrintJob.() -> Unit): Response

    fun postJob(job: PrintJob): Response

    fun getJobFile(id: Id, file: String): InputStream?

    fun getOldJobs(): List<PrintJob>
}

interface DbQueueLayer {

    fun getQueue(id: Id): PrintQueue

    fun getQueues(): List<PrintQueue>

    /*
       On Success: returns Response containing the collection of PrintQueues Added
     */
    fun putQueues(queues: Collection<PrintQueue>): Response

    fun deleteQueue(id: Id): String

    fun getEta(destinationId: Id): Long
}

interface DbMarqueeLayer {

    fun getMarquees(): List<MarqueeData>
}

interface DbSessionLayer {

    /*
   On Success: returns Response containing the Session added
    */
    fun putSession(session: FullSession): Response

    fun getSessionOrNull(id: Id): FullSession?

    fun getSessionIdsForUser(shortUser: ShortUser): List<Id>

    fun getAllSessions(): List<FullSession>

    fun deleteSession(id: Id): String
}

interface DbUserLayer {

    /*
   On Success: returns Response containing the User added
    */
    fun putUser(user: FullUser): Response

    fun getUserOrNull(sam: PersonalIdentifier): FullUser?

    fun isAdminConfigured(): Boolean

    fun getTotalPrintedCount(shortUser: ShortUser): Int
}
