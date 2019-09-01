package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.mapper
import ca.mcgill.science.tepid.server.util.failBadRequest
import ca.mcgill.science.tepid.server.util.text
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import java.io.InputStream
import java.util.*
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

class CouchDbLayer : DbLayer {

    override fun getDestination(id: Id): FullDestination {
        return CouchDb.path(id).request(MediaType.APPLICATION_JSON).get(FullDestination::
        class.java)
    }

    override fun getDestinations(): List<FullDestination> =
            CouchDb.getViewRows("destinations")

    override fun putDestinations(destinations: Map<Id, FullDestination>): String =
            CouchDb.putArray("docs", destinations.values).postJson("_bulk_docs")


    override fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response =
            CouchDb.updateWithResponse(id, updater)

    override fun deleteDestination(id: Id): String =
            CouchDb.path(id).deleteRev()

    override fun getJob(id: Id): PrintJob =
            CouchDb.path(id).getJson()

    override fun getJobsByQueue(queue: String,
                                maxAge: Long,
                                sortOrder: Order,
                                limit: Int): List<PrintJob> {
        val from = if (maxAge > 0) Date().time - maxAge else 0 // assuming 0 is near beginning of time
        return CouchDb.getViewRows<PrintJob>("jobsByQueueAndTime") {
            // TODO verify that the descending keyword actually works.
            // In Screensaver, the list was being sorted again afterwards
            query("descending" to (sortOrder == Order.DESCENDING),
                    "startkey" to "[\"$queue\",%7B%7D]",
                    "endkey" to "[\"$queue\",$from]")
        }.sortAs(sortOrder)
    }

    override fun getJobsByUser(sam: Sam, sortOrder: Order): List<PrintJob> =
            CouchDb.getViewRows<PrintJob>("byUser") {
                query("key" to "\"$sam\"")
            }.sortAs(sortOrder)

    override fun getStoredJobs(): List<PrintJob> {
        return CouchDb.getViewRows<PrintJob>("storedJobs")
    }

    override fun updateJob(id: Id, updater: PrintJob.() -> Unit): PrintJob? =
            CouchDb.update(id, updater)

    override fun updateJobWithResponse(id: Id, updater: PrintJob.() -> Unit): Response {
        return CouchDb.updateWithResponse<PrintJob>(id, updater)
    }

    override fun postJob(job: PrintJob): Response =
            CouchDb.target.postJson(job)

    override fun getJobFile(id: Id, file: String): InputStream? =
            CouchDb.path(id, file).request().get()
                    .takeIf(Response::isSuccessful)
                    ?.readEntity(InputStream::class.java)

    override fun getOldJobs(): List<PrintJob> {
        return CouchDb.getViewRows<PrintJob>("oldJobs") {
            query("endkey" to System.currentTimeMillis() - 1800000)
        }
    }


    override fun getQueue(id: Id): PrintQueue {
        return CouchDb.path(id).request(MediaType.APPLICATION_JSON).get(PrintQueue::class.java)
    }

    override fun getQueues(): List<PrintQueue> =
            CouchDb.path(CouchDb.CouchDbView.Queues).getViewRows()

    override fun putQueues(queues: Collection<PrintQueue>): Response {
        val root = CouchDb.putArray("docs", queues)
        return CouchDb.path("_bulk_docs").postJson(root)
    }

    override fun deleteQueue(id: Id): String =
            CouchDb.path(id).deleteRev()

    override fun getEta(destinationId: Id): Long {
        return CouchDb
                .path("_design/main/_view")
                .path("maxEta")
                .request(MediaType.APPLICATION_JSON)
                .get(ObjectNode::class.java).get("rows").get(0).get("value").asLong(0)
    }

    override fun getMarquees(): List<MarqueeData> =
            CouchDb.getViewRows("_design/marquee/_view", "all")

    override fun putSession(session: FullSession): Response =
            CouchDb.path(session._id ?: failBadRequest("No id applied to session"))
                    .putJson(session)

    override fun getSessionOrNull(id: Id): FullSession? =
            CouchDb.path(id).getJsonOrNull()

    override fun getSessionIdsForUser(shortUser: ShortUser): List<String> {
        return CouchDb.getViewRows<String>("sessionsByUser") { query("key" to "\"$shortUser\"") }
    }

    override fun getAllSessions(): List<FullSession> {
        return CouchDb.getViewRows<FullSession>("sessions")
    }

    override fun deleteSession(id: Id): String =
            CouchDb.path(id).deleteRev()

    override fun putUser(user: FullUser): Response =
            CouchDb.path("u${user.shortUser}").putJson(user)

