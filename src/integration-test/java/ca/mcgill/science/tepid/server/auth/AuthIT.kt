package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.deleteRev
import ca.mcgill.science.tepid.server.server.Config
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import kotlin.test.*

open class AuthIT {
    @Before
    fun before() {
        Assume.assumeTrue(Config.LDAP_ENABLED)
        Assume.assumeTrue(Config.TEST_USER.isNotEmpty())
        Assume.assumeTrue(Config.TEST_PASSWORD.isNotEmpty())
        println("Running ldap tests with test user")
    }

    protected fun FullUser?.assertEqualsTestUser() {
        assertNotNull(this)
        println(this!!)
        assertEquals(Config.TEST_USER, shortUser, "Short user mismatch. Perhaps you passed in the long user in your test?")
        val user = toUser()
        assertTrue(user.role.isNotEmpty(), "Role may not have propagated")
    }

    private fun FullUser?.assertValidUser() {
        assertNotNull(this)
        println(this!!)
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
}

class LdapIT : AuthIT() {

    @Test
    fun authenticate() {
        Ldap.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    //TODO: parametrise for test user, not real data
    @Test
    fun queryWithoutPass() {
        Ldap.queryUserLdap(Config.TEST_USER, null).assertEqualsTestUser()
    }
}

class SessionManagerIT : AuthIT() {

    @Test
    fun authenticateWithLdapUserInDb() {
        SessionManager.queryUser(Config.TEST_USER, null)
                ?: fail("Couldn't prime DB with test user ${Config.TEST_USER}")
        SessionManager.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun authenticateWithLdapUserNotInDb() {
        CouchDb.path("u${Config.TEST_USER}").deleteRev()
        SessionManager.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun queryUserInDb() {
        // TODO: add user to DB in way which does not need LDAP
        val ldapUser = Ldap.queryUserLdap(Config.TEST_USER, null)
                ?: fail("Couldn't get test user ${Config.TEST_USER} from LDAP")
        SessionManager.updateDbWithUser(ldapUser)
        SessionManager.queryUserDb(Config.TEST_USER) ?: fail("User ${Config.TEST_USER} not already in DB")

        SessionManager.queryUser(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun queryUserNotInDb() {
        CouchDb.path("u${Config.TEST_USER}").deleteRev()
        SessionManager.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun autosuggest() {
        SessionManager.autoSuggest(Config.TEST_USER, 1).getResult(20000)[0].assertEqualsTestUser()
    }

    fun isExchange(testSU: String): Boolean {
        val ldapUser = Ldap.queryUserLdap(testSU, null) ?: fail("Couldn't get test user $testSU from LDAP")
        return Config.CURRENT_EXCHANGE_GROUP in ldapUser.groups
    }

    fun addToExchangeStudent(testSU: String) {
        println("Adding")
        assertTrue(SessionManager.setExchangeStudent(testSU, true))
        assertTrue(isExchange(Config.TEST_USER))
    }

    fun addToExchangeStudentAlreadyIn(testSU: String) {
        println("Re-adding")
        assertTrue(SessionManager.setExchangeStudent(testSU, true))
        assertTrue(isExchange(Config.TEST_USER))
    }

    fun removeFromExchangeStudentAlreadyOut(testSU: String) {
        println("Re-removing")
        assertFalse(SessionManager.setExchangeStudent(testSU, false))
        assertFalse(isExchange(Config.TEST_USER))
    }

    fun removeFromExchangeStudent(testSU: String) {
        println("Removing")
        assertFalse(SessionManager.setExchangeStudent(testSU, false))
        assertFalse(isExchange(Config.TEST_USER))
    }


    @Test
    fun addAndRemoveFromExchange() {
        when (isExchange(Config.TEST_USER)) {
            true -> {
                println("Already in")
                removeFromExchangeStudent(Config.TEST_USER)
                removeFromExchangeStudentAlreadyOut(Config.TEST_USER)
                addToExchangeStudent(Config.TEST_USER)
                addToExchangeStudentAlreadyIn(Config.TEST_USER)
                println("Done")
            }
            false -> {
                println("Already out")
                addToExchangeStudent(Config.TEST_USER)
                addToExchangeStudentAlreadyIn(Config.TEST_USER)
                removeFromExchangeStudent(Config.TEST_USER)
                removeFromExchangeStudentAlreadyOut(Config.TEST_USER)
                println("Done")
            }
        }
    }
}