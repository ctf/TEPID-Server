package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.printing.loadbalancers.FiftyFifty
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer
import io.mockk.impl.annotations.SpyK
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestQM(printQueue: PrintQueue, destinations: MutableList<FullDestination>) :
    QueueManager(printQueue, destinations) {
    override fun getEta(destination: String): Long = 0
}

abstract class LoadBalancerTest {

    // Should set queueManager.printQueue.loadBalancer to the LoadBalancer under test
    // Should mock getEta
    abstract fun createLB(queueManager: QueueManager): LoadBalancer

    // Should set printQueue.loadBalancer to the LoadBalancer under test
    abstract fun testQM(printQueue: PrintQueue, destinations: MutableList<FullDestination>): TestQM

    abstract fun validateMultiple(
        j0: LoadBalancer.LoadBalancerResults?,
        j1: LoadBalancer.LoadBalancerResults?,
        j2: LoadBalancer.LoadBalancerResults?
    )

    @Test
    fun testAssignNoDestinations() {
        val lb = createLB(testQM(PrintQueue(destinations = emptyList()), mutableListOf()))
        assertNull(lb.processJob(PrintJob()))
    }

    @Test
    fun testAssignSingle() {
        val lb = createLB(testQM(PrintQueue(destinations = listOf(d0._id!!)), destinations = mutableListOf(d0)))
        assertEquals(d0._id, lb.processJob(PrintJob())?.destination)
    }

    @Test
    fun testAssignMultiple() {
        val lb = createLB(
            testQM(
                PrintQueue(destinations = listOf(d0._id!!, d1._id!!)),
                destinations = mutableListOf(d0, d1)
            )
        )

        validateMultiple(
            lb.processJob(PrintJob()),
            lb.processJob(PrintJob()),
            lb.processJob(PrintJob())
        )
    }

    @Test
    fun testAssignAllDown() {
        val lb = createLB(
            testQM(
                PrintQueue(destinations = listOf(d0._id!!, d1._id!!)),
                destinations = mutableListOf(d0.copy(up = false), d1.copy(up = false))
            )
        )
        assertNull(lb.processJob(PrintJob()))
        assertNull(lb.processJob(PrintJob()))
        assertNull(lb.processJob(PrintJob()))
    }

    @Test
    fun testReassignDestinationValid() {
        val qm = testQM(PrintQueue(destinations = listOf(d0._id!!, d1._id!!)), destinations = mutableListOf(d0, d1))
        val lb = createLB(qm)

        val j0 = lb.processJob(PrintJob())
        assertNotNull(j0)
        val dest = qm.destinations.find { it._id == j0.destination } ?: fail("no destination found??")
        dest.up = false

        repeat(10) {
            val j = lb.processJob(PrintJob())
            assertNotNull(j)
            assertNotEquals(j.destination, dest._id)
        }
    }

    companion object {

        val d0 = FullDestination("D0", up = true)
        val d1 = FullDestination("D1", up = true)

        init {
            d0._id = "d0"
            d1._id = "d1"
        }
    }
}

class FiftyFiftyTest : LoadBalancerTest() {
    override fun createLB(queueManager: QueueManager): LoadBalancer {
        queueManager.printQueue.loadBalancer = FiftyFifty.name
        @SpyK
        val lb = FiftyFifty(queueManager)
        return lb
    }

    override fun testQM(printQueue: PrintQueue, destinations: MutableList<FullDestination>): TestQM {
        printQueue.loadBalancer = FiftyFifty.name
        return TestQM(printQueue, destinations)
    }

    override fun validateMultiple(
        j0: LoadBalancer.LoadBalancerResults?,
        j1: LoadBalancer.LoadBalancerResults?,
        j2: LoadBalancer.LoadBalancerResults?
    ) {
        assertNotNull(j0)
        assertNotNull(j1)
        assertNotNull(j2)

        assertTrue(j0.destination != j1.destination && j1.destination != j2.destination)
        assertEquals(j0.destination, j2.destination)
    }
}