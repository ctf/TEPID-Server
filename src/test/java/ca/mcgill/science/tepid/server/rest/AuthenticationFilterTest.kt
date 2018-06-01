package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class AuthenticationFilterTest : WithLogging() {

    val endpoints: AuthenticationFilter by lazy {
        AuthenticationFilter()
    }

    @Test
    fun testGetCtfRoleNoGroups() {
        fail("NI")
    }

    @Test
    fun testGetCtfRoleAuthTypeLocalAndAdmin() {
        fail("NI")
    }

    @Test
    fun testGetCtfRoleAuthTypeLocalAndUser(){
        fail("NI")
    }

    @Test
    fun testGetCtfRoleAuthTypeNull() {
        fail("NI")
    }

    @Test
    fun testGetCtfRole() {
        fail("NI")
    }

}