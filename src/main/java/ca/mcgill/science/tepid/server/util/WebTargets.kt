package ca.mcgill.science.tepid.server.util

import com.fasterxml.jackson.databind.node.ObjectNode
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

val couchdb: WebTarget by lazy { initTarget(Config.COUCHDB_USERNAME, Config.COUCHDB_PASSWORD, Config.COUCHDB_URL) }
val temdb: WebTarget by lazy { initTarget(Config.TEM_URL) }
val barcodesdb: WebTarget by lazy { initTarget(Config.BARCODES_USERNAME, Config.BARCODES_PASSWORD, Config.BARCODES_URL) }

/*
 * -----------------------------------------
 * Extension functions
 * -----------------------------------------
 */

fun WebTarget.requestJson() = request(MediaType.APPLICATION_JSON)!!
inline fun <reified T> WebTarget.get() = requestJson().get(T::class.java)
fun WebTarget.post(data: ObjectNode) = request().post(Entity.entity(data, MediaType.APPLICATION_JSON))