    private val numRegex = Regex("[0-9]+")

    override fun getUserOrNull(sam: Sam): FullUser? = when {
        sam.contains(".") ->
            CouchDb
                    .path(CouchDb.CouchDbView.ByLongUser)
                    .queryParam("key", "\"${sam.substringBefore("@")}%40${Config.ACCOUNT_DOMAIN}\"")
                    .getViewRows<FullUser>()
                    .firstOrNull()
        sam.matches(numRegex) ->
            CouchDb
                    .path(CouchDb.CouchDbView.ByStudentId)
                    .queryParam("key", sam)
                    .getViewRows<FullUser>()
                    .firstOrNull()
        else -> CouchDb.path("u$sam").getJsonOrNull()
    }

    override fun isAdminConfigured(): Boolean {
        val rows = CouchDb.path(CouchDb.MAIN_VIEW, "localAdmins").getObject().get("rows")
        return rows.size() > 0
    }

    override fun getTotalPrintedCount(shortUser: String): Int =
            CouchDb.path(CouchDb.MAIN_VIEW, "totalPrinted")
                    .query("key" to "\"$shortUser\"").getObject()
                    .get("rows")?.get(0)?.get("value")?.get("sum")?.asInt(0) ?: 0

}

object CouchDb : WithLogging() {

    const val MAIN_VIEW = "_design/main/_view"

    val target
        get() = couchdbOld

    /**
     * We have defined paths for views, this enum lists them out.
     * Now the IDE can check for valid methods
     */
    enum class CouchDbView(viewName: String) {
        ByLongUser("byLongUser"),
        ByStudentId("byStudentId"),
        Queues("queues");

        val path: String = "$MAIN_VIEW/$viewName"

    }

    /**
     * Create an [ArrayNode] from the given [data] at field [fieldName]
     */
    fun <T> putArray(fieldName: String, data: Collection<T>): ArrayNode {
        _log.trace("putting array at $fieldName: ${data.joinToString(" | ")}")
        return JsonNodeFactory.instance.objectNode()
                .putArray(fieldName)
                .addAll(mapper.convertValue<ArrayNode>(data))
    }

    fun path(vararg segment: String): WebTarget {
        var target = couchdbOld
        segment.forEach { target = target.path(it) }
        return target
    }

    fun path(couchDbView: CouchDbView): WebTarget {
        return couchdbOld.path(couchDbView.path)
    }

    /*
     * -------------------------------------------
     * View data retriever
     *
     * Given path, retrieve ViewResult variant
     * and return just the value of the "value" attribute of each row
     * -------------------------------------------
     */

    inline fun <reified T : Any> getViewRows(path: String): List<T> =
            getViewRows(MAIN_VIEW, path)

    inline fun <reified T : Any> getViewRows(path: String,
                                             targetConfig: WebTarget.() -> WebTarget): List<T> =
            getViewRows(MAIN_VIEW, path, targetConfig)

    inline fun <reified T : Any> getViewRows(base: String, path: String): List<T> =
            getViewRows(base, path, { this })

    inline fun <reified T : Any> getViewRows(base: String, path: String,
                                             targetConfig: WebTarget.() -> WebTarget): List<T> =
            path(base, path).targetConfig().getViewRows()

    /**
     * Helper for getting data at path [id], editing, then putting it back at the same path
     * If the put request is successful, the updated data will be returned
     * If anything went wrong, null will be returned
     */
    inline fun <reified T : Any> update(id: String, action: T.() -> Unit): T? {
        if (id.isBlank()) {
            log.error("Requested update for blank path for ${T::class.java.simpleName}")
            return null
        }
        try {
            val target = path(id)
            val data = target.getJson<T>()
            data.action()
            log.trace("Updating data at $id")
            val response = target.putJson(data)
            if (response.isSuccessful) {
                return data
            }
        } catch (e: Exception) {
        }
        return null
    }

    /**
     * Attempts to update the given target, and returns the response
     */
    inline fun <reified T : Any> updateWithResponse(id: String, action: T.() -> Unit): Response {
        if (id.isBlank()) {
            log.error("Requested update for blank path for ${T::class.java.simpleName}")
            return Response.Status.BAD_REQUEST.text("Empty path")
        }
        return try {
            val target = path(id)
            val data = target.getJson<T>()
            data.action()
            log.trace("Updating data at $id")
            target.putJson(data)
        } catch (e: Exception) {
            log.error("Update with response failed for ${T::class.java.simpleName}", e)
            Response.Status.BAD_REQUEST.text("${e::class.java.simpleName} occurred")
        }
    }

}
