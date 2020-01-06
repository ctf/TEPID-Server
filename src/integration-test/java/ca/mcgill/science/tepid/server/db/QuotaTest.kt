package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.server.UserMembershipMonitor
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue
import kotlin.test.fail

class QuotaTest : Logging {
    @Test
    @EnabledIfEnvironmentVariable(named = "LONG_TESTS", matches = "TRUE")
    fun userMembershipMonitorTest() {
        val monitor = UserMembershipMonitor()
        monitor.run()

        val u = DB.users.find(PropsLDAPTestUser.TEST_USER) ?: fail("did not retrieve test user")
        assertTrue { u.semesters.contains(Semester.current) }
    }
}