package ca.mcgill.science.tepid.server.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Where the magic happens
 * Provides class bindings for
 * serialization & deserialization
 */
val mapper = jacksonObjectMapper()