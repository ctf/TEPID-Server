package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

@Disabled("NI")
class
testQueryUserLdap : Logging {

    @Test
    fun testQueryUserWithSuAndPw() {
        fail("NI")
    }

    @Test
    fun testQueryUserWithSuNoPw() {
        fail("NI")
    }

    @Test
    fun testQueryUserWithNonSuAndPw() {
        fail("NI")
    }

    @Test
    fun testQueryUserWithNonSuNoPw() {
        fail("NI")
    }

    @Test
    fun testQueryUserNullUser() {
        fail("NI")
    }
}