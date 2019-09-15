package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.AdGroup
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
        val user = FullUser(groups=setOf())
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ""
        assertEquals(expected, actual, "User with no groups is not given no roles")
    }

    @Test
    fun testGetCtfRoleAuthTypeNull() {
        mockkObject(Config)
        every { Config.USERS_GROUP } returns listOf(AdGroup("user_group"))

        val user = FullUser(groups = setOf(AdGroup("user_group")))
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = USER
        assertEquals(expected, actual, "authtype null not handled as non-local")

    }

    @Test
    fun testGetCtfRoleElder() {
        val user = FullUser(groups = setOf(AdGroup("not_elder_test_group"), AdGroup("elder_test_group")))
        mockkObject(Config)
        every { Config.ELDERS_GROUP } returns listOf(AdGroup("other_elder_test_group"), AdGroup("elder_test_group"))
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ELDER
        assertEquals(expected, actual, "Standard role not assigned to standard user")
    }

    @Test
    fun testGetCtfRoleNone() {
        val user = FullUser(groups = setOf(AdGroup("not_a_permitted_group"), AdGroup("a_different_group"))) 
        mockkObject(Config)
        every { Config.ELDERS_GROUP } returns listOf(AdGroup("other_elder_test_group"), AdGroup("elder_test_group"))
        val actual = AuthenticationFilter.getCtfRole(user)
        val expected = ""
        assertEquals(expected, actual, "Standard role not assigned to standard user")
    }

}