package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.test.get
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.junit5.MockKExtension
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JobTest : ITBase(), Loggable by WithLogging() {

    lateinit var testJob: PrintJob

    @Before
    fun initTest() {
        testJob = PrintJob(
                name= "Server Test ${System.currentTimeMillis()}",
                queueName = DB.getQueues().first().name,
                userIdentification = testUser
        )
    }

    @After
    fun tearTest() {
        unmockkAll()
    }

    @Test
    fun testNewJob() {
        testApi.createNewJob(PrintJob(name="Server Test ${System.currentTimeMillis()}")).get()

    }

}