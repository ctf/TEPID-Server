package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.deleteRev
import ca.mcgill.science.tepid.server.db.getJsonOrNull
import ca.mcgill.science.tepid.server.server.Config
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
                ?: fail("Could not prime DB with test user ${Config.TEST_USER}")
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
                ?: fail("Could get test user ${Config.TEST_USER} from LDAP")
        SessionManager.updateDbWithUser(ldapUser)
        SessionManager.queryUserDb(Config.TEST_USER) ?: fail("User ${Config.TEST_USER} not already in DB")

        SessionManager.queryUser(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun queryUserNotInDb() {
        CouchDb.path("u${Config.TEST_USER}").deleteRev()
        SessionManager.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }
}