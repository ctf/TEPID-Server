package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UserTest : WithLogging() {

    val endpoints: Users by lazy {
        Users()
    }

    @Test
    fun configured() {
        assertTrue(endpoints.adminConfigured(), "Tepid is not configured")
    }

}