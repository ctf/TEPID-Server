package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.Course
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import javax.ws.rs.ClientErrorException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo
import kotlin.test.assertEquals
import kotlin.test.fail


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestUserGetQuota : WithLogging () {

    val c2018s = Course("2018s", Season.SUMMER, 2018)
    val c1066f = Course("1066f", Season.FALL, 1066)
    val c2016f = Course("2016f", Season.FALL, 2016)
    val c2018f = Course("2018f", Season.FALL, 2018)
    val c2018w = Course("2018w", Season.WINTER, 2018)
    val c2018w0 = Course("2018w other", Season.WINTER, 2018)

    /**
     * Runs a test of Users.getQuota, Mockking [tailoredUser] as the user returned by SessionManager
     */
    private fun userGetQuotaTest (tailoredUser: FullUser?, expected: Int, message: String){
        mockUser(tailoredUser)
        val actual = Users.getQuota(tailoredUser)
        assertEquals(expected, actual, message)
    }

    private fun userGetQuotaTest(tailoredUser: FullUser, tailoredUserRole: String, expected: Int, message: String) {
        every {
            AuthenticationFilter.getCtfRole(ofType(FullUser::class))
        } returns tailoredUserRole
        userGetQuotaTest(tailoredUser.copy(shortUser="tailoredUser"), expected, message)
    }

    private fun mockUser(tailoredUser: FullUser?){
        every {
            SessionManager.queryUser("targetUser", null)
        } returns (tailoredUser)
    }

    @Before
    fun initTest() {
        mockkObject(SessionManager)
        mockkObject(AuthenticationFilter)
        mockkObject(Users)
        every {
            SessionManager.queryUser("targetUser", null)
        } returns (FullUser())

    }
    @After
    fun tearTest(){
        unmockkAll()
    }

    private fun setPrintedPages(printedPages:Int) {
        every {
            Users.getTotalPrinted(ofType(String::class))
        } returns printedPages
    }


    @Test
    fun testGetQuotaQueriedUserNull(){
        userGetQuotaTest(null, 0, "Null user is not assigned 0 quota")
    }

    @Test
    fun testGetQuotaQueriedUserNoRole(){
        userGetQuotaTest(FullUser(), "", 0, "Null user is not assigned 0 quota")
    }

    @Test
    @Ignore
    fun testGetQuotaElder(){
        fail("Test needs an expected value (discussion item)")
        userGetQuotaTest(FullUser(role = ELDER), ELDER, 10000, "Elder is not given correct quota")
    }

    @Test
    @Ignore
    fun testGetQuotaCTFer(){
        fail("Test needs an expected value (discussion item)")
        userGetQuotaTest(FullUser(role = CTFER), CTFER, 10000, "CTFER is not given correct quota")
    }

    @Test
    fun testGetQuotaUserIgnoreSummerSemester(){
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c2018s)), USER, 0,"Summer gives quota")
    }

    @Test
    fun testGetQuotaUserSemesterPre2016() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c1066f)), USER, 0,"Ancient semester gives quota")
    }

    @Test
    fun testGetQuotaUserSemester2016F () {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c2016f)), USER, 500,"500 pages not give for 2016F")
    }

    @Test
    fun testGetQuotaUserSemesterPost2016F () {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c2018f)), USER, 1000,"1000 pages not give for semester")
    }

    @Test
    fun testGetQuotaUserSpanMultipleSemesters () {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c2018f, c2018w)), USER, 2000,"multiple semesters not counted")
    }

    @Test
    fun testGetQuotaTotalPrintedSubtracted(){
        setPrintedPages(300)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c2018f)), USER, 700,"Printed pages not subtracted (you had one job)")
    }

    //Tests that if there are multiple courses in the same semester they only contribute as one semester
    @Test
    fun testGetQuotaMultipleCoursesReduced(){
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role= USER, courses = listOf(c2018w0, c2018w)), USER, 1000,"multiple courses in same semester counted as other semesters")
    }


}

class getUserBySamTest : WithLogging() {

    val endpoints: Users by lazy {
        Users()
    }

    var queryingUser: FullUser = UserFactory.generateTestUser("querying")
    var targetUser: FullUser = UserFactory.generateTestUser("target")

    lateinit var uriInfo: UriInfo
    lateinit var rc : ContainerRequestContext



    @Before
    fun initTest() {
    }
    @After
    fun tearTest() {
        unmockkAll()
    }

    fun mockSession(role:String){
        uriInfo = mockk<UriInfo>()
        every {uriInfo.getQueryParameters().containsKey("noRedirect")} returns true

        val session = FullSession(role, queryingUser)

        rc = mockk<ContainerRequestContext>()
        mockkStatic("ca.mcgill.science.tepid.server.util.UtilsKt")
        every {
            rc.getSession()
        } returns session


    }
    fun mockUserQuery(user:FullUser?){
        mockkObject(SessionManager)
        every {
            SessionManager.queryUser("targetUser", null)
        } returns (user)
    }
    fun doTestUserQuery(role:String, queryResult: FullUser?, expected: FullUser?): Response {
        mockSession(role)
        mockUserQuery(queryResult)
        val result = endpoints.queryLdap("targetUser", null, rc, uriInfo)
        assertEquals(expected, result.entity)
        return result
    }

    fun doTestUserQuery403(role:String, queryResult: FullUser?) {
        mockSession(role)
        mockUserQuery(queryResult)
        val response = endpoints.queryLdap("targetUser", null, rc, uriInfo)
        assertEquals(403, response.status)
    }

    @Test
    fun getUserBySamElderAndValidUser() {
        doTestUserQuery(ELDER, targetUser, targetUser)
    }

    @Test
    fun getUserBySamElderAndInvalidUser(){

        try {
            doTestUserQuery(ELDER, null, null)
            fail("Did not throw 404 error when an Elder queried for a nonexistant user")
        } catch (e: ClientErrorException) {
            assertEquals(404, e.response.status)
        }
    }

    @Test
    fun getUserBySamCtferAndValidUser() {
        doTestUserQuery(CTFER, targetUser, targetUser)
    }

    @Test
    fun getUserBySamCtferAndInvalidUser(){

        try {
            doTestUserQuery(CTFER, null, null)
            fail("Did not throw 404 error when a CTFer queried for a nonexistant user")
        } catch (e: ClientErrorException) {
            assertEquals(404, e.response.status)
        }
    }

    @Test
    fun getUserBySamUserAndInvalidUser(){
        doTestUserQuery403(USER,null)
    }

    @Test
    fun getUserBySamUserAndOtherUser(){
        doTestUserQuery403(USER,targetUser)
    }

    @Test
    fun getUserBySamUserAndSelfUser(){
        doTestUserQuery(USER, queryingUser, queryingUser)
    }


    /*
    I am aware that this is technically overkill; the AuthenticationFilter should already reject sessions without any role.
    That said, given how hard it is to get sessions right and given how bad it would be to leak user data, I've got these here.
     */
    @Test
    fun getUserBySamNoneAndValidUser() {
        doTestUserQuery403("",targetUser)
    }

    @Test
    fun getUserBySamNoneAndInvalidUser(){
        doTestUserQuery403("",null)
    }
}