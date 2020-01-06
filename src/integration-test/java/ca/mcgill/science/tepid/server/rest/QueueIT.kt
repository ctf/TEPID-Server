package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.ITBase
import ca.mcgill.science.tepid.test.get
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class QueueIT : ITBase() {

    @Test
    fun testAddQueuesAndDestinations() {

        // adds the destinations
        val destinationResponses = testDestinations.values.map { server.testApi.putDestination(it._id!!, it).get() }
        destinationResponses.forEach { assertTrue { it.ok } }

        // adds the queues
        val queueResponses = testQueues.values.map {
            server.testApi
                .putQueue(it._id!!, it)
                .get()
        }
        queueResponses.forEach { assertTrue { it.ok } }
    }
}