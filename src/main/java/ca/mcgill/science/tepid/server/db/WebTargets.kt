package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.mapper
import com.fasterxml.jackson.databind.JsonNode
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
 * Attempt to retrieve the "_rev" attribute from the given target
 * Returns an empty string if nothing is found
 */
fun WebTarget.getRev(): String = getObject().get("_rev")?.asText() ?: ""

/**
 * Get json in the format of the supplied class
 */
inline fun <reified T : Any> WebTarget.getJson(): T =
        mapper.readValue(getString())

fun <T> WebTarget.getJson(classParameter: Class<T>): T =
        mapper.readValue<T>(getString(), classParameter)

/**
 * Call [getJson] but with an exception check
 * Note that this is expensive, and should only be used if the target
 * is expected to be potentially invalid
 */
inline fun <reified T : Any> WebTarget.getJsonOrNull(): T? = try {
    getJson(T::class.java)
} catch (e: Exception) {
    null
}

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
 * Post
 * -----------------------------------------
 */

fun JsonNode.postJson(path: String): String =
        CouchDb.path(path)
                .request(MediaType.TEXT_PLAIN)
                .post(Entity.entity(this, MediaType.APPLICATION_JSON))
                .readEntity(String::class.java)

/**
 * Submit a post request at the current target with the supplied [data]
 * and retrieve the result as a [String]
 */
fun <T : Any> WebTarget.postJson(data: T): Response =
        request(MediaType.TEXT_PLAIN)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON))


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

/**
 * Deletes revision code at the current web target
 */
fun WebTarget.deleteRev(): String {
    val rev = getRev()
    val result = queryParam("rev", rev)
            .request(MediaType.TEXT_PLAIN).delete()
            .readEntity(String::class.java)
    _log.trace("deleteRev: $rev, $result")
    return result
}

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