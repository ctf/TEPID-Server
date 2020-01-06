package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.HibernateCrud
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.Config.emf
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

open class AuthIT {

    init {
        // ensures Config can enable the proper paths to load from
        println("Testing against ${Config.TEPID_URL_PRODUCTION}")
    }

    protected fun FullUser?.assertEqualsTestUser() {
        assertNotNull(this)
        assertEquals(
            PropsLDAPTestUser.TEST_USER,
            shortUser,
            "Short user mismatch. Perhaps you passed in the long user in your test?"
        )
        val user = toUser()
        assertTrue(user.role.isNotEmpty(), "Role may not have propagated")
    }

    private fun FullUser?.assertValidUser() {
        assertNotNull(this)
        mapOf(
            "givenName" to givenName,
            "lastName" to lastName,
            "studentId" to studentId,
            "longUser" to longUser,
            "email" to email
        ).forEach { (tag, data) ->
            assertNotNull(data, "$tag is null for user")
        }
    }

    fun deleteUser(shortUser: ShortUser = PropsLDAPTestUser.TEST_USER) {
        Config.emf
        val userDb = HibernateCrud<FullUser, String?>(emf!!, FullUser::class.java)
        try {
            userDb.deleteById(shortUser)
        } catch (e: IllegalStateException) {
        }
    }

    companion object {
        @BeforeAll
        fun before() {
            Assumptions.assumeTrue(PropsLDAPTestUser.TEST_USER.isNotEmpty())
            Assumptions.assumeTrue(PropsLDAPTestUser.TEST_PASSWORD.isNotEmpty())
            println("Running ldap tests with test user")
        }
    }
}

class LdapIT : AuthIT() {

    @Test
    fun authenticate() {
        Ldap.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD).assertEqualsTestUser()
    }

    // TODO: parametrise for test user, not real data
    @Test
    fun queryWithResourceAccount() {
        AuthenticationManager.queryUserLdap(PropsLDAPTestUser.TEST_USER).assertEqualsTestUser()
    }
}

class SessionManagerIT : AuthIT() {

    @Test
    fun queryUserNotInDb() {
        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithLdapUserInDb() {
        AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER)
            ?: fail("Couldn't prime DB with test user ${PropsLDAPTestUser.TEST_USER}")
        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun queryUserInDb() {
        val ldapUser = AuthenticationManager.queryUserLdap(PropsLDAPTestUser.TEST_USER)
            ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from LDAP")
        DB.users.put(ldapUser)
        AuthenticationManager.queryUserDb(PropsLDAPTestUser.TEST_USER)
            ?: fail("User ${PropsLDAPTestUser.TEST_USER} not already in DB")

        AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER)
            .assertEqualsTestUser()
    }

    @Test
    fun forceDbRefresh() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER)
            ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from DB or LDAP")
        user.groups = setOf(AdGroup("DefinitelyFakeGroup"))
        DB.users.put(user)

        val refreshedUser = AuthenticationManager.refreshUser(PropsLDAPTestUser.TEST_USER)
        val alteredUser = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER)
            ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from DB or LDAP")

        assertFalse(alteredUser.groups.contains(AdGroup("DefinitelyFakeGroup")), "User has not been refreshed")
    }

    @Test
    fun invalidateSession() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER)
            ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from DB or LDAP")
        val session = SessionManager.start(user, 2400)
        assertNotNull(SessionManager.get(session._id!!))

        SessionManager.invalidateSessions(user.shortUser!!)

        assertNull(SessionManager.get(session._id!!))
    }
}

class AuthenticateIT : AuthIT() {

    @Test
    fun authenticateWithShortUserNotInDb() {
        deleteUser(PropsLDAPTestUser.TEST_USER)

        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithShortUser() {
        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithEmailNotInDb() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER) ?: fail("could not get user")
        deleteUser(user.shortUser!!)

        AuthenticationManager.authenticate(user.email!!, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithEmail() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER) ?: fail("could not get user")

        AuthenticationManager.authenticate(user.email!!, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithLongUserNotInDb() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER) ?: fail("could not get user")
        deleteUser(user.shortUser!!)

        AuthenticationManager.authenticate(user.longUser!!, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithLongUser() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER) ?: fail("could not get user")

        AuthenticationManager.authenticate(user.longUser!!, PropsLDAPTestUser.TEST_PASSWORD)
            .assertEqualsTestUser()
    }

    @Test
    fun authenticateWithIncorrectPasswordNotInDb() {
        deleteUser(PropsLDAPTestUser.TEST_USER)

        assertNull(AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, "PropsLDAPTestUser.TEST_PASSWORD"))
    }

    @Test
    fun authenticateWithIncorrectPassword() {
        assertNull(AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, "PropsLDAPTestUser.TEST_PASSWORD"))
    }
}