package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LdapTest {

    companion object {

        @BeforeAll
        @JvmStatic
        fun before() {
            assumeTrue(PropsLDAPTestUser.TEST_USER.isNotEmpty())
            assumeTrue(PropsLDAPTestUser.TEST_PASSWORD.isNotEmpty())
            println("Running ldap tests with test user")
        }
    }

    private fun FullUser?.assertEqualsTestUser() {
        assertNotNull(this)
        println(this!!)
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
