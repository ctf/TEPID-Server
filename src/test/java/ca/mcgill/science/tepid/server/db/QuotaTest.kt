package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuotaTest : Logging {
    @Test
    fun getAlreadyGrantedUsers() {
        val u = UserFactory.generateTestUser("quota").copy(semesters = setOf(Semester.current))
        DB.putUser(u)
        logger.info { logMessage("put user", "_id" to u._id) }

        val db = HibernateQuotaLayer(Config.emf!!)
        val result = db.getAlreadyGrantedUsers(setOf(u.getId()), Semester.current)

        logger.info { logMessage("got users, validating") }
        assertTrue { result.isNotEmpty() }
        assertNotNull("users does not contain our user") { result.find { t -> t == u._id } }
    }
}