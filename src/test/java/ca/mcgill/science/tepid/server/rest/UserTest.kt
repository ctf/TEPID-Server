package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Test
import kotlin.test.assertTrue

class UserTest : WithLogging() {

    val endpoints: Users by lazy {
        log.error("asdf") // todo remove
        Users()
    }

    @Test
    fun configured() {
        assertTrue(endpoints.adminConfigured(), "Tepid is not configured")
    }

}