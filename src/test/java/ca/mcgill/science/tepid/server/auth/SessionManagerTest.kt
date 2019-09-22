package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.DbLayer
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.ws.rs.core.Response
import kotlin.test.assertEquals
import kotlin.test.assertFalse

val okPutResponse = Response.ok().entity(PutResponse(id="TEST")).build()

class MergeUsersTest {

    // This should never happen, but it would cause so much trouble downstream that we need to guard against it
    // I don't even have a plausible scenario for how it would happen
    @Test
    fun testMergeUserNoShortUser() {
        val dbUser: FullUser? = FullUser()
        val ldapUser = FullUser()
        Assertions.assertThrows(RuntimeException::class.java) { AuthenticationManager.mergeUsers(ldapUser, dbUser) }
    }

    @Test
    fun testMergeUsersNonMatchNullDbUser() {
        val dbUser: FullUser? = null
        val ldapUser = UserFactory.makeLdapUser()
        val actual = AuthenticationManager.mergeUsers(ldapUser, dbUser)
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
        Assertions.assertThrows(RuntimeException::class.java) { AuthenticationManager.mergeUsers(ldapUser, dbUser) }
    }

    @Test
    fun testMergeUsers() {
        val actual = AuthenticationManager.mergeUsers(UserFactory.makeLdapUser(), UserFactory.makeDbUser())
        assertEquals(UserFactory.makeMergedUser(), actual)
    }

    @Test
    fun testMergeUsersNoStudentIdInLdapUser() {
        val ldapUser = UserFactory.makeLdapUser().copy(studentId = -1)
        val actual = AuthenticationManager.mergeUsers(ldapUser, UserFactory.makeDbUser())
        val expected = UserFactory.makeMergedUser().copy(studentId = UserFactory.makeDbUser().studentId)
        assertEquals(expected, actual)
    }
}

class QueryUserDbTest {
    var testUser = UserFactory.makeDbUser()
    var testOtherUser = UserFactory.makeLdapUser()

