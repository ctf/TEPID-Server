package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.test.get
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueIT : ITBase(), Loggable by WithLogging() {

    @Test
    fun testAddQueuesAndDestinations() {

        // adds the destinations

        val destinationResponses = server.testApi
            .putDestinations(mapOf(d0 to testDestinations[0], d1 to testDestinations[1]))
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
        // ref tepid-commons#12 for why we're not checking further
    }

    companion object {
        val d0 = "d0".padEnd(36)
        val d1 = "d1".padEnd(36)
        val testDestinations = listOf(FullDestination(name = d0, up = false), FullDestination(name = d1, up = false))
        val testQueues = {
            val l = listOf(
                PrintQueue(name = "q1", destinations = listOf(d0)),
                PrintQueue(name = "q2", destinations = listOf(d0, d1))
            ); l[0]._id = "q1"; l[1]._id = "q2"; l
        }()
    }
}