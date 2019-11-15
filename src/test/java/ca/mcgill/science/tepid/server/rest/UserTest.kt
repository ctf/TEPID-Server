package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.ErrorResponse
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
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
import org.junit.jupiter.api.TestInstance
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestUserGetQuota : Logging {

    val s2019f = Semester(Season.FALL, 2019)
    val s2018s = Semester(Season.SUMMER, 2018)
    val s1066f = Semester(Season.FALL, 1066)
    val s2016f = Semester(Season.FALL, 2016)
    val s2018f = Semester(Season.FALL, 2018)
    val s2018w = Semester(Season.WINTER, 2018)
    val s2018w0 = Semester(Season.WINTER, 2018)

    /**
     * Runs a test of Users.getQuota, Mockking [tailoredUser] as the user returned by AuthenticationManager
     */
    private fun userGetQuotaTest(tailoredUser: FullUser, expected: Int, message: String) {
        mockUser(tailoredUser)
        val actual = Users.getQuotaData(tailoredUser).quota
        assertEquals(expected, actual, message)
    }

    private fun userGetQuotaTest(tailoredUser: FullUser, tailoredUserRole: String, expected: Int, message: String) {
        every {
            AuthenticationFilter.getCtfRole(ofType(FullUser::class))
        } returns tailoredUserRole
        userGetQuotaTest(tailoredUser.copy(shortUser = "tailoredUser"), expected, message)
    }

    private fun mockUser(tailoredUser: FullUser?) {
        every {
            AuthenticationManager.queryUser("targetUser")
        } returns (tailoredUser)
    }

    private fun setPrintedPages(printedPages: Int) {
        every {
            Users.getTotalPrinted(ofType(String::class))
        } returns printedPages
    }

    @Test
    fun testGetQuotaQueriedUserNoRole() {
        userGetQuotaTest(FullUser(), "", 0, "Null user is not assigned 0 quota")
    }

    @Test
    fun testGetQuotaElder() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = ELDER, semesters = setOf(s2019f)),
            ELDER,
            250,
            "Elder is not given correct quota"
        )
    }

    @Test
    fun testGetQuotaCTFer() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = CTFER, semesters = setOf(s2019f)),
            CTFER,
            250,
            "CTFER is not given correct quota"
        )
    }

    @Test
    fun testGetQuotaNUS() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2019f), groups = setOf(AdGroup("520-NUS Users"))),
            CTFER,
            1000,
            "NUS is not given correct quota"
        )
    }

    @Test
    fun testGetQuotaUserIgnoreSummerSemester() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role = USER, semesters = setOf(s2018s)), USER, 0, "Summer gives quota")
    }

    @Test
    fun testGetQuotaUserSemesterPre2016() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role = USER, semesters = setOf(s1066f)), USER, 0, "Ancient semester gives quota")
    }

    @Test
    fun testGetQuotaUserSemester2016F() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role = USER, semesters = setOf(s2016f)), USER, 500, "500 pages not given for 2016F")
    }

    @Test
    fun testGetQuotaUserSemesterPost2016F() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018f)),
            USER,
            1000,
            "1000 pages not given for semester"
        )
    }

    @Test
    fun testGetQuotaUserSemesterPost2019F() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2019f)),
            USER,
            250,
            "250 pages not given for semester post 2019f"
        )
    }

    @Test
    fun testGetQuotaUserSpanMultipleSemesters() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018f, s2018w)),
            USER,
            2000,
            "multiple semesters not counted"
        )
    }

    @Test
    fun testGetQuotaTotalPrintedSubtracted() {
        setPrintedPages(300)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018f)),
            USER,
            700,
            "Printed pages not subtracted (you had one job)"
        )
    }

    // Tests that if there are multiple courses in the same semester they only contribute as one semester
    @Test
    fun testGetQuotaMultipleCoursesReduced() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018w0, s2018w)),
            USER,
            1000,
            "multiple courses in same semester counted as other semesters"
        )
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(AuthenticationManager)
            mockkObject(AuthenticationFilter)
            mockkObject(Users)
            every {
                AuthenticationManager.queryUser("targetUser")
            } returns (FullUser())
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}

class getUserBySamTest : Logging {

    val endpoints: Users by lazy {
        Users()
    }

    var queryingUser: FullUser = UserFactory.generateTestUser("querying")
    var targetUser: FullUser = UserFactory.generateTestUser("target")

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