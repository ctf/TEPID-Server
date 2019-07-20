package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.api.addJobDataFromInput
import ca.mcgill.science.tepid.api.executeDirect
import ca.mcgill.science.tepid.models.data.DestinationTicket
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.TestUtils
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.test.*

class JobTest : ITBase(), Loggable by WithLogging() {

    lateinit var testJob: PrintJob

    @BeforeEach
    fun initTest() {
        Config.DEBUG

        server.testApi.enableColor(server.testUser, true).executeDirect()


        server.testApi.putDestinations(mapOf("d0" to FullDestination(name = "d0", up = true), "d1" to FullDestination(name = "d1", up = true))).executeDirect()

        val q = PrintQueue(loadBalancer = "fiftyfifty", name = "q0", destinations = listOf("d0", "d1"))
        q._id = "q0"
        server.testApi.putQueues(listOf(q)).executeDirect()

        testJob = PrintJob(
                name = "Server Test ${System.currentTimeMillis()}",
                queueName = "0",
                userIdentification = server.testUser
        )
    }

    @Test
    fun testPrint() {
        val testFile = "pdf-test.pdf"

        val putJob = server.testApi.createNewJob(testJob).executeDirect()
        assertTrue(putJob!!.ok, "Could not put job")
        val jobId = putJob.id

        log.debug("Sending job data for $jobId")

        // print once
        val fileInStream = FileInputStream(File(
                this::class.java.classLoader.getResource(testFile).file
        ))

        val response = server.testApi.addJobDataFromInput(jobId, FileInputStream(File(this::class.java.classLoader.getResource(testFile).file))).executeDirect()
                ?: fail("null response received sending job contents")
        println("Job sent: $response")

        assertTrue(response.ok)

    }

    @Test
    fun testReprint() {
        val testFile = "pdf-test.pdf"

        val putJob = server.testApi.createNewJob(testJob).executeDirect()
        assertTrue(putJob!!.ok, "Could not put job")
        val jobId = putJob.id

        log.debug("Sending job data for $jobId")

        // print once
        val response = server.testApi.addJobDataFromInput(jobId, FileInputStream(File(this::class.java.classLoader.getResource(testFile).file)))
                .executeDirect() ?: fail("null response received sending job contents")
        println("Job sent: $response")

        assertTrue(response.ok)

        // turn off original destination
        TimeUnit.MILLISECONDS.sleep(1000)

        val printedJob = server.testApi.getJob(jobId).executeDirect()
                ?: fail("did not retrieve printed job after print")

        val setStatusResponse = TestUtils.testApi.setPrinterStatus(printedJob.destination
                ?: fail("printed job did not have destination"), DestinationTicket(up = false, reason = "reprint test, put me up")).execute()

        if (setStatusResponse?.body()?.contains("down") != true) {
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