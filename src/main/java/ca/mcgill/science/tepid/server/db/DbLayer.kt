package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import javax.ws.rs.core.Response

// TODO deleteDestination should return Response instead of String
// TODO, all outputs returning response should likely return models that can then be wrapped inside a response

/**
 * Ids are unique string keys in dbs
 */
typealias Id = String

/**
 * Abstraction layer for all db transactions
 * Inputs and outputs must be pure JVM models that are separated from db models
 */
interface DbLayer : DbDestinationLayer

interface DbDestinationLayer {

    fun getDestinations(): Map<Id, FullDestination>

    fun putDestinations(destinations: Map<Id, FullDestination>)

    fun updateDestination(id: Id, updater: FullDestination.() -> Unit): Response

    /**
     * Returns a string result representing a response entity
     */
    fun deleteDestination(id: Id): String
}