package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.server.TestHelpers
import ca.mcgill.science.tepid.server.db.DB
import io.mockk.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.ws.rs.core.Response
import kotlin.test.assertEquals
import kotlin.test.assertFalse

val okPutResponse = Response.ok().entity(PutResponse(id = "TEST")).build()

class SessionIsValidTest {

    @Test
    fun testValidSelfInvalidation() {
        every { mockSession.isUnexpired() } returns false

        assertFalse(SessionManager.isValid(mockSession), "SessionaManger ignores a session's declaration of invalidity")
    }

    @Test
    fun testValidRoleInvalidation() {
        mockSession.role = "oldRole"

        every { mockSession.isUnexpired() } returns true

        assertFalse(
            SessionManager.isValid(mockSession),
            "SessionaManger ignores a mismatch between session permission and user permission"
        )
    }

    companion object {
        private val testUser = TestHelpers.makeDbUser().copy(role = "user")
        private val testSession = FullSession("user", testUser)
        val mockSession = spyk(testSession)

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(SessionManager)
            mockkObject(AuthenticationManager)
            every { AuthenticationManager.queryUserDb(testUser.shortUser!!) } returns testUser
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}

class SessionGetTest {

    @Test
    fun testGetInvalidSession() {
        every { DB.sessions.readOrNull("testID") } returns testSession
        every { DB.sessions.deleteById("testID") } just runs
        every { SessionManager.isValid(testSession) } returns false

        assertEquals(null, SessionManager.get("testID"), "Does not return null for invalid session")
    }

    @Test
    fun testGetValidSession() {
        every { DB.sessions.readOrNull("testID") } returns testSession
        every { DB.sessions.deleteById("testID") } just runs
        every { SessionManager.isValid(testSession) } returns true

        assertEquals(testSession, SessionManager.get("testID"), "Does not return valid session")
    }

    companion object {
        val testUser = TestHelpers.makeDbUser().copy(role = "user")
        val testSession = FullSession("user", testUser)

        @JvmStatic
        @BeforeAll
        fun initTest() {
            testSession._id = "testId"
            testSession._rev = "testRev"
            mockkObject(SessionManager)
            mockkObject(AuthenticationManager)
            every { AuthenticationManager.queryUserDb(testUser.shortUser!!) } returns testUser
            DB = TestHelpers.makeMockDb()
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}