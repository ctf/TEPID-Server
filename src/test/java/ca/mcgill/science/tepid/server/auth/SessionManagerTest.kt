package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.bindings.LOCAL
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.DbLayer
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.junit.jupiter.api.*
import org.mindrot.jbcrypt.BCrypt
import javax.ws.rs.core.Response
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MergeUsersTest {


    // This should never happen, but it would cause so much trouble downstream that we need to guard against it
    // I don't even have a plausible scenario for how it would happen
    @Test
    fun testMergeUserNoShortUser() {
        val dbUser: FullUser? = FullUser()
        val ldapUser = FullUser()
        Assertions.assertThrows(RuntimeException::class.java) { SessionManager.mergeUsers(ldapUser, dbUser) }
    }

    @Test
    fun testMergeUsersNonMatchNullDbUser() {
        val dbUser: FullUser? = null
        val ldapUser = UserFactory.makeLdapUser()
        val actual = SessionManager.mergeUsers(ldapUser, dbUser)
        assertEquals(UserFactory.makeLdapUser(), actual)
    }

    // This should never happen, but it would cause so much trouble downstream that we need to guard against it.
    // It would be indicative that somehow either our database or the LDAP database had become degraded such that whatever was used to query the shortUser of a user (like an email) did not match between our database and the LDAP.
    // For example, if someone had manually changed an email in LDAP to refer to a different shortUser.
    // I see no sane way to proceed automatically in that case
    @Test()
    fun testMergeUsersNonMatchDbUser() {
        val dbUser: FullUser? = UserFactory.makeDbUser().copy(shortUser = "dbSU")
        val ldapUser = UserFactory.makeLdapUser().copy(shortUser = "ldapSU")
        Assertions.assertThrows(RuntimeException::class.java) { SessionManager.mergeUsers(ldapUser, dbUser) }
    }

    @Test
    fun testMergeUsers() {
        val actual = SessionManager.mergeUsers(UserFactory.makeLdapUser(), UserFactory.makeDbUser())
        assertEquals(UserFactory.makeMergedUser(), actual)
    }

    @Test
    fun testMergeUsersNoStudentIdInLdapUser() {
        val ldapUser = UserFactory.makeLdapUser().copy(studentId = -1)
        val actual = SessionManager.mergeUsers(ldapUser, UserFactory.makeDbUser())
        val expected = UserFactory.makeMergedUser().copy(studentId = UserFactory.makeDbUser().studentId)
        assertEquals(expected, actual)
    }

}

class UpdateDbWithUserTest {

    @Test
    fun testUpdateUserUnsuccessfulResponse() {

        val mockObjectNode = ObjectMapper().createObjectNode()
                .put("ok", false)
                .put("id", "utestSU")
                .put("_rev", "3333")

        val mockResponse = spyk(Response.serverError().entity(mockObjectNode).build())

        every {
            mockDb.putUser(ofType(FullUser::class))
        } returns mockResponse

        // Run Test
        SessionManager.updateDbWithUser(testUser)

        // Verifies the path
        verify { mockDb.putUser(testUser) }
        assertEquals(testUser._rev, "1111")
    }

    @Test
    fun testUpdateUserWithException() {
        val mockResponse = spyk(Response.ok().build())
        every {
            mockResponse.entity
        } throws RuntimeException("Testing")

        every {
            mockDb.putUser(ofType(FullUser::class))
        } returns mockResponse

        // Run Test
        SessionManager.updateDbWithUser(testUser)

        // Verifies the path
        verify { mockDb.putUser(testUser) }

        assertEquals(testUser._rev, "1111")
    }

    companion object {
        lateinit var mockDb: DbLayer

        val testSU = "testSU"
        val testUser = FullUser(shortUser = testSU)

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockDb = mockk<DbLayer>(relaxed = true)
            DB = mockDb
            testUser._rev = "1111"
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}

class QueryUserDbTest {
    var testUser = UserFactory.makeDbUser()
    var testOtherUser = UserFactory.makeLdapUser()

    @Test
    fun testQueryUserDbNullSam() {
        val actual = SessionManager.queryUserDb(null)
        assertEquals(null, actual, "Result was not null")
    }

