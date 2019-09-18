package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.ITBase
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.test.assertTrue

class UserIT : ITBase(), Loggable by WithLogging() {

    val endpoints: Users by lazy {
        Users()
    }

    @Test
    fun configured() {
        assertTrue(endpoints.adminConfigured(), "Tepid is not configured")
    }

    @Test
    fun testAutoSuggest() {
        val u = server.testApi.queryUsers(server.testUser, 10).execute().body() ?: fail("derp")

        assertTrue { u.size > 0 }
        assertTrue { u.any { it.shortUser == server.testUser } }
    }
}