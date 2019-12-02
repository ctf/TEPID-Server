package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.ErrorResponse
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.TestHelpers
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.server.mapper
import ca.mcgill.science.tepid.server.util.TepidException
import ca.mcgill.science.tepid.server.util.getSession
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo
import kotlin.test.assertEquals

class UserTest : Logging {

    val endpoints: Users by lazy {
        Users()
    }

    var queryingUser: FullUser = TestHelpers.generateTestUser("querying")
    var targetUser: FullUser = TestHelpers.generateTestUser("target")

    lateinit var uriInfo: UriInfo
    lateinit var rc: ContainerRequestContext

    fun mockSession(role: String) {
        uriInfo = mockk<UriInfo>()
        every { uriInfo.getQueryParameters().containsKey("noRedirect") } returns true

        val session = FullSession(role, queryingUser)

        rc = mockk<ContainerRequestContext>()
        mockkStatic("ca.mcgill.science.tepid.server.util.UtilsKt")
        every {
            rc.getSession()
        } returns session
    }

    fun mockUserQuery(user: FullUser?) {
        mockkObject(SessionManager)
        mockkObject(AuthenticationManager)
        every {
            AuthenticationManager.queryUser("targetUser")
        } returns (user)
    }

    fun doTestUserQuery(role: String, queryResult: FullUser?, expected: FullUser?): Response {
        mockSession(role)
        mockUserQuery(queryResult)
        val result = endpoints.queryLdap("targetUser", rc, uriInfo)
        assertEquals(expected, result.entity)
        return result
    }

    fun doTestUserQuery403(role: String, queryResult: FullUser?) {
        mockSession(role)
        mockUserQuery(queryResult)
        val exception = assertThrows(TepidException::class.java) { endpoints.queryLdap("targetUser", rc, uriInfo) }
        assertEquals(403, exception.response.status)

        // This line makes sure that a 403 response doesn't also leak an attached user.
        // In case the user is added to the response before the response is marked as forbidden
        assertEquals("You cannot access this resource", mapper.readValue<ErrorResponse>(exception.response.entity as String).error)
    }

    @Test
    fun getUserBySamElderAndValidUser() {
        doTestUserQuery(ELDER, targetUser, targetUser)
    }

    @Test
    fun getUserBySamElderAndInvalidUser() {
        val exception = assertThrows(
            TepidException::class.java,
            { doTestUserQuery(ELDER, null, null) },
            "Did not throw 404 error when an Elder queried for a nonexistant user"
        )
        assertEquals(404, exception.response.status)
    }

    @Test
    fun getUserBySamCtferAndValidUser() {
        doTestUserQuery(CTFER, targetUser, targetUser)
    }

    @Test
    fun getUserBySamCtferAndInvalidUser() {
        val exception = assertThrows(
            TepidException::class.java,
            { doTestUserQuery(CTFER, null, null) },
            "Did not throw 404 error when a CTFer queried for a nonexistant user"
        )
        assertEquals(404, exception.response.status)
    }

    @Test
    fun getUserBySamUserAndInvalidUser() {
        doTestUserQuery403(USER, null)
    }

    @Test
    fun getUserBySamUserAndOtherUser() {
        doTestUserQuery403(USER, targetUser)
    }

    @Test
    fun getUserBySamUserAndSelfUser() {
        doTestUserQuery(USER, queryingUser, queryingUser)
    }

    /*
    I am aware that this is technically overkill; the AuthenticationFilter should already reject sessions without any role.
    That said, given how hard it is to get sessions right and given how bad it would be to leak user data, I've got these here.
     */
    @Test
    fun getUserBySamNoneAndValidUser() {
        doTestUserQuery403("", targetUser)
    }

    @Test
    fun getUserBySamNoneAndInvalidUser() {
        doTestUserQuery403("", null)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun initTest() {
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}