package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.Course
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.utils.WithLogging
import ca.mcgill.science.tepid.server.util.*

import io.mockk.*
import org.junit.*
import org.junit.Test
import java.util.logging.Logger
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
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
        val wt = mockk<WebTarget>(relaxed = true)
        every {
            wt.putJson(FullUser::class)
        } returns Response.accepted().build()
        every {
            CouchDb.path(any())
        } returns wt
        SessionManager.updateDbWithUser(testUser)
        verify { CouchDb.path("u" + testSU) }
    }
    
    @Test
    fun testUpdateUserUnsuccessfulResponse () {
        fail("Test is not implemented")
    }
    
    @Test
    fun testUpdateUserWithException () {
        fail("Test is not implemented")
    }
}