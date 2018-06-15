package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.util.SessionManager
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.every
import io.mockk.objectMockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class UserTest : WithLogging() {

    val endpoints: Users by lazy {
        Users()
    }

    @Test
    fun configured() {
        assertTrue(endpoints.adminConfigured(), "Tepid is not configured")
    }

}

class TestUserGetQuota : WithLogging () {

    /**
     * Runs a test of Users.getQuota, Mockking [tailoredUser] as the user returned by SessionManager
     */
    private fun userGetQuotaTest (tailoredUser: FullUser?, expected: Int, message: String){
        every {
            SessionManager.queryUser("targetUser", null)
        } returns (tailoredUser)
        val actual = Users.getQuota("targetUser")
        assertEquals(expected, actual, message)
    }

    private fun userGetQuotaTest(tailoredUser: FullUser, tailoredUserRole: String, expected: Int, message: String) {
        every {
            AuthenticationFilter.getCtfRole(tailoredUser)
        } returns tailoredUserRole
        userGetQuotaTest(tailoredUser, expected, message)
    }


    @Before
    fun initTest() {
        objectMockk(SessionManager).mock()
        objectMockk(AuthenticationFilter).mock()
    }
    @After
    fun tearTest(){
        objectMockk(SessionManager).unmock()
        objectMockk(AuthenticationFilter).unmock()
    }


    @Test
    fun testGetQuotaQueriedUserNull(){
        userGetQuotaTest(null, 0, "Null user is not assigned 0 quota")
    }

    @Test
    fun testGetQuotaQueriedUserNoRole(){
        userGetQuotaTest(FullUser(role = ""), "", 0, "Null user is not assigned 0 quota")
    }

    @Test
    fun testGetQuotaElder(){
        fail("Test needs an expected value (discussion item)")
        userGetQuotaTest(FullUser(role = ELDER), ELDER, 10000, "Elder is not given correct quota")
    }

    @Test
    fun testGetQuotaCTFer(){
        fail("Test needs an expected value (discussion item)")
        userGetQuotaTest(FullUser(role = CTFER), CTFER, 10000, "CTFER is not given correct quota")
    }

    @Test
    fun testGetQuotaUserIgnoreSummerSemester(){
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSemesterPre2016 () {
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSemester2016F () {
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSemesterPost2016F () {
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSpanMultipleSemesters () {
        fail("Test is not implemented")
    }

}