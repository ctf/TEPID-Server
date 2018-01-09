package ca.mcgill.science.tepid.server.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature
import org.glassfish.jersey.jackson.JacksonFeature
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType

private fun initTarget(username: String, password: String, target: String)
        = ClientBuilder.newBuilder()
        .register(JacksonFeature::class.java)
        .register(HttpAuthenticationFeature.basic(username, password))
        .build()
        .target(target)

private fun initTarget(target: String)
        = ClientBuilder.newBuilder()
        .register(JacksonFeature::class.java)
        .build()
        .target(target)

val couchdbOld: WebTarget by lazy { initTarget(Config.COUCHDB_USERNAME, Config.COUCHDB_PASSWORD, Config.COUCHDB_URL) }
val temdb: WebTarget by lazy { initTarget(Config.TEM_URL) }
val barcodesdb: WebTarget by lazy { initTarget(Config.BARCODES_USERNAME, Config.BARCODES_PASSWORD, Config.BARCODES_URL) }

/*
 * -----------------------------------------
 * Extension functions
 * -----------------------------------------
 */

//fun WebTarget.requestJson() = request(MediaType.APPLICATION_JSON)!!
//inline fun <reified T> WebTarget.get() = requestJson().get(T::class.java)
//fun WebTarget.post(data: ObjectNode) = request().post(Entity.entity(data, MediaType.APPLICATION_JSON))

fun JsonNode.postJson(path: String): String {
    val result = couchdbOld.path(path).request(MediaType.TEXT_PLAIN)
            .post(Entity.entity(this, MediaType.APPLICATION_JSON))
            .readEntity(String::class.java)
    CouchDb.debug { "postJson: $result" }
    return result
}

//fun WebTarget.requestJson() = request(MediaType.APPLICATION_JSON)!!
//inline fun <reified T> WebTarget.get() = requestJson().get(T::class.java)!!

/**
 * Get json without class restrictions and read it as an [ObjectNode]
 */
fun WebTarget.readJsonObject() =
        request(MediaType.APPLICATION_JSON).get().readEntity(ObjectNode::class.java)

fun WebTarget.getRev() = readJsonObject().get("_rev").asText()

/**
 * Deletes revision code at the current web target
 */
fun WebTarget.deleteRev(): String {
    val rev = getRev()
    val result = queryParam("rev", rev).request(MediaType.TEXT_PLAIN).delete()
            .readEntity(String::class.java)
    CouchDb.debug { "deleteRev: $rev, $result" }
    return result
}

/**
 * Get json in the format of the supplied class
 */
inline fun <reified T : Any> WebTarget.getJson(): T {
    val data = getString()
    val result: T = mapper.readValue(data)
    CouchDb.debug { "getJson: $result" }
    return result
}

fun WebTarget.getObject() = request().get(ObjectNode::class.java)

fun WebTarget.getString() = request(MediaType.TEXT_PLAIN).get(String::class.java)


/**
 * Put [data] as a json to current target
 * TODO check if text_plain is suitable
 */
inline fun <reified T : Any> WebTarget.putJson(data: T) {
    request(MediaType.TEXT_PLAIN).put(Entity.entity(data, MediaType.APPLICATION_JSON))
}

fun <T : Any> WebTarget.postJson(data: T) =
        request(MediaType.TEXT_PLAIN).post(Entity.entity(data, MediaType.APPLICATION_JSON))
                .readEntity(String::class.java)

fun <T : Any> WebTarget.postJsonGetObj(data: T) =
        request(MediaType.APPLICATION_JSON).post(Entity.entity(data, MediaType.APPLICATION_JSON))
                .readEntity(ObjectNode::class.java)

fun WebTarget.query(vararg segment: Pair<String, Any>): WebTarget {
    var target = this
    segment.forEach { (key, value) -> target = target.queryParam(key, value) }
    return target
}