package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Created by Allan Wang on 2017-10-31.
 */
class LdapTest {

    companion object {

        @BeforeClass
        @JvmStatic
        fun before() {
            Assume.assumeTrue(Config.LDAP_ENABLED)
            Assume.assumeTrue(Config.TEST_USER.isNotEmpty())
            Assume.assumeTrue(Config.TEST_PASSWORD.isNotEmpty())
            println("Running ldap tests with test user")
        }
    }

    private fun FullUser?.assertEqualsTestUser() {
        assertNotNull(this)
        println(this!!)
        assertEquals(Config.TEST_USER, shortUser, "Short user mismatch. Perhaps you passed in the long user in your test?")
    }

    @Test
    fun authenticate() {
        SessionManager.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun query() {
        SessionManager.queryUser(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun queryWithoutPass() {
        Ldap.queryUser(Config.TEST_USER, null).assertEqualsTestUser()
    }

    @Test
    fun queryById() {
        val id = 260674302
        val user = Ldap.queryUser(id.toString(), null)
        assertNotNull(user)
        println(user!!)
        assertEquals(id, user.studentId)
    }

    @Test
    fun test() {
        val id = ***REMOVED***
        val user = Ldap.queryUser(id.toString(), null)
        assertNotNull(user)
        println(user!!)
        assertEquals(id, user.studentId)
    }
}
