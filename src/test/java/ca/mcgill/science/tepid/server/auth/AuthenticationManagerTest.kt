package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.TestHelpers
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.DbLayer
import ca.mcgill.science.tepid.server.db.createDb
import ca.mcgill.science.tepid.server.server.Config
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MergeUsersTest {

    // This should never happen, but it would cause so much trouble downstream that we need to guard against it
    // I don't even have a plausible scenario for how it would happen
    @Test
    fun testMergeUserNoShortUser() {
        val dbUser: FullUser? = FullUser()
        val ldapUser = FullUser()
        Assertions.assertThrows(RuntimeException::class.java) {
            AuthenticationManager.mergeUsers(
                ldapUser,
                dbUser
            )
        }
    }

    @Test
    fun testMergeUsersNonMatchNullDbUser() {
        val dbUser: FullUser? = null
        val ldapUser = TestHelpers.makeLdapUser()
        val actual = AuthenticationManager.mergeUsers(ldapUser, dbUser)
        assertEquals(TestHelpers.makeLdapUser(), actual)
    }

    // This should never happen, but it would cause so much trouble downstream that we need to guard against it.
    // It would be indicative that somehow either our database or the LDAP database had become degraded such that whatever was used to query the shortUser of a user (like an email) did not match between our database and the LDAP.
    // For example, if someone had manually changed an email in LDAP to refer to a different shortUser.
    // I see no sane way to proceed automatically in that case
    @Test()
    fun testMergeUsersNonMatchDbUser() {
        val dbUser: FullUser = TestHelpers.makeDbUser()
        dbUser.shortUser = "dbSU"
        val ldapUser = TestHelpers.makeLdapUser()
        ldapUser.shortUser = "ldapSU"
        Assertions.assertThrows(RuntimeException::class.java) {
            AuthenticationManager.mergeUsers(
                ldapUser,
                dbUser
            )
        }
    }

    @Test
    fun testMergeUsers() {
        val actual = AuthenticationManager.mergeUsers(
            TestHelpers.makeLdapUser(),
            TestHelpers.makeDbUser()
        )
        assertEquals(TestHelpers.makeMergedUser(), actual)
    }

    @Test
    fun testMergeUsersNoStudentIdInLdapUser() {
        val ldapUser = TestHelpers.makeLdapUser()
        ldapUser.studentId = -1
        val actual = AuthenticationManager.mergeUsers(
            ldapUser,
            TestHelpers.makeDbUser()
        )
        val expected = TestHelpers.makeMergedUser()
            .copy(studentId = TestHelpers.makeDbUser().studentId)
        assertEquals(expected, actual)
    }

    @Test
    fun testMergeUsersCombineSemesters() {
        val testSemester = Semester(Season.FALL, 1111)
        val ldapUser = TestHelpers.makeLdapUser()
        ldapUser.semesters = setOf(Semester.current)
        val dbUser = TestHelpers.makeDbUser()
        dbUser.semesters = setOf(testSemester)
        val actual = AuthenticationManager.mergeUsers(
            ldapUser,
            dbUser
        )
        val expected = TestHelpers.makeMergedUser()
            .copy(semesters = setOf(Semester.current, testSemester))
        assertEquals(expected, actual)
    }

    @Test
    fun testMergeUsersNewUser() {
        val dbUser: FullUser? = null
        val ldapUser = TestHelpers.makeLdapUser()
        val result = AuthenticationManager.mergeUsers(
            ldapUser,
            dbUser
        )
        assertEquals(ldapUser.shortUser, result.shortUser)
    }
}

class QueryUserDbTest {
    var testUser = TestHelpers.makeDbUser()
    var testOtherUser = TestHelpers.makeLdapUser()

