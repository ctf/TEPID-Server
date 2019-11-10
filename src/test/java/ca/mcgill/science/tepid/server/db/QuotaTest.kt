package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.UserFactory
import ca.mcgill.science.tepid.server.server.Config
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotaTest {
    @Test
    fun getAlreadyGrantedUsers() {
        val u = UserFactory.generateTestUser("quota").copy(semesters = setOf(Semester.current))
        DB.putUser(u)

        val db = HibernateQuotaLayer(Config.emf!!)
        val result = db.getAlreadyGrantedUsers(setOf(u.getId()), Semester.current)
        assertTrue { result.isNotEmpty() }
        assertEquals(result.first(), u.getId())
    }
}