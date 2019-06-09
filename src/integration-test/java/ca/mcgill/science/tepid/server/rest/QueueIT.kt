package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.test.get
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueTest : ITBase(), Loggable by WithLogging() {

    @Test
    fun testAddQueuesAndDestinations(){

        // adds the destinations

        val destinationResponses = server.testApi
                .putDestinations(mapOf("d1" to testDestinations[0], "d2" to testDestinations[1]))
                .get()

        assertFalse { destinationResponses.isEmpty() }

        for (putResponse in destinationResponses) {
            assertTrue { putResponse.ok }
        }

        // adds the queues
        val queueResponses = server.testApi
                .putQueues(testQueues)
                .get()

        assertFalse { queueResponses.isEmpty() }

        for (putResponse in queueResponses) {
            assertTrue { putResponse.ok }
        }
    }


    companion object {
        val testDestinations = listOf(FullDestination(name= "d1", up = false), FullDestination(name="d2", up = false))
        val testQueues = listOf(PrintQueue(name="q1", destinations = listOf("d1")), PrintQueue(name="q2", destinations = listOf("d1", "d2")))
    }

}