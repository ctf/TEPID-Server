package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.convertValue
import javax.ws.rs.client.WebTarget

object CouchDb : WithLogging() {

    const val MAIN_VIEW = "_design/main/_view"

    val target
        get() = couchdbOld

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
                                             targetConfig: WebTarget.() -> WebTarget): List<T> =
            getViewRows(MAIN_VIEW, path, targetConfig)

    inline fun <reified T : Any> getViewRows(base: String, path: String): List<T> =
            getViewRows(base, path, { this })

    inline fun <reified T : Any> getViewRows(base: String, path: String,
                                             targetConfig: WebTarget.() -> WebTarget): List<T> =
            path(base, path).targetConfig().getViewRows()

    inline fun <reified T : Any> jsonFromId(id: String) = path(id).getJson<T>()

    /**
     * Helper for getting data at path [id], editing, then putting it back at the same path
     */
    inline fun <reified T : Any> update(id: String, action: T.() -> Unit): T {
        val target = path(id)
        val data = target.getJson<T>()
        data.action()
        log.trace("updated id $id")
        target.putJson(data)
        return data
    }

    inline fun <reified T : Any> tryUpdate(id: String, action: T.() -> Unit) = try {
        update(id, action)
        true
    } catch (e: Exception) {
        false
    }

}