    @Test
    fun testQueryUserDbByEmail() {
        every {
            mockDb.users.find(testUser.email!!)
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.email!!)

        verify { mockDb.users.find(testUser.email!!) }
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByEmailNull() {
        every {
            mockDb.users.find(testUser.email!!)
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.email!!)

        verify { mockDb.users.find(testUser.email!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUser() {
        every {
            mockDb.users.find(testUser.longUser!!)
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.longUser!!)

        verify { mockDb.users.find(testUser.longUser!!) }
        assertEquals(testUser, actual, "User was not returned when searched by Email")
    }

    @Test
    fun testQueryUserDbByFullUserNull() {
        every {
            mockDb.users.find(testUser.longUser!!)
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.longUser!!)

        verify { mockDb.users.find(testUser.longUser!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by Email")
    }

    @Test
    fun testQueryUserDbByStudentId() {
        every {
            mockDb.users.find(testUser.studentId.toString())
        } returns testUser

        val actual =
            AuthenticationManager.queryUserDb(testUser.studentId.toString())

        verify { mockDb.users.find(testUser.studentId.toString()) }
        assertEquals(testUser, actual, "User was not returned when searched by studentId")
    }

    @Test
    fun testQueryUserDbByStudentIdNull() {
        every {
            mockDb.users.find(testUser.studentId.toString())
        } returns null

        val actual =
            AuthenticationManager.queryUserDb(testUser.studentId.toString())

        verify { mockDb.users.find(testUser.studentId.toString()) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by studentId")
    }

    @Test
    fun testQueryUserDbByShortUser() {
        every {
            mockDb.users.find(testUser.shortUser!!)
        } returns testUser

        val actual = AuthenticationManager.queryUserDb(testUser.shortUser!!)

        verify { mockDb.users.find(testUser.shortUser!!) }
        assertEquals(testUser, actual, "User was not returned when searched by shortUser")
    }

    @Test
    fun testQueryUserDbByShortUserNull() {
        every {
            mockDb.users.find(testUser.shortUser!!)
        } returns null

        val actual = AuthenticationManager.queryUserDb(testUser.shortUser!!)

        verify { mockDb.users.find(testUser.shortUser!!) }
        assertEquals(null, actual, "Null was not returned when nonexistent searched by shortUser")
    }

    companion object {
        lateinit var mockDb: DbLayer

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Config)
            every { Config.ACCOUNT_DOMAIN } returns "config.example.com"
            mockDb = TestHelpers.makeMockDb()
            DB = mockDb
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
            DB = createDb()
        }
    }
}

class AuthenticateTest {
    private lateinit var testUser: FullUser
    private lateinit var testUserFromDb: FullUser
    private var testShortUser = "testShortUser"
    private var testPassword = "testPassword"
    lateinit var mockDb: DbLayer

    @BeforeEach
    fun initTest() {
        createDb()
        mockDb = TestHelpers.makeMockDb()
        DB = mockDb
        testUser = TestHelpers.generateTestUser("test")
            .copy(colorPrinting = true)
        testUser._id = testShortUser
        testUserFromDb = TestHelpers.generateTestUser("db").copy(
            studentId = 5555,
            colorPrinting = false
        )
        testUserFromDb._id = testShortUser
        mockkObject(AuthenticationManager)
        mockkObject(Ldap)
    }

    @AfterEach
    fun tearTest() {
        unmockkAll()
        DB = createDb()
    }

    @Test
    fun testAuthenticateLdapUserNull() {
        every { AuthenticationManager.queryUserDb(testShortUser) } returns testUser
        every { Ldap.authenticate(testShortUser, testPassword) } returns null

        val actual = AuthenticationManager.authenticate(testShortUser, testPassword)
        val expected = null

        verify { Ldap.authenticate(testShortUser, testPassword) }
        assertEquals(expected, actual, "")
    }

    @Test
    fun testAuthenticateLdap() {
        every { AuthenticationManager.queryUserDb(any()) } returns testUserFromDb
        every { Ldap.authenticate(testShortUser, testPassword) } returns testUser
        every { mockDb.users.put(any()) } returns okPutResponse

        val actual = AuthenticationManager.authenticate(testShortUser, testPassword)
        val expected = AuthenticationManager.mergeUsers(testUser, testUserFromDb)

        verify { Ldap.authenticate(testShortUser, testPassword) }
        verify {
            AuthenticationManager.mergeUsers(
                testUser,
                testUserFromDb
            )
        }
        verify { mockDb.users.put(expected) }
        // a check that mergeUsers has merged some DB stuff into ldapUser
        assertFalse(expected.colorPrinting)
        assertEquals(expected, actual, "")
    }
}

class RefreshUserTest {

    @Test
    fun testRefreshUserLdapEnabled() {
        every {
            SessionManager.invalidateSessions(testSam)
        } just runs

        val actual = AuthenticationManager.refreshUser(testSam)

        assertEquals(TestHelpers.makeMergedUser(), actual)

        verify {
            mockDb.users.put(
                TestHelpers.makeMergedUser()
            )
        }
    }

    companion object {
        val testSam = "SU"
        lateinit var mockDb: DbLayer

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Ldap)

            mockkObject(SessionManager)
            mockkObject(AuthenticationManager)
            mockDb = TestHelpers.makeMockDb()
            DB = mockDb

            every {
                AuthenticationManager.queryUserDb(testSam)
            } returns TestHelpers.makeDbUser()
            every {
                AuthenticationManager.queryUserLdap(testSam)
            } returns TestHelpers.makeLdapUser()

            every {
                mockDb.users.put(ofType(FullUser::class))
            } returns okPutResponse

            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
            DB = createDb()
        }
    }
}

class QueryUserTest : Logging {

    var testUser = TestHelpers.makeDbUser()

    @BeforeEach
    fun createMockDb() {
        mockDb = TestHelpers.makeMockDb()
        DB = mockDb
    }
    @AfterEach
    fun unmockDb() {
        unmockkObject(mockDb)
    }

    companion object {
        lateinit var mockDb: DbLayer
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
            DB = createDb()
        }
    }

    @Test
    fun testQueryUserDbHit() {
        every { am.queryUserDb("SU") } returns testUser

        val actual = am.queryUser("SU")
        val expected = testUser

        assertEquals(expected, actual, "User from DB is not returned when found")
    }

    @Test
    fun testQueryUserWithLdapBadSam() {
        every { am.queryUserDb("db.LU@example.com") } returns null

        val actual = am.queryUser("db.LU@example.com")
        val expected = null

        verify(inverse = true) { mockDb.users.put(any()) }
        assertEquals(expected, actual, "AuthenticationManager doesn't return null if SAM is not shortUser")
    }

    @Test
    fun testQueryUserWithLdapLdapUserNull() {
        every { am.queryUserDb("SU") } returns null
        every { am.queryUserLdap(any()) } returns null

        val actual = am.queryUser("SU")
        val expected = null

        verify(inverse = true) { mockDb.users.put(any()) }
        assertEquals(expected, actual, "AuthenticationManager doesn't return null if Ldap returns null")
    }

    @Test
    fun testQueryUserWithLdap() {
        every { am.queryUserDb("SU") } returns null

        every { mockDb.users.put(any()) } returns okPutResponse
        every { am.queryUserLdap(any()) } returns testUser

        val actual = am.queryUser("SU")
        val expected = testUser

        verify { mockDb.users.put(testUser) }
        assertEquals(expected, actual, "AuthenticationManager doesn't return null if Ldap returns null")
    }
}
