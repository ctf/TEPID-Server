package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertNotNull

class QuotaIT : AuthIT() {

    @Test
    @EnabledIfEnvironmentVariable(named = "LONG_TESTS", matches = "TRUE")
    fun testGetAllNeeding() {
        deleteUser()

        val startTime = System.nanoTime()
        AuthenticationManager.addAllCurrentlyEligible()
        val elapsedTime = System.nanoTime() - startTime

        println("Fetched in ${elapsedTime*1e-9}")
        assertNotNull(DB.users.getUserOrNull(PropsLDAPTestUser.TEST_USER))
    }
}
