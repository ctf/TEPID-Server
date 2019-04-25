package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ADMIN
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.LOCAL
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Test
import kotlin.test.assertEquals

class AuthenticationFilterTest : WithLogging() {

    @Test
    fun testGetCtfRoleNoGroups() {
        val user = FullUser(groups=listOf())
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ""
        assertEquals(expected, actual, "User with no groups is not given no roles")
    }

    @Test
    fun testGetCtfRoleAuthTypeLocalAndAdmin() {
        val user = FullUser(authType=LOCAL, role=ADMIN)
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ELDER
        assertEquals(expected, actual, "Local admin not given Elder privileges")
    }

    @Test
    fun testGetCtfRoleAuthTypeLocalAndUser(){
        val user = FullUser(authType=LOCAL, role=USER)
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = USER
        assertEquals(expected, actual, "Local user not given User privileges")
    }

    @Test
    fun testGetCtfRoleAuthTypeNull() {
        mockkObject(Config)
        every { Config.USERS_GROUP } returns listOf("user_group")

        val user = FullUser(authType = null, groups = listOf("user_group"))
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = USER
        assertEquals(expected, actual, "authtype null not handled as non-local")

    }

    @Test
    fun testGetCtfRoleElder() {
        val user = FullUser(groups = listOf("not_elder_test_group", "elder_test_group"), authType = "not_null") //TODO: actual auth type
        mockkObject(Config)
        every { Config.ELDERS_GROUP } returns listOf("other_elder_test_group", "elder_test_group")
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ELDER
        assertEquals(expected, actual, "Standard role not assigned to standard user")
    }

    @Test
    fun testGetCtfRoleNone() {
        val user = FullUser(groups = listOf("not_a_permitted_group", "a_different_group"), authType = "not_null") //TODO: actual auth type
        mockkObject(Config)
        every { Config.ELDERS_GROUP } returns listOf("other_elder_test_group", "elder_test_group")
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ""
        assertEquals(expected, actual, "Standard role not assigned to standard user")
    }

}