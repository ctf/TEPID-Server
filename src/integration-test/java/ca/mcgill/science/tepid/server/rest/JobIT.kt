package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.ws.rs.container.ContainerRequestContext
import kotlin.test.fail

class JobTest : WithLogging() {

    val endpoints: Jobs by lazy {
        Jobs()
    }

    lateinit var testUser: FullUser

    @MockK
    lateinit var mockUserCtx: ContainerRequestContext

    @Before
    fun initTest() {
        testUser = SessionManager.queryUser(Config.TEST_USER, null) ?: fail("Test User with SAM ${Config.TEST_USER} not found")

        every {
            mockUserCtx.getSession()
        } returns FullSession(user=testUser, role = USER)
    }

    @After
    fun tearTest() {
        unmockkAll()
    }

    val testJob = PrintJob(
            name= "TEST",
            queueName = DB.getQueues().first().name,
            userIdentification = testUser.shortUser
    )

    @Test
    fun testNewJob() {
        endpoints.newJob(testJob, mockUserCtx)
    }

}