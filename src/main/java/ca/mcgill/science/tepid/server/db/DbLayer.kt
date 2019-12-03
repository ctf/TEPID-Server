package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.Semester
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

interface ICrud<T, P> {
    val classParameter: Class<T>

    fun create(obj: T): T

    fun readOrNull(id: P): T?

    fun read(id: P): T

    fun readAll(): List<T>

    fun update(obj: T): T

    fun update(id: P, updater: T.() -> Unit): T

    fun delete(obj: T)

    fun deleteById(id: P)

    fun put(obj: T): T
}

/**
 * Abstraction layer for all db transactions.
 * Inputs and outputs must be pure JVM models that are separated from db models.
 * Note that layers are categorized based on particular models to help with delegation patterns.
 * Each method in each of the included interfaces should still be unique with respect to all other methods.
 */
class DbLayer(
    @JvmField
    val destinations: DbDestinationLayer,
    @JvmField
    val printJobs: DbJobLayer,
    @JvmField
    val queues: DbQueueLayer,
    @JvmField
    val marquee: DbMarqueeLayer,
    @JvmField
    val sessions: DbSessionLayer,
    @JvmField
    val users: DbUserLayer,
    @JvmField
    val quota: DbQuotaLayer
) {
    fun <T : Comparable<T>> List<T>.sortAs(order: Order): List<T> = order.sort(this)
}

interface DbDestinationLayer : ICrud<FullDestination, Id?> {

    fun putDestinations(destinations: Map<Id, FullDestination>): String

    fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response
}

interface DbJobLayer : ICrud<PrintJob, Id?> {

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

    fun getJobsByUser(su: ShortUser, sortOrder: Order = Order.DESCENDING): List<PrintJob>

    /**
     * Gets jobs which have a value for their file, implying the contents are still stored in TEPID
     */
    fun getStoredJobs(): List<PrintJob>

    fun updateJobWithResponse(id: Id, updater: PrintJob.() -> Unit): Response

    fun postJob(job: PrintJob): Response

    fun getJobFile(id: Id, file: String): InputStream?

    fun getOldJobs(): List<PrintJob>
}

interface DbQueueLayer : ICrud<PrintQueue, Id?> {

    fun getQueue(id: Id): PrintQueue

    /*
       On Success: returns Response containing the collection of PrintQueues Added
     */
    fun putQueues(queues: Collection<PrintQueue>): Response

    fun deleteQueue(id: Id): String

    fun getEta(destinationId: Id): Long
}

interface DbMarqueeLayer : ICrud<MarqueeData, Id?> {
    fun getMarquees(): List<MarqueeData>
}

interface DbSessionLayer : ICrud<FullSession, Id?> {

    /*
   On Success: returns Response containing the Session added
    */
    fun putSession(session: FullSession): Response

    fun getSessionOrNull(id: Id): FullSession?

    fun getSessionIdsForUser(shortUser: ShortUser): List<Id>

    fun getAllSessions(): List<FullSession>

    fun deleteSession(id: Id): String
}

interface DbUserLayer : ICrud<FullUser, Id?> {

    /*
   On Success: returns Response containing the User added
    */
    fun putUser(user: FullUser): Response

    fun putUsers(users: Collection<FullUser>): Unit

    fun getAllIfPresent(ids: Set<String>): Set<FullUser>

    fun getUserOrNull(sam: PersonalIdentifier): FullUser?

    fun isAdminConfigured(): Boolean
}

interface DbQuotaLayer {
    fun getTotalPrintedCount(shortUser: ShortUser): Int

    fun getAlreadyGrantedUsers(ids: Set<String>, semester: Semester): Set<String>
}
