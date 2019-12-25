package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.text
import java.io.InputStream
import javax.persistence.EntityExistsException
import javax.persistence.EntityNotFoundException
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response

var DB: DbLayer = Config.getDb()

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

    fun create(obj: T): PutResponse

    fun readOrNull(id: P): T?

    fun read(id: P): T

    fun readAll(): List<T>

    fun update(obj: T): T

    fun update(id: P, updater: T.() -> Unit): T

    fun delete(obj: T)

    fun deleteById(id: P)

    fun put(obj: T): PutResponse
}

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

interface DbDestinationLayer : ICrud<FullDestination, Id?>

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

    fun getJobFile(id: Id, file: String): InputStream?

    fun getOldJobs(): List<PrintJob>
}

interface DbQueueLayer : ICrud<PrintQueue, Id?> {

    fun getEta(destinationId: Id): Long
}

interface DbMarqueeLayer : ICrud<MarqueeData, Id?>

interface DbSessionLayer : ICrud<FullSession, Id?> {
    fun getSessionIdsForUser(id: Id): List<Id>
}

interface DbUserLayer : ICrud<FullUser, Id?> {

    fun putUsers(users: Collection<FullUser>): Unit

    fun getAllIfPresent(ids: Set<String>): Set<FullUser>

    fun find(sam: PersonalIdentifier): FullUser?
}

interface DbQuotaLayer {
    fun getTotalPrintedCount(id: Id): Int

    fun getAlreadyGrantedUsers(ids: Set<String>, semester: Semester): Set<String>
}

fun <T> remapExceptions(f: () -> T): T {
    try {
        return f()
    } catch (e: Exception) {
        throw WebApplicationException(
            when (e) {
                is EntityNotFoundException -> Response.Status.NOT_FOUND.text("Not found")
                is IllegalArgumentException -> Response.Status.BAD_REQUEST.text("${e::class.java.simpleName} occurred")
                is EntityExistsException -> Response.Status.CONFLICT.text("Entity Exists; ${e::class.java.simpleName} occurred")
                else -> Response.Status.INTERNAL_SERVER_ERROR.text("Ouch! ${e::class.java.simpleName} occurred")
            }
        )
    }
}