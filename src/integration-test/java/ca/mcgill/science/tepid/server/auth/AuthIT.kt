package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.*

open class AuthIT {
    protected fun FullUser?.assertEqualsTestUser() {
        assertNotNull(this)
        assertEquals(PropsLDAPTestUser.TEST_USER, shortUser, "Short user mismatch. Perhaps you passed in the long user in your test?")
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

    companion object{
        @BeforeAll
        fun before() {
            Assumptions.assumeTrue(Config.LDAP_ENABLED)
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

    //TODO: parametrise for test user, not real data
    @Test
    fun queryWithoutPass() {
        Ldap.queryUser(PropsLDAPTestUser.TEST_USER, null).assertEqualsTestUser()
    }
}

class SessionManagerIT : AuthIT() {

    @Test
    fun queryUserNotInDb() {
        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun authenticateWithLdapUserNotInDb() {
        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun authenticateWithLdapUserInDb() {
        AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER, null)
                ?: fail("Couldn't prime DB with test user ${PropsLDAPTestUser.TEST_USER}")
        AuthenticationManager.authenticate(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun queryUserInDb() {
        // TODO: add user to DB in way which does not need LDAP
        val ldapUser = Ldap.queryUser(PropsLDAPTestUser.TEST_USER, null)
                ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from LDAP")
        AuthenticationManager.updateDbWithUser(ldapUser)
        AuthenticationManager.queryUserDb(PropsLDAPTestUser.TEST_USER) ?: fail("User ${PropsLDAPTestUser.TEST_USER} not already in DB")

        AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER, PropsLDAPTestUser.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun autosuggest() {
        AutoSuggest.autoSuggest(PropsLDAPTestUser.TEST_USER, 1).getResult(20000)[0].assertEqualsTestUser()
    }

    fun isExchange(testSU: String): Boolean {
        val ldapUser = Ldap.queryUser(testSU, null) ?: fail("Couldn't get test user $testSU from LDAP")
        return Config.CURRENT_EXCHANGE_GROUP in ldapUser.groups
    }

    fun addToExchangeStudent(testSU: String) {
        println("Adding")
        assertFalse(isExchange(testSU), "Precondition failed: user $testSU is already in LDAP group")
        assertTrue(ExchangeManager.setExchangeStudent(testSU, true))
        assertTrue(isExchange(testSU))
    }

    fun addToExchangeStudentAlreadyIn(testSU: String) {
        println("Re-adding")
        assertTrue(isExchange(testSU), "Precondition failed: user $testSU is not already in LDAP group")
        assertTrue(ExchangeManager.setExchangeStudent(testSU, true))
        assertTrue(isExchange(testSU))
    }

    fun removeFromExchangeStudentAlreadyOut(testSU: String) {
        println("Re-removing")
        assertFalse(isExchange(testSU), "Precondition failed: user $testSU is not already out of LDAP group")
        assertFalse(ExchangeManager.setExchangeStudent(testSU, false))
        assertFalse(isExchange(testSU))
    }

    fun removeFromExchangeStudent(testSU: String) {
        println("Removing")
        assertTrue(isExchange(testSU), "Precondition failed: user $testSU is not already in LDAP group")
        assertFalse(ExchangeManager.setExchangeStudent(testSU, false))
        assertFalse(isExchange(testSU))
    }

    @Test
    fun addAndRemoveFromExchange() {
        when (isExchange(PropsLDAPTestUser.TEST_USER)) {
            true -> {
                println("Already in")
                removeFromExchangeStudent(PropsLDAPTestUser.TEST_USER)
                removeFromExchangeStudentAlreadyOut(PropsLDAPTestUser.TEST_USER)
                addToExchangeStudent(PropsLDAPTestUser.TEST_USER)
                addToExchangeStudentAlreadyIn(PropsLDAPTestUser.TEST_USER)
                println("Done")
            }
            false -> {
                println("Already out")
                addToExchangeStudent(PropsLDAPTestUser.TEST_USER)
                addToExchangeStudentAlreadyIn(PropsLDAPTestUser.TEST_USER)
                removeFromExchangeStudent(PropsLDAPTestUser.TEST_USER)
                removeFromExchangeStudentAlreadyOut(PropsLDAPTestUser.TEST_USER)
                println("Done")
            }
        }
    }

    @Test
    fun forceDbRefresh() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER, null) ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from DB or LDAP")
        user.groups = setOf(AdGroup("DefinitelyFakeGroup"))
        AuthenticationManager.updateDbWithUser(user)

        val refreshedUser = AuthenticationManager.refreshUser(PropsLDAPTestUser.TEST_USER)
        val alteredUser = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER, null) ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from DB or LDAP")

        assertFalse(alteredUser.groups.contains(AdGroup("DefinitelyFakeGroup")), "User has not been refreshed")
    }

    @Test
    fun invalidateSession() {
        val user = AuthenticationManager.queryUser(PropsLDAPTestUser.TEST_USER, null) ?: fail("Couldn't get test user ${PropsLDAPTestUser.TEST_USER} from DB or LDAP")
        val session = SessionManager.start(user, 2400)
        assertNotNull(SessionManager.get(session._id!!))

        SessionManager.invalidateSessions(user.shortUser!!)

        assertNull(SessionManager.get(session._id!!))
    }
}