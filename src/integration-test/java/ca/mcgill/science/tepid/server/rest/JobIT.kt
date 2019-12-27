package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.api.addJobDataFromInput
import ca.mcgill.science.tepid.api.executeDirect
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.ITBase
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.TestUtils
import ca.mcgill.science.tepid.test.get
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class JobIT : ITBase() {

    lateinit var testJob: PrintJob

    @BeforeEach
    fun initTest() {
        Config.DEBUG

        server.testApi.enableColor(server.testUser, true).executeDirect()

        val d0 = "d0"
        val d1 = "d1"
        val testDestinations = {
            listOf(d0, d1).map { FullDestination(name = it, up = true) }.map { it.apply { _id = "mangle$name" } }
        }()
        val destinationResponses = testDestinations.map { server.testApi.putDestination(it._id!!, it).get() }
        destinationResponses.forEach { assertTrue { it.ok } }

        val q0 = "q0"

        val q = PrintQueue(loadBalancer = "fiftyfifty", name = "0", destinations = testDestinations.mapNotNull(FullDestination::_id))
        q._id = q0
        server.testApi.putQueue(q0, q).executeDirect()

        testJob = PrintJob(
            name = "Server Test ${System.currentTimeMillis()}",
            queueId = q0,
            userIdentification = server.testUser
        )
    }

    @Test
    fun testPrint() {
        val testFile = "pdf-test.pdf"

        val putJob = server.testApi.createNewJob(testJob).executeDirect()
        assertTrue(putJob!!.ok, "Could not put job")
        val jobId = putJob.id

        logger.debug("Sending job data for $jobId")

        // print once
        val fileInStream = FileInputStream(
            File(
                this::class.java.classLoader.getResource(testFile).file
            )
        )

        val response = server.testApi.addJobDataFromInput(
            jobId,
            FileInputStream(File(this::class.java.classLoader.getResource(testFile).file))
        ).executeDirect()
            ?: fail("null response received sending job contents")
        println("Job sent: $response")

        assertTrue(response.ok)
    }

    @Test
    fun testReprint() {
        val testFile = "pdf-test.pdf"

        val r = server.testApi.createNewJob(testJob).execute()
        val putJob = r.body()
        assertTrue(putJob!!.ok, "Could not put job")
        val jobId = putJob.id

        logger.debug("Sending job data for $jobId")

        // print once
        val response = server.testApi.addJobDataFromInput(
            jobId,
            FileInputStream(File(this::class.java.classLoader.getResource(testFile).file))
        )
            .executeDirect() ?: fail("null response received sending job contents")
        println("Job sent: $response")

        assertTrue(response.ok)

        // turn off original destination
        var printedJob: PrintJob? = null
        for (i in 0..10) {
            printedJob = server.testApi.getJob(jobId).executeDirect()
                ?: fail("did not retrieve printed job after print")
            if (printedJob.destination != null) break
            TimeUnit.MILLISECONDS.sleep(200)
        }
        val dest = printedJob?.destination ?: fail("printed job did not have destination")

        val setStatusResponse = TestUtils.testApi.setTicket(
            dest,
            DestinationTicket(up = false, reason = "reprint test, put me up")
        ).executeDirect() ?: fail("destination was not marked down")

        if (!setStatusResponse.ok) {
            fail("destination was not marked down")
        }

        // reprint
        val reprintResponse = server.testApi.reprintJob(jobId).execute().body()
            ?: fail("did not retrieve response after reprint")

        assertFalse(reprintResponse.contains("Failed"))

        val foundIds = server.GUID_REGEX.findAll(reprintResponse).map { f -> f.value }.toList()
        assertEquals(2, foundIds.size)
        assertEquals(jobId, foundIds[0])
        assertNotEquals(jobId, foundIds[1])
    }
}