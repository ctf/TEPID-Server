package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ExchangeManagerIT : AuthIT() {
    fun isExchange(testSU: String): Boolean {
        val ldapUser = AuthenticationManager.queryUserLdap(testSU)
            ?: fail("Couldn't get test user $testSU from LDAP")
        return Config.CURRENT_EXCHANGE_GROUP in ldapUser.groups
    }

    fun addToExchangeStudent(testSU: String) {
        println("Adding")
        assertFalse(isExchange(testSU), "Precondition failed: user $testSU is already in LDAP group")
        assertTrue(ExchangeManager.setExchangeStudent(testSU, true))
        assertTrue(isExchange(testSU))
    }

    fun addToExchangeStudentAlreadyIn(testSU: String) {
        println("Re-adding")
        assertTrue(isExchange(testSU), "Precondition failed: user $testSU is not already in LDAP group")
        assertTrue(ExchangeManager.setExchangeStudent(testSU, true))
        assertTrue(isExchange(testSU))
    }

    fun removeFromExchangeStudentAlreadyOut(testSU: String) {
        println("Re-removing")
        assertFalse(
            isExchange(testSU),
            "Precondition failed: user $testSU is not already out of LDAP group"
        )
        assertFalse(ExchangeManager.setExchangeStudent(testSU, false))
        assertFalse(isExchange(testSU))
    }

    fun removeFromExchangeStudent(testSU: String) {
        println("Removing")
        assertTrue(isExchange(testSU), "Precondition failed: user $testSU is not already in LDAP group")
        assertFalse(ExchangeManager.setExchangeStudent(testSU, false))
        assertFalse(isExchange(testSU))
    }

    @Test
    fun addAndRemoveFromExchange() {
        when (isExchange(PropsLDAPTestUser.TEST_USER)) {
            true -> {
                println("Already in")
                removeFromExchangeStudent(PropsLDAPTestUser.TEST_USER)
                removeFromExchangeStudentAlreadyOut(PropsLDAPTestUser.TEST_USER)
                addToExchangeStudent(PropsLDAPTestUser.TEST_USER)
                addToExchangeStudentAlreadyIn(PropsLDAPTestUser.TEST_USER)
                println("Done")
            }
            false -> {
                println("Already out")
                addToExchangeStudent(PropsLDAPTestUser.TEST_USER)
                addToExchangeStudentAlreadyIn(PropsLDAPTestUser.TEST_USER)
                removeFromExchangeStudent(PropsLDAPTestUser.TEST_USER)
                removeFromExchangeStudentAlreadyOut(PropsLDAPTestUser.TEST_USER)
                println("Done")
            }
        }
    }
}

class AutoSuggestTest : AuthIT() {
    @Test
    fun autosuggest() {
        AutoSuggest.autoSuggest(PropsLDAPTestUser.TEST_USER, 1).getResult(20000)[0].assertEqualsTestUser()
    }
}