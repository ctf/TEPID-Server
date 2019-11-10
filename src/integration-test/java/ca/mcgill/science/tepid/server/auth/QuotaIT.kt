package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.server.printing.QuotaCounter
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class QuotaIT : AuthIT() {

    @Test
    @EnabledIfEnvironmentVariable(named = "LONG_TESTS", matches = "TRUE")
    fun testGetAllNeeding() {
        deleteUser()

        val startTime = System.nanoTime()
        val gotten = QuotaCounter.getAllNeedingGranting()
        val elapsedTime = System.nanoTime() - startTime
        assertNotNull(gotten)
        assertTrue { gotten.isNotEmpty() }
        assertNotNull(gotten.find { u -> u.shortUser == PropsLDAPTestUser.TEST_USER })
        println("Fetched ${gotten.size} users in ${elapsedTime*1e-9}")
    }
}
