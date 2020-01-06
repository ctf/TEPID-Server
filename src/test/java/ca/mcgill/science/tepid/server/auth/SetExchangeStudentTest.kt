package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.server.TestHelpers
import ca.mcgill.science.tepid.server.server.Config
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SetExchangeStudentTest {

    @Test
    fun testSetExchangeStudentLdapEnabled() {
        ExchangeManager.setExchangeStudent(testSam, true)

        val targetUser = AuthenticationManager.mergeUsers(
            TestHelpers.makeLdapUser(),
            TestHelpers.makeDbUser()
        )
        verify {
            AuthenticationManager.refreshUser(
                targetUser.shortUser!!
            )
        }
        verify { ExchangeManager.setExchangeStudentLdap(testSam, true) }
    }

    companion object {
        val testSam = "SU"

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Ldap)
            mockkObject(ExchangeManager)
            every {
                ExchangeManager.setExchangeStudentLdap(
                    any(),
                    any()
                )
            } returns true

            mockkObject(AuthenticationManager)
            every {
                AuthenticationManager.refreshUser(testSam)
            } returns TestHelpers.makeDbUser()
            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}