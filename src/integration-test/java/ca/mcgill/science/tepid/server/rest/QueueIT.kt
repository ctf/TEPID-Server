package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.ITBase
import ca.mcgill.science.tepid.test.get
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class QueueIT : ITBase() {

    @Test
    fun testAddQueuesAndDestinations() {

        // adds the destinations
        val destinationResponses = testDestinations.map { server.testApi.putDestination(it._id!!, it).get() }
        destinationResponses.forEach { assertTrue { it.ok } }

        // adds the queues
        val queueResponses = testQueues.map {
            server.testApi
                .putQueue(it._id!!, it)
                .get()
        }
        queueResponses.forEach { assertTrue { it.ok } }
    }

    companion object {
        val d0 = "d0"
        val d1 = "d1"
        val testDestinations = {
            listOf(d0, d1).map { FullDestination(name = it, up = false) }.map { it.apply { _id = "mangle$name" } }
        }()

        // listOf(FullDestination(name = d0, up = false), FullDestination(name = d1, up = false))
        val testQueues = {
            listOf(
                PrintQueue(name = "q1", destinations = listOf(d0)),
                PrintQueue(name = "q2", destinations = listOf(d0, d1))
            ).map { it.apply { _id = "mangle$name" } }
        }()
    }
}