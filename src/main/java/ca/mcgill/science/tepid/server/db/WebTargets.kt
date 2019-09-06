package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.mapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.logging.log4j.LogManager
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature
import org.glassfish.jersey.jackson.JacksonFeature
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo

val _log = LogManager.getLogger("WebTargets")

private fun initTarget(username: String, password: String, target: String): WebTarget {
    if (target.isBlank())
        _log.error("Requested a target with an empty string")
    else {
        if (username.isBlank() || password.isBlank())
            _log.error("Requested authenticated target $target with a blank username or password")
    }
    return ClientBuilder.newBuilder()
            .register(JacksonFeature::class.java)
            .register(HttpAuthenticationFeature.basic(username, password))
            .build()
            .target(target)
}

private fun initTarget(target: String): WebTarget {
    if (target.isBlank())
        _log.error("Requested a target with an empty string")
    return ClientBuilder.newBuilder()
            .register(JacksonFeature::class.java)
            .build()
            .target(target)
}

//todo hide
val couchdbOld: WebTarget by lazy { initTarget(Config.DB_USERNAME, Config.DB_PASSWORD, Config.DB_URL) }
val temdb: WebTarget by lazy { initTarget(Config.TEM_URL) }
val barcodesdb: WebTarget by lazy { initTarget(Config.BARCODES_USERNAME, Config.BARCODES_PASSWORD, Config.BARCODES_URL) }

/*
 * -----------------------------------------
 * Extension functions
 * -----------------------------------------
 */

/**
 * Alternative to [WebTarget.queryParam], allowing you to pass
 * everything as pairs directly
 */
fun WebTarget.query(vararg segment: Pair<String, Any>): WebTarget {
    var target = this
    segment.forEach { (key, value) -> target = target.queryParam(key, value) }
    return target
}

/*
 * -----------------------------------------
 * Get
 * -----------------------------------------
 */

/**
 * Get the current WebTarget without class restrictions and read it as an [ObjectNode]
 */
fun WebTarget.getObject(): ObjectNode = request().get(ObjectNode::class.java)

/**
 * Get the current WebTarget without parsing as a [String]
 */
fun WebTarget.getString(): String = request(MediaType.TEXT_PLAIN).get(String::class.java)

/**
 * Get json in the format of the supplied class
 */
inline fun <reified T : Any> WebTarget.getJson(): T =
        mapper.readValue(getString())

/**
 * Retrieve a list of the defined class from the WebTarget.
 * The layout matches that of a CouchDb row,
 * with a "rows" attribute containing a map of data to "value"
 */
inline fun <reified T : Any> WebTarget.getViewRows(): List<T> {
    return getViewRows(T::class.java)
}

fun <T> WebTarget.getViewRows(classParameter : Class<T>): List<T> {
    val rows = getObject().get("rows") ?: return emptyList<T>()
    return rows.mapNotNull { it?.get("value") }.map { mapper.treeToValue(it, classParameter) }
}

/*
 * -----------------------------------------
 * Put
 * -----------------------------------------
 */

/**
 * Put [data] as a json to current target
 * Returns the response sent back from couch
 */
inline fun <reified T : Any> WebTarget.putJson(data: T): Response =
        request(MediaType.APPLICATION_JSON)
                .put(Entity.entity(data, MediaType.APPLICATION_JSON))

/*
 * -----------------------------------------
 * Delete
 * -----------------------------------------
 */

val Response.isSuccessful: Boolean
    get() = status in 200 until 300

fun WebTarget.query(uriInfo: UriInfo, vararg queries: String): WebTarget {
    val params = uriInfo.queryParameters
    if (params.isEmpty() || queries.isEmpty()) return this
    var target = this
    queries.filter { params.contains(it) }.forEach {
        target = target.queryParam(it, params.getFirst(it))
    }
    return target
}