    @Test
    fun testQueryUserDbByEmail() {
        every {
            mockDb.getUserOrNull(testUser.email!!)
        } returns testUser

        val actual = SessionManager.queryUserDb(testUser.email)

        verify { mockDb.getUserOrNull(testUser.email!!) }
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByEmailNull() {
        every {
            mockDb.getUserOrNull(testUser.email!!)
        } returns null

        val actual = SessionManager.queryUserDb(testUser.email)

        verify { mockDb.getUserOrNull(testUser.email!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUser() {
        every {
            mockDb.getUserOrNull(testUser.longUser!!)
        } returns testUser

        val actual = SessionManager.queryUserDb(testUser.longUser)

        verify { mockDb.getUserOrNull(testUser.longUser!!) }
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUserNull() {
        every {
            mockDb.getUserOrNull(testUser.longUser!!)
        } returns null

        val actual = SessionManager.queryUserDb(testUser.longUser)

        verify { mockDb.getUserOrNull(testUser.longUser!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByStudentId() {
        every {
            mockDb.getUserOrNull(testUser.studentId.toString())
        } returns testUser

        val actual = SessionManager.queryUserDb(testUser.studentId.toString())

        verify { mockDb.getUserOrNull(testUser.studentId.toString()) }
        assertEquals(testUser, actual, "User was not returned when searched by studentId")
    }

    @Test
    fun testQueryUserDbByStudentIdNull() {
        every {
            mockDb.getUserOrNull(testUser.studentId.toString())
        } returns null

        val actual = SessionManager.queryUserDb(testUser.studentId.toString())

        verify { mockDb.getUserOrNull(testUser.studentId.toString()) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by studentId")
    }

    @Test
    fun testQueryUserDbByShortUser() {
        every {
            mockDb.getUserOrNull(testUser.shortUser!!)
        } returns testUser

        val actual = SessionManager.queryUserDb(testUser.shortUser)

        verify { mockDb.getUserOrNull(testUser.shortUser!!) }
        assertEquals(testUser, actual, "User was not returned when searched by shortUser")
    }

    @Test
    fun testQueryUserDbByShortUserNull() {
        every {
            mockDb.getUserOrNull(testUser.shortUser!!)
        } returns null

        val actual = SessionManager.queryUserDb(testUser.shortUser)

        verify { mockDb.getUserOrNull(testUser.shortUser!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by shortUser")
    }

    companion object {
        lateinit var mockDb: DbLayer

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Config)
            every { Config.ACCOUNT_DOMAIN } returns "config.example.com"
            mockDb = mockk<DbLayer>(relaxed = true)
            DB = mockDb
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}

class QueryUserTest : WithLogging() {

    var testUser = UserFactory.makeDbUser()
    lateinit var sm: SessionManager

    @BeforeEach
    fun initTest() {
        mockkObject(Config)
        mockkObject(Ldap)
        sm = spyk(SessionManager)
    }

    @AfterEach
    fun tearTest() {
        unmockkAll()
    }

    @Test
    fun testQueryUserSamNull() {
        val actual = sm.queryUser(null, null)
        val expected = null

        assertEquals(expected, actual, "Null is not returned when SAM is null")
    }

    @Test
    fun testQueryUserDbHit() {
        every { sm.queryUserDb("SU") } returns testUser

        val actual = sm.queryUser("SU", null)
        val expected = testUser

        assertEquals(expected, actual, "User from DB is not returned when found")
    }

    @Test
    fun testQueryUserDbMissLdapDisabled() {
        every { Config.LDAP_ENABLED } returns false
        every { sm.queryUserDb("SU") } returns null

        val actual = sm.queryUser("SU", null)
        val expected = null

        assertEquals(expected, actual, "Null is not returned when DB miss and Ldap disabled")
    }

    @Test
    fun testQueryUserWithLdapBadSam() {
        every { Config.LDAP_ENABLED } returns true
        every { sm.queryUserDb("db.LU@example.com") } returns null

        val actual = sm.queryUser("db.LU@example.com", null)
        val expected = null

        verify(inverse = true) { sm.updateDbWithUser(any()) }
        assertEquals(expected, actual, "SessionManager doesn't return null if SAM is not shortUser")

    }

    @Test
    fun testQueryUserWithLdapLdapUserNull() {
        every { Config.LDAP_ENABLED } returns true
        every { sm.queryUserDb("SU") } returns null
        every { Ldap.queryUserLdap(any(), null) } returns null

        val actual = sm.queryUser("SU", null)
        val expected = null

        verify(inverse = true) { sm.updateDbWithUser(any()) }
        assertEquals(expected, actual, "SessionManager doesn't return null if Ldap returns null")
    }


    @Test
    fun testQueryUserWithLdap() {
        every { Config.LDAP_ENABLED } returns true
        every { sm.queryUserDb("SU") } returns null
        every { sm.updateDbWithUser(any()) } just runs
        every { Ldap.queryUserLdap(any(), null) } returns testUser


        val actual = sm.queryUser("SU", null)
        val expected = testUser

        verify { sm.updateDbWithUser(testUser) }
        assertEquals(expected, actual, "SessionManager doesn't return null if Ldap returns null")
    }
}

class AuthenticateTest {
    /**
     * These tests do not exhaustively test the authenticate function.
     * Rather, they test the most difficult situations
     * For example, they don't test local auth with LDAP disabled, since this case should be less difficult to handle than with LDAP enabled
     * Basically, if this is something which needs testing, the actual function has logic which breaks the general description
     */

    lateinit var sm: SessionManager
    lateinit var testUser: FullUser
    lateinit var testUserFromDb: FullUser
    var testShortUser = "testShortUser"
    var testPassword = "testPassword"

    @BeforeEach
    fun initTest() {
        testUser = UserFactory.generateTestUser("test").copy(shortUser = testShortUser, colorPrinting = true)
        testUserFromDb = UserFactory.generateTestUser("db").copy(
                shortUser = testUser.shortUser,
                studentId = 5555,
                colorPrinting = false
        )
        mockkObject(Config)
        sm = spyk(SessionManager)
        mockkObject(Ldap)
    }

    @AfterEach
    fun tearTest() {
        unmockkAll()
    }

    @Test
    fun testAuthenticateAuthTypeLocalSuccess() {
        every { Config.LDAP_ENABLED } returns true
        testUser.authType = LOCAL
        testUser.password = BCrypt.hashpw(testPassword, BCrypt.gensalt())
        every { sm.queryUserDb(testShortUser) } returns testUser

        val actual = sm.authenticate(testShortUser, testPassword)
        val expected = testUser

        assertEquals(expected, actual, "")
    }

    @Test
    fun testAuthenticateAuthTypeLocalFailure() {
        every { Config.LDAP_ENABLED } returns true
        testUser.authType = LOCAL
        testUser.password = BCrypt.hashpw(testPassword, BCrypt.gensalt())
        every { sm.queryUserDb(testShortUser) } returns testUser

        val actual = sm.authenticate(testShortUser, testPassword + "otherStuff")
        val expected = null

        assertEquals(expected, actual, "")
    }

    @Test
    fun testAuthenticateLdapDisabled() {
        every { Config.LDAP_ENABLED } returns false
        // not necessary, but ensures that there is a password to auth against if it's derping
        testUser.password = BCrypt.hashpw(testPassword, BCrypt.gensalt())
        every { sm.queryUserDb(testShortUser) } returns testUser


        val actual = sm.authenticate(testShortUser, testPassword)
        val expected = null

        assertEquals(expected, actual, "")
    }

    @Test
    fun testAuthenticateLdapUserNull() {
        every { Config.LDAP_ENABLED } returns true
        every { sm.queryUserDb(testShortUser) } returns testUser
        every { Ldap.authenticate(testShortUser, testPassword) } returns null

        val actual = sm.authenticate(testShortUser, testPassword)
        val expected = null

        verify { Ldap.authenticate(testShortUser, testPassword) }
        assertEquals(expected, actual, "")
    }

    @Test
    fun testAuthenticateLdap() {
        every { Config.LDAP_ENABLED } returns true
        every { sm.queryUserDb(any()) } returns testUserFromDb
        every { Ldap.authenticate(testShortUser, testPassword) } returns testUser
        every { sm.updateDbWithUser(any()) } just runs

        val actual = sm.authenticate(testShortUser, testPassword)
        val expected = SessionManager.mergeUsers(testUser, testUserFromDb)

        verify { Ldap.authenticate(testShortUser, testPassword) }
        verify { sm.mergeUsers(testUser, testUserFromDb) }
        verify { sm.updateDbWithUser(expected) }
        // a check that mergeUsers has merged some DB stuff into ldapUser
        assertFalse(expected.colorPrinting)
        assertEquals(expected, actual, "")
    }
}

class SetExchangeStudentTest {

    @Test
    fun testSetExchangeStudentLdapEnabled() {
        every { Config.LDAP_ENABLED } returns true
        SessionManager.setExchangeStudent(testSam, true)

        val targetUser = SessionManager.mergeUsers(UserFactory.makeLdapUser(), UserFactory.makeDbUser())
        verify {
            SessionManager.updateDbWithUser(
                    targetUser
            )
        }
        verify { Ldap.setExchangeStudent(testSam, true) }
    }

    @Test
    fun testSetExchangeStudentLdapDisabled() {
        every { Config.LDAP_ENABLED } returns false
        SessionManager.setExchangeStudent(testSam, true)
        verify(inverse = true) { Ldap.setExchangeStudent(testSam, true) }
    }

    companion object {
        val testSam = "SU"

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Ldap)
            every { Ldap.setExchangeStudent(any(), any()) } returns true

            mockkObject(SessionManager)
            every {
                SessionManager.queryUserDb(testSam)
            } returns UserFactory.makeDbUser()
            every {
                Ldap.queryUserLdap(testSam, null)
            } returns UserFactory.makeLdapUser()

            every {
                SessionManager.updateDbWithUser(ofType(FullUser::class))
            } just runs

            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }

}

class RefreshUserTest {

    @Test
    fun testRefreshUserLdapDisabled() {
        every { Config.LDAP_ENABLED } returns false

        val actual = SessionManager.refreshUser(testSam)

        assertEquals(UserFactory.makeDbUser(), actual)
    }

    @Test
    fun testRefreshUserLdapEnabled() {
        every { Config.LDAP_ENABLED } returns true
        every {
            SessionManager.invalidateSessions(testSam)
        } just runs

        val actual = SessionManager.refreshUser(testSam)

        assertEquals(UserFactory.makeMergedUser(), actual)

        verify {
            SessionManager.updateDbWithUser(
                    UserFactory.makeMergedUser()
            )
        }
    }

    companion object {
        val testSam = "SU"

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Ldap)

            mockkObject(SessionManager)
            every {
                SessionManager.queryUserDb(testSam)
            } returns UserFactory.makeDbUser()
            every {
                Ldap.queryUserLdap(testSam, null)
            } returns UserFactory.makeLdapUser()

            every {
                SessionManager.updateDbWithUser(ofType(FullUser::class))
            } just runs

            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}

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

        assertFalse(SessionManager.isValid(mockSession), "SessionaManger ignores a mismatch between session permission and user permission")
    }

    companion object {
        val testUser = UserFactory.makeDbUser().copy(role = "user")
        val testSession = FullSession("user", testUser)
        val mockSession = spyk(testSession)

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(SessionManager)
            every { SessionManager.queryUserDb(testUser.shortUser) } returns testUser
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
        every { DB.getSessionOrNull("testID") } returns testSession
        every { DB.deleteSession("testID") } returns "fakeResponse"
        every { SessionManager.isValid(testSession) } returns false

        assertEquals(null, SessionManager.get("testID"), "Does not return null for invalid session")
    }

    @Test
    fun testGetValidSession() {
        every { DB.getSessionOrNull("testID") } returns testSession
        every { DB.deleteSession("testID") } returns "fakeResponse"
        every { SessionManager.isValid(testSession) } returns true

        assertEquals(testSession, SessionManager.get("testID"), "Does not return valid session")

    }

    companion object {
        val testUser = UserFactory.makeDbUser().copy(role = "user")
        val testSession = FullSession("user", testUser)

        @JvmStatic
        @BeforeAll
        fun initTest() {
            testSession._id = "testId"
            testSession._rev = "testRev"
            mockkObject(SessionManager)
            every { SessionManager.queryUserDb(testUser.shortUser) } returns testUser
            mockkObject(DB)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}