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

        val l = server.testApi
                .putDestinations(mapOf("d1" to testDestinations[0], "d2" to testDestinations[1]))
                .get()

        assertFalse { l.isEmpty() }

        for (putResponse in l) {
            assertTrue { putResponse.ok }
        }
    }


    companion object {
        val testDestinations = listOf(FullDestination(name= "d1", up = false), FullDestination(name="d2", up = false))
        val testQueues = listOf(PrintQueue(name="q1"), PrintQueue(name="q2"))
    }

}