package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun authenticate() {
        Ldap.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun authenticateWithCache() {
        SessionManager.authenticate(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }

    @Test
    fun query() {
        SessionManager.queryUser(Config.TEST_USER, Config.TEST_PASSWORD).assertEqualsTestUser()
    }


    @Test
    fun queryWithoutPass() {
        Ldap.queryUserLdap(Config.TEST_USER, null).assertEqualsTestUser()
    }
//
//    @Test
//    fun queryById() {
//        val id = ***REMOVED***
//        val user = Ldap.queryUser(id.toString(), null)
//        user.assertValidUser()
//        println(user!!.getSemesters())
//        assertEquals(id, user.studentId)
//    }
}
