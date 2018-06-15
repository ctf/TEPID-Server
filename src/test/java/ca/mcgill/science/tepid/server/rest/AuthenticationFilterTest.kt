package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.*
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.util.Config
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import io.mockk.*

class AuthenticationFilterTest : WithLogging() {

    val endpoints: AuthenticationFilter by lazy {
        AuthenticationFilter()
    }

    @Test
    fun testGetCtfRoleNoGroups() {
        val user = FullUser(groups= listOf())
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ""
        assertEquals(expected, actual, "User with no groups is not given no roles")
    }

    @Test
    fun testGetCtfRoleAuthTypeLocalAndAdmin() {
        val user = FullUser(authType = LOCAL, role= ADMIN)
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ELDER
        assertEquals(expected, actual, "Local admin not given Elder privileges")
    }

    @Test
    fun testGetCtfRoleAuthTypeLocalAndUser(){
        val user = FullUser(authType = LOCAL, role= USER)
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = USER
        assertEquals(expected, actual, "Local user not given User privileges")
    }

    @Test
    fun testGetCtfRoleAuthTypeNull() {
        objectMockk(Config).mock()
        every { Config.USERS_GROUP } returns listOf("user_group")

        val user = FullUser(authType = null, groups = listOf("user_group"))
        val actual = AuthenticationFilter.getCtfRole(user)
        var expected = CTFER


    }

    @Test
    fun testGetCtfRoleElder() {
        val user = FullUser(groups = listOf("not_elder_test_group", "elder_test_group"), authType = "not_null") //TODO: actual auth type
        objectMockk(Config).mock()
        every { Config.ELDERS_GROUP } returns listOf("other_elder_test_group", "elder_test_group")
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ELDER
        assertEquals(expected, actual, "Standard role not assigned to standard user")
    }

    @Test
    fun testGetCtfRoleNone() {
        val user = FullUser(groups = listOf("not_a_permitted_group", "a_different_group"), authType = "not_null") //TODO: actual auth type
        objectMockk(Config).mock()
        every { Config.ELDERS_GROUP } returns listOf("other_elder_test_group", "elder_test_group")
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ""
        assertEquals(expected, actual, "Standard role not assigned to standard user")
    }

}