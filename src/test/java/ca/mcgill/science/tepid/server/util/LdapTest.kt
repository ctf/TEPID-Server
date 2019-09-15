package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LdapTest {

    companion object {

        @BeforeClass
        @JvmStatic
        fun before() {
            Assume.assumeTrue(PropsLDAPTestUser.TEST_USER.isNotEmpty())
            Assume.assumeTrue(PropsLDAPTestUser.TEST_PASSWORD.isNotEmpty())
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

@Ignore("NI")
class testQueryUserLdap : WithLogging() {

    @Before
    fun initTest() {
    }

    @After
    fun tearTest() {
    }

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