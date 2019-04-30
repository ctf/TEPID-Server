package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DbIT : DbTest() {


    @Test
    fun testGetCourses(){
        val u = testItems[0].copy()
        u._id = "u${u.shortUser}"
        val groups = mutableSetOf(
                AdGroup("Group0"),
                AdGroup("Group1"),
                AdGroup("Group2")
        )
        val courses = mutableSetOf(
                Course("course0", Season.SUMMER, 1337),
                Course("course1", Season.SUMMER, 1337),
                Course("course2", Season.SUMMER, 1337)
        )

        u.groups = groups
        u.courses = courses
        persist(u)
//        em.clear()

        val ri = hl.getUserOrNull(u.shortUser!!) ?: fail("Did not retieve user")


        assertEquals(3, ri.groups.size)
        assertEquals(3, ri.courses.size)
    }

    @AfterEach
    fun truncateUsed(){
        println("------End Test------")

        val u = listOf(PrintJob::class.java)
        u.forEach { truncate(it) }

        deleteAllIndividually(FullUser::class.java)
    }

    companion object {

        val testItems  = listOf(
                FullUser(shortUser = "USER1"),
                FullUser(shortUser = "USER2")
        )

        lateinit var hc: HibernateCrud<FullUser, String?>
        lateinit var hl: HibernateUserLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            emf = HibernateDbLayer.makeEntityManagerFactory("tepid-pu")

            hc = HibernateCrud(emf, FullUser::class.java)
            hl = HibernateUserLayer(hc)
            println("======Begin Tests======")
        }
    }
}