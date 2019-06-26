package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.api.executeDirect
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.TestUtils
import ca.mcgill.science.tepid.test.get
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Before
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import kotlin.test.assertTrue
import kotlin.test.fail

class JobTest : ITBase(), Loggable by WithLogging() {

    lateinit var testJob: PrintJob

    @Before
    fun initTest() {
        Config.DEBUG
        testJob = PrintJob(
                name= "Server Test ${System.currentTimeMillis()}",
                queueName = "1B16",
                userIdentification = server.testUser
        )
    }

    @Test
    fun test() {
        val testFile = "pdf-test.pdf"
        val job = PrintJob(name = server.testUser,
                queueName = "1B16",
                originalHost = "Unit Test")

        val user = server.testApi.getUser(server.testUser).get()


        val putJob = server.testApi.createNewJob(job).executeDirect()

        assertTrue(putJob!!.ok, "Could not put job")

        val jobId = putJob.id

        log.debug("Sending job data for $jobId")

        val fileInStream = FileInputStream(File(
                this::class.java.classLoader.getResource(testFile).file
        ))

        val o = ByteArrayOutputStream()
        val xzStream = XZOutputStream(o, LZMA2Options())



        xzStream.write(fileInStream.readAllBytes())

        val i = o.toByteArray()

        val response = TestUtils.testApi.addJobData(jobId, i).executeDirect() ?: fail("null response received sending job contents")
        println("Job sent: $response")

        assertTrue(response.ok)

    }

}