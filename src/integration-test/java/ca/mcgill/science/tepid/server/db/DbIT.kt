package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DbIT : DbTest() {

    @Test
    fun testGetCourses() {
        val u = testItems[0].copy()
        u._id = "u${u.shortUser}"
        val groups = mutableSetOf(
            AdGroup("Group0"),
            AdGroup("Group1"),
            AdGroup("Group2")
        )
        val semesters = mutableSetOf(
            Semester(Season.SUMMER, 1337),
            Semester(Season.SUMMER, 1337),
            Semester(Season.WINTER, 1337)
        )

        u.groups = groups
        u.semesters = semesters
        persist(u)
//        em.clear()

        val ri = hl.find(u.shortUser!!) ?: fail("Did not retieve user")

        assertEquals(3, ri.groups.size)
        assertEquals(2, ri.semesters.size)
    }

    @AfterEach
    fun truncateUsed() {
        println("------End Test------")

        val u = listOf(PrintJob::class.java)
        u.forEach { truncate(it) }

        deleteAllIndividually(FullUser::class.java)
    }

    companion object {

        val testItems = listOf(
            FullUser(shortUser = "USER1"),
            FullUser(shortUser = "USER2")
        )

        lateinit var hc: HibernateCrud<FullUser, String?>
        lateinit var hl: HibernateUserLayer

        @JvmStatic
        @BeforeAll
        fun initHelper() {
            hc = HibernateCrud(emf, FullUser::class.java)
            hl = HibernateUserLayer(hc)
            println("======Begin Tests======")
        }
    }
}