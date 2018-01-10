package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.bindings.TepidJackson
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.type.TypeReference

/**
 * Collection of rows containing values
 */
open class ViewResultSet<V : Any> : TepidJackson {
    open var rows: List<Row<V>> = emptyList()

    open class Row<out V>(open val value: V?) : TepidJackson {

        override fun hashCode() = value?.hashCode() ?: 7

        override fun equals(other: Any?) =
                if (other !is Row<*>) false else value == other.value

        override fun toString() = "Row[${value?.toString() ?: "null"}]"
    }

    fun getValues(): List<V> = rows.mapNotNull { it.value }

    override fun hashCode() = rows.hashCode()

    override fun equals(other: Any?) =
            if (other !is ViewResultSet<*>) false else rows == other.rows

    override fun toString() =
            "ViewResultSet[${rows.joinToString(", ")}]"
}

open class ViewResultSet2(var rows: List<String>) : TepidJackson

/**
 * Collection of rows containing keys and values
 *
 * TODO deprecate. We can write our own parser which doesn't use reflection
 */
open class ViewResultMap<K : Any, V : Any> : TepidJackson {
    open var rows: List<Row<K, V>> = emptyList()

    open class Row<out K, out V> @JsonCreator constructor(open val key: K?, open val value: V?) : TepidJackson {
        override fun hashCode() = (key?.hashCode() ?: 13) * 13 + (value?.hashCode() ?: 7)

        override fun equals(other: Any?) =
                if (other !is Row<*, *>) false else key == other.key && value == other.value

        override fun toString() = "($key, $value)"
    }

    fun getValues(): List<V> = rows.mapNotNull { it.value }

    override fun hashCode() = rows.hashCode()

    override fun equals(other: Any?) =
            if (other !is ViewResultMap<*, *>) false else rows == other.rows

    override fun toString() =
            "ViewResultMap[${rows.joinToString(", ")}]"
}