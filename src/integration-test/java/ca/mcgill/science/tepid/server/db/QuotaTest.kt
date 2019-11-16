package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.UserMembershipMonitor
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuotaTest : Logging {
// So this is here because H2 has a problem with the part of this query which matches the semester :(
    @Test
    fun getAlreadyGrantedUsers() {
        val u = UserFactory.generateTestUser("quota").copy(semesters = setOf(Semester.current))
        DB.putUser(u)
        logger.info { logMessage("put user", "_id" to u._id) }

        val db = HibernateQuotaLayer(Config.emf!!)
        val result = db.getAlreadyGrantedUsers(setOf(u.getId(), "utest"), Semester.current)

        logger.info { logMessage("got users, validating") }
        assertTrue { result.isNotEmpty() }
        assertNotNull("users does not contain our user") { result.find { t -> t == u._id } }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LONG_TESTS", matches = "TRUE")
    fun userMembershipMonitorTest() {
        val monitor = UserMembershipMonitor()
        monitor.run()
    }
}