    @Test
    fun testQueryUserDbByEmail() {
        every {
            mockDb.getUserOrNull(testUser.email!!)
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.email!!)

        verify { mockDb.getUserOrNull(testUser.email!!) }
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByEmailNull() {
        every {
            mockDb.getUserOrNull(testUser.email!!)
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.email!!)

        verify { mockDb.getUserOrNull(testUser.email!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUser() {
        every {
            mockDb.getUserOrNull(testUser.longUser!!)
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.longUser!!)

        verify { mockDb.getUserOrNull(testUser.longUser!!) }
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUserNull() {
        every {
            mockDb.getUserOrNull(testUser.longUser!!)
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.longUser!!)

        verify { mockDb.getUserOrNull(testUser.longUser!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByStudentId() {
        every {
            mockDb.getUserOrNull(testUser.studentId.toString())
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.studentId.toString())

        verify { mockDb.getUserOrNull(testUser.studentId.toString()) }
        assertEquals(testUser, actual, "User was not returned when searched by studentId")
    }

    @Test
    fun testQueryUserDbByStudentIdNull() {
        every {
            mockDb.getUserOrNull(testUser.studentId.toString())
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.studentId.toString())

        verify { mockDb.getUserOrNull(testUser.studentId.toString()) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by studentId")
    }

    @Test
    fun testQueryUserDbByShortUser() {
        every {
            mockDb.getUserOrNull(testUser.shortUser!!)
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.shortUser!!)

        verify { mockDb.getUserOrNull(testUser.shortUser!!) }
        assertEquals(testUser, actual, "User was not returned when searched by shortUser")
    }

    @Test
    fun testQueryUserDbByShortUserNull() {
        every {
            mockDb.getUserOrNull(testUser.shortUser!!)
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.shortUser!!)

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

    @BeforeEach
    fun createMockDb() {
        mockDb = mockk<DbLayer>(relaxed=true)
        DB = mockDb
    }
    @AfterEach
    fun unmockDb() {
        unmockkObject(mockDb)
    }

    companion object{
        lateinit var mockDb : DbLayer
        lateinit var am: AuthenticationManager

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Config)
            mockkObject(Ldap)
            am = spyk(AuthenticationManager)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }

    @Test
    fun testQueryUserDbHit() {
        every { am.queryUserDb("SU") } returns testUser

        val actual = am.queryUser("SU", null)
        val expected = testUser

        assertEquals(expected, actual, "User from DB is not returned when found")
    }

    @Test
    fun testQueryUserWithLdapBadSam() {
        every { am.queryUserDb("db.LU@example.com") } returns null

        val actual = am.queryUser("db.LU@example.com", null)
        val expected = null

        verify(inverse = true) { mockDb.putUser(any()) }
        assertEquals(expected, actual, "AuthenticationManager doesn't return null if SAM is not shortUser")
    }

    @Test
    fun testQueryUserWithLdapLdapUserNull() {
        every { am.queryUserDb("SU") } returns null
        every { Ldap.queryUserWithResourceAccount(any()) } returns null

        val actual = am.queryUser("SU", null)
        val expected = null

        verify(inverse = true) { mockDb.putUser(any()) }
        assertEquals(expected, actual, "AuthenticationManager doesn't return null if Ldap returns null")
    }

    @Test
    fun testQueryUserWithLdap() {
        every { am.queryUserDb("SU") } returns null
        every { mockDb.putUser(any()) } returns okPutResponse
        every { Ldap.queryUserWithResourceAccount(any()) } returns testUser

        val actual = am.queryUser("SU", null)
        val expected = testUser

        verify { mockDb.putUser(testUser) }
        assertEquals(expected, actual, "AuthenticationManager doesn't return null if Ldap returns null")
    }
}

class AuthenticateTest {
    /**
     * These tests do not exhaustively test the authenticate function.
     * Rather, they test the most difficult situations
     * For example, they don't test local auth with LDAP disabled, since this case should be less difficult to handle than with LDAP enabled
     * Basically, if this is something which needs testing, the actual function has logic which breaks the general description
     */

    lateinit var sm: AuthenticationManager
    private lateinit var testUser: FullUser
    private lateinit var testUserFromDb: FullUser
    private var testShortUser = "testShortUser"
    private var testPassword = "testPassword"
    lateinit var mockDb : DbLayer


    @BeforeEach
    fun initTest() {
        mockDb = mockk<DbLayer>(relaxed = true)
        DB = mockDb
        testUser = UserFactory.generateTestUser("test").copy(shortUser = testShortUser, colorPrinting = true)
        testUserFromDb = UserFactory.generateTestUser("db").copy(
            shortUser = testUser.shortUser,
            studentId = 5555,
            colorPrinting = false
        )
        mockkObject(Config)
        sm = spyk(AuthenticationManager)
        mockkObject(Ldap)

    }

    @AfterEach
    fun tearTest() {
        unmockkAll()
    }

    @Test
    fun testAuthenticateLdapUserNull() {
        every { sm.queryUserDb(testShortUser) } returns testUser
        every { Ldap.authenticate(testShortUser, testPassword) } returns null

        val actual = sm.authenticate(testShortUser, testPassword)
        val expected = null

        verify { Ldap.authenticate(testShortUser, testPassword) }
        assertEquals(expected, actual, "")
    }

    @Test
    fun testAuthenticateLdap() {
        every { sm.queryUserDb(any()) } returns testUserFromDb
        every { Ldap.authenticate(testShortUser, testPassword) } returns testUser
        every { mockDb.putUser(any()) } returns okPutResponse

        val actual = sm.authenticate(testShortUser, testPassword)
        val expected = AuthenticationManager.mergeUsers(testUser, testUserFromDb)

        verify { Ldap.authenticate(testShortUser, testPassword) }
        verify { sm.mergeUsers(testUser, testUserFromDb) }
        verify { mockDb.putUser(expected) }
        // a check that mergeUsers has merged some DB stuff into ldapUser
        assertFalse(expected.colorPrinting)
        assertEquals(expected, actual, "")
    }
}

class SetExchangeStudentTest {

    @Test
    fun testSetExchangeStudentLdapEnabled() {
        ExchangeManager.setExchangeStudent(testSam, true)

        val targetUser = AuthenticationManager.mergeUsers(UserFactory.makeLdapUser(), UserFactory.makeDbUser())
        verify {
            AuthenticationManager.refreshUser(
                targetUser.shortUser!!
            )
        }
        verify { ExchangeManager.setExchangeStudentLdap(testSam, true) }
    }

    companion object {
        val testSam = "SU"

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Ldap)
            mockkObject(ExchangeManager)
            every { ExchangeManager.setExchangeStudentLdap(any(), any()) } returns true

            mockkObject(AuthenticationManager)
            every {
                AuthenticationManager.refreshUser(testSam)
            } returns UserFactory.makeDbUser()
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
    fun testRefreshUserLdapEnabled() {
        every {
            SessionManager.invalidateSessions(testSam)
        } just runs

        val actual = AuthenticationManager.refreshUser(testSam)

        assertEquals(UserFactory.makeMergedUser(), actual)

        verify {
            mockDb.putUser(
                UserFactory.makeMergedUser()
            )
        }
    }

    companion object {
        val testSam = "SU"
        lateinit var mockDb : DbLayer


        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Ldap)

            mockkObject(SessionManager)
            mockkObject(AuthenticationManager)
            mockDb = mockk<DbLayer>(relaxed = true)
            DB = mockDb

            every {
                AuthenticationManager.queryUserDb(testSam)
            } returns UserFactory.makeDbUser()
            every {
                Ldap.queryUserWithResourceAccount(testSam)
            } returns UserFactory.makeLdapUser()

            every {
                mockDb.putUser(ofType(FullUser::class))
            } returns okPutResponse

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

        assertFalse(
            SessionManager.isValid(mockSession),
            "SessionaManger ignores a mismatch between session permission and user permission"
        )
    }

    companion object {
        private val testUser = UserFactory.makeDbUser().copy(role = "user")
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
            mockkObject(AuthenticationManager)
            every { AuthenticationManager.queryUserDb(testUser.shortUser!!) } returns testUser
            mockkObject(DB)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}