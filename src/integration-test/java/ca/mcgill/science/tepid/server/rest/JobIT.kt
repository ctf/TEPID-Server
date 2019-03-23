package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.SessionRequest
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.ws.rs.container.ContainerRequestContext
import kotlin.test.fail

@ExtendWith(MockKExtension::class)
class JobTest : WithLogging() {

    val endpoints: Jobs by lazy {
        Jobs()
    }
    val Sessions by lazy {
        Sessions()
    }

    lateinit var testUser: FullUser
    lateinit var testJob: PrintJob

    @MockK
    lateinit var mockUserCtx: ContainerRequestContext

    @Before
    fun initTest() {

        MockKAnnotations.init(this)

        Config.TEST_USER;

        testUser = SessionManager.queryUser(Config.TEST_USER, null) ?: fail("Test User with SAM ${Config.TEST_USER} not found")

        Assume.assumeTrue(Config.LDAP_ENABLED)
        Assume.assumeTrue(Config.TEST_USER.isNotEmpty())
        Assume.assumeTrue(Config.TEST_PASSWORD.isNotEmpty())
        println("Running ldap tests with test user")

        val testSession = Sessions.startSession(SessionRequest(Config.TEST_USER, Config.TEST_PASSWORD, false, false))
        val testFullSession = DB.getSessionOrNull(testSession._id!!) ?: fail("session could not be created. {" +
                "  \"shortUser\": {$Config.TEST_USER}," +
                "  \"sessionId\": {$testSession._id}" +
                "}")

        every {
            mockUserCtx.getSession()
        } returns testFullSession

        testJob = PrintJob(
                name= "TEST",
                queueName = DB.getQueues().first().name,
                userIdentification = testUser.shortUser
        )
    }

    @After
    fun tearTest() {
        unmockkAll()
    }

    @Test
    fun testNewJob() {
        endpoints.newJob(testJob, mockUserCtx)
    }

}