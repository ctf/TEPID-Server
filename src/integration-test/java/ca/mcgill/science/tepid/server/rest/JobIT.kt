package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.api.addJobDataFromInput
import ca.mcgill.science.tepid.api.executeDirect
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.ITBase
import ca.mcgill.science.tepid.test.TestUtils
import ca.mcgill.science.tepid.test.get
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class JobIT : ITBase() {

    lateinit var testJob: PrintJob

    @BeforeEach
    fun initTest() {
        createTestQueues()
        server.testApi.enableColor(server.testUser, true).get()

        testJob = PrintJob(
            name = "Server Test ${System.currentTimeMillis()}",
            queueId = q1,
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
        val refundResponse = server.testApi.refundJob(response.id, true).get()
        println("Job refunded")

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

        var refundResponse = server.testApi.refundJob(response.id, true).get()
        println("Job refunded")

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
        ).get()

        if (!setStatusResponse.ok) {
            fail("destination was not marked down")
        }

        // reprint
        val reprintResponse = server.testApi.reprintJob(jobId).execute().body()
            ?: fail("did not retrieve response after reprint")

        assertTrue(reprintResponse.ok)
        assertNotEquals(jobId, reprintResponse.id)

        refundResponse = server.testApi.refundJob(response.id, true).get()
        println("Job refunded")
    }
}