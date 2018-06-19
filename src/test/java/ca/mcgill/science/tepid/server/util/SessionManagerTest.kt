package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.Course
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.utils.WithLogging
import ca.mcgill.science.tepid.server.util.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

import io.mockk.*
import org.junit.*
import org.junit.Test
import org.omg.CORBA.Object
import java.util.logging.Logger
import javax.ws.rs.client.Entity
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Request
import javax.ws.rs.core.Response
import javax.ws.rs.core.Variant
import kotlin.test.*

class SessionManagerTest : WithLogging() {

    @Before
    fun initTest() {
        objectMockk(Ldap).mock()
    }

    @After
    fun tearTest() {
        objectMockk(Ldap).unmock()
    }

}

class MergeUsersTest {

    //note that the shortUsers are the same, since they are the unique key
    val testLdapUser = FullUser(displayName = "ldapDN", givenName = "ldapGN", lastName = "ldapLN", shortUser = "SU", longUser = "ldapLU", email = "ldapEM", faculty = "ldapFaculty", groups = listOf("ldapGroups"), courses = listOf(Course("ldapCourseName", Season.FALL, 2222)), studentId = 1111)
    val testDbUser = FullUser(displayName = "dbDN", givenName = "dbGN", lastName = "dbLN", shortUser = "SU", longUser = "dbLU", email = "dbEM", faculty = "dbFaculty", groups = listOf("dbGroups"), courses = listOf(Course("dbCourseName", Season.FALL, 4444)), studentId = 3333, colorPrinting = true, jobExpiration = 12)
    val testMergedUser = testLdapUser.copy(colorPrinting = true, jobExpiration = 12)

    // This should never happen, but it would cause so much trouble downstream that we need to guard against it
    // I don't even have a plausible scenario for how it would happen
    @Test(expected = RuntimeException::class)
    fun testMergeUserNoShortUser () {
        val dbUser:FullUser? = FullUser()
        val ldapUser = FullUser()
        SessionManager.mergeUsers(ldapUser, dbUser)
    }

    @Test
    fun testMergeUsersNonMatchNullDbUser () {
        val dbUser:FullUser? = null
        val ldapUser = testLdapUser
        val actual = SessionManager.mergeUsers(ldapUser, dbUser)
        assertEquals(testLdapUser, actual)
    }
    
    // This should never happen, but it would cause so much trouble downstream that we need to guard against it.
    // It would be indicative that somehow either our database or the LDAP database had become degraded such that whatever was used to query the shortUser of a user (like an email) did not match between our database and the LDAP.
    // For example, if someone had manually changed an email in LDAP to refer to a different shortUser.
    // I see no sane way to proceed automatically in that case
    @Test(expected = RuntimeException::class)
    fun testMergeUsersNonMatchDbUser () {
        val dbUser:FullUser? = testDbUser.copy(shortUser = "dbSU")
        val ldapUser = testLdapUser.copy(shortUser = "ldapSU")
        SessionManager.mergeUsers(ldapUser, dbUser)
    }
    
    @Test
    fun testMergeUsers () {
        val actual = SessionManager.mergeUsers(testLdapUser, testDbUser)
        assertEquals(testMergedUser, actual)
    }

    @Test
    fun testMergeUsersNoStudentIdInLdapUser () {
        val ldapUser = testLdapUser.copy(studentId = -1)
        val actual = SessionManager.mergeUsers(ldapUser, testDbUser)
        val expected = testMergedUser.copy(studentId = testDbUser.studentId)
        assertEquals(expected, actual)
    }

}

class UpdateDbWithUserTest {
    @Before
    fun initTest() {
        objectMockk(CouchDb).mock()
        staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").mock()
        testUser._rev = "1111"
    }

    @After
    fun tearTest() {
        objectMockk(CouchDb).unmock()
        staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").unmock()
    }

    val testSU = "testSU"
    val testUser = FullUser(shortUser = testSU)

    @Test
    fun testUpdateUser () {

        val mockObjectNode = ObjectMapper().createObjectNode()
                .put("ok", true)
                .put("id", "utestSU")
                .put("_rev", "2222")

        val mockResponse = spyk(Response.ok().build())
        every {
            mockResponse.readEntity(ObjectNode::class.java)
        } returns mockObjectNode

        val wt = mockk<WebTarget>()
        every {
            wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))
        } returns mockResponse
        every {
            CouchDb.path(ofType(String::class))
        } returns wt

        // Run Test
        SessionManager.updateDbWithUser(testUser)

        // Verifies the path
        verify { CouchDb.path("u" + testSU) }
        verify { wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))}
        assertEquals(testUser._rev, "2222")
    }
    
    @Test
    fun testUpdateUserUnsuccessfulResponse () {

        val mockObjectNode = ObjectMapper().createObjectNode()
                .put("ok", true)
                .put("id", "utestSU")
                .put("_rev", "3333")

        val mockResponse = spyk(Response.serverError().build())
        every {
            mockResponse.readEntity(ObjectNode::class.java)
        } returns mockObjectNode

        val wt = mockk<WebTarget>()
        every {
            wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))
        } returns mockResponse
        every {
            CouchDb.path(ofType(String::class))
        } returns wt

        // Run Test
        SessionManager.updateDbWithUser(testUser)

        // Verifies the path
        verify { CouchDb.path("u" + testSU) }
        verify { wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))}
        assertEquals(testUser._rev, "1111")
    }
    
    @Test
    fun testUpdateUserWithException () {
        val mockResponse = spyk(Response.ok().build())
        every {
            mockResponse.readEntity(ObjectNode::class.java)
        } throws RuntimeException("Testing")

        val wt = mockk<WebTarget>()
        every {
            wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))
        } returns mockResponse
        every {
            CouchDb.path(ofType(String::class))
        } returns wt

        // Run Test
        SessionManager.updateDbWithUser(testUser)

        // Verifies the path
        verify { CouchDb.path("u" + testSU) }
        verify { wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))}
        // Verifies that it was called, but that it's unchanged
        verify { mockResponse.readEntity(ObjectNode::class.java)}
        assertEquals(testUser._rev, "1111")
    }
}

class QueryUserDbTest {
    lateinit var testUser: FullUser
    lateinit var testOtherUser: FullUser
    lateinit var wt: WebTarget

    @Before
    fun initTest() {
        objectMockk(Config).mock()
        every{Config.ACCOUNT_DOMAIN} returns "config.example.com"
        objectMockk(CouchDb).mock()
        staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").mock()

        wt = mockk<WebTarget>()

        testUser = FullUser(displayName = "dbDN", givenName = "dbGN", lastName = "dbLN", shortUser = "SU", longUser = "db.LU@example.com", email = "db.EM@example.com", faculty = "dbFaculty", groups = listOf("dbGroups"), courses = listOf(Course("dbCourseName", Season.FALL, 4444)), studentId = 3333, colorPrinting = true, jobExpiration = 12)
        testUser._id = "0000"
        testUser._rev = "0001"
        testOtherUser = FullUser(displayName = "ldapDN", givenName = "ldapGN", lastName = "ldapLN", shortUser = "SU", longUser = "ldap.LU@example.com", email = "ldap.EM@example.com", faculty = "ldapFaculty", groups = listOf("ldapGroups"), courses = listOf(Course("ldapCourseName", Season.FALL, 2222)), studentId = 1111)
        testUser._id = "1000"
        testUser._rev = "1001"
    }

    @After
    fun tearTest() {
        objectMockk(CouchDb).unmock()
        staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").unmock()
        objectMockk(Config).unmock()
    }

    private fun makeMocks(userListReturned : List<FullUser>) {
        every {
            CouchDb.path(ofType(CouchDb.CouchDbView::class))
        } returns wt
        every {
            wt.queryParam(ofType(String::class), ofType(String::class))
        } returns wt
        every {
            wt.getViewRows<FullUser>()
        } returns userListReturned
    }

    @Test
    fun testQueryUserDbNullSam () {
        val actual = SessionManager.queryUserDb(null)
        assertEquals(null, actual, "Result was not null")
    }

    @Test
    fun testQueryUserDbByEmail () {
        makeMocks(listOf<FullUser>(testUser, testOtherUser))

        val actual = SessionManager.queryUserDb(testUser.email)

        verify { CouchDb.path(CouchDb.CouchDbView.ByLongUser)}
        verify { wt.queryParam("key", match {it.toString() == "\"db.EM%40config.example.com\""})}
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByEmailNull () {
        makeMocks(listOf<FullUser>())

        val actual = SessionManager.queryUserDb(testUser.email)

        verify { CouchDb.path(CouchDb.CouchDbView.ByLongUser)}
        verify { wt.queryParam("key", match {it.toString() == "\"db.EM%40config.example.com\""})}
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUser () {
        makeMocks(listOf<FullUser>(testUser, testOtherUser))

        val actual = SessionManager.queryUserDb(testUser.longUser)

        verify { CouchDb.path(CouchDb.CouchDbView.ByLongUser)}
        verify { wt.queryParam("key", match {it.toString() == "\"db.LU%40config.example.com\""})}
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUserNull () {
        makeMocks(listOf<FullUser>())

        val actual = SessionManager.queryUserDb(testUser.longUser)

        verify { CouchDb.path(CouchDb.CouchDbView.ByLongUser)}
        verify { wt.queryParam("key", match {it.toString() == "\"db.LU%40config.example.com\""})}
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByStudentId () {
        makeMocks(listOf<FullUser>(testUser, testOtherUser))

        val actual = SessionManager.queryUserDb(testUser.studentId.toString())

        verify { CouchDb.path(CouchDb.CouchDbView.ByStudentId)}
        verify { wt.queryParam("key", match {
            println(it.toString())
            it.toString() == "3333"})}
        assertEquals(testUser, actual, "User was not returned when searched by studentId")
    }

    @Test
    fun testQueryUserDbByStudentIdNull () {
        makeMocks(listOf<FullUser>())

        val actual = SessionManager.queryUserDb(testUser.studentId.toString())

        verify { CouchDb.path(CouchDb.CouchDbView.ByStudentId)}
        verify { wt.queryParam("key", match {
            println(it.toString())
            it.toString() == "3333"})}
        assertEquals(null, actual, "Null was not returned when nonexistent searched by studentId")
    }

    @Test
    fun testQueryUserDbByShortUser () {
        fail("Test is not implemented")
    }

    @Test
    fun testQueryUserDbByShortUserNull () {
        fail("Test is not implemented")
    }

}