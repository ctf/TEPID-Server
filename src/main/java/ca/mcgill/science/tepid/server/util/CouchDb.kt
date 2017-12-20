package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.ViewResultSet
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import javax.ws.rs.client.WebTarget

object CouchDb : WithLogging() {

    const val MAIN_VIEW = "_design/main/_view"

    val logging = false

    fun debug(message: () -> String) {
        if (CouchDb.logging)
            CouchDb.log.debug("CouchDb: ${message()}")
    }

    val target
        get() = couchdbOld

    /**
     * Create an [ArrayNode] from the given [data] at field [fieldName]
     */
    fun <T> putArray(fieldName: String, data: Collection<T>): ArrayNode {
        debug { "putting array at $fieldName: ${data.joinToString(" | ")}" }
        return JsonNodeFactory.instance.objectNode()
                .putArray(fieldName)
                .addAll(ObjectMapper().convertValue(data, ArrayNode::class.java))
    }

    fun path(vararg segment: String): WebTarget {
        var target = couchdbOld
        segment.forEach { target = target.path(it) }
        return target
    }

    /*
     * -------------------------------------------
     * View data retriever
     *
     * Given path, retrieve ViewResult variant
     * and return just the row values
     * -------------------------------------------
     */

    inline fun <reified T : Any> getViewRows(path: String): List<T> =
            getViewRows(MAIN_VIEW, path)

    inline fun <reified T : Any> getViewRows(path: String,
                                             targetConfig: WebTarget.() -> Unit): List<T> =
            getViewRows(MAIN_VIEW, path, targetConfig)

    inline fun <reified T : Any> getViewRows(base: String, path: String): List<T> =
            getViewRows(base, path, {})

    inline fun <reified T : Any> getViewRows(base: String, path: String,
                                             targetConfig: WebTarget.() -> Unit) =
            CouchDb.path(base, path).apply { targetConfig() }
                    .getJson<ViewResultSet<T>>()
                    .getValues()

    inline fun <reified T> jsonFromId(id: String) = path(id).getJson<T>()

    /**
     * Helper for getting data at path [id], editing, then putting it back at the same path
     */
    inline fun <reified T> update(id: String, action: T.() -> Unit): T? {
        val target = path(id)
        val data = target.getJson<T>() ?: return null
        data.action()
        debug { "updated id $id" }
        target.putJson(data)
        return data
    }

    inline fun <reified T> tryUpdate(id: String, action: T.() -> Unit) = try {
        update(id, action)
        true
    } catch (e: Exception) {
        false
    }

}
