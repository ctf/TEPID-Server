package ca.mcgill.science.tepid.server.util

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature
import org.glassfish.jersey.jackson.JacksonFeature
import shared.Config

import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.WebTarget

private fun initTarget(username: String, password: String, target: String)
        = ClientBuilder
        .newBuilder()
        .register(JacksonFeature::class.java)
        .register(HttpAuthenticationFeature.basic(username, password))
        .build()
        .target(target)

private fun initTarget(target: String)
        = ClientBuilder
        .newBuilder()
        .register(JacksonFeature::class.java)
        .build()
        .target(target)

val couchdb: WebTarget by lazy { initTarget(Config.COUCHDB_USERNAME, Config.COUCHDB_PASSWORD, Config.COUCHDB_URL) }
val temdb: WebTarget by lazy { initTarget(Config.TEM_URL) }
val barcodesdb: WebTarget by lazy { initTarget(Config.BARCODES_USERNAME, Config.BARCODES_PASSWORD, Config.BARCODES_URL) }