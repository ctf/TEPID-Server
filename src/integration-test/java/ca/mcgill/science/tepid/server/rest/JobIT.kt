package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.get
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Before
import org.junit.Test

class JobTest : ITBase(), Loggable by WithLogging() {

    lateinit var testJob: PrintJob

    @Before
    fun initTest() {
        Config.TEST_USER
        testJob = PrintJob(
                name= "Server Test ${System.currentTimeMillis()}",
                queueName = "1B16",
                userIdentification = server.testUser
        )
    }

    @Test
    fun testNewJob() {
        server.testApi.createNewJob(testJob).get()
    }

}