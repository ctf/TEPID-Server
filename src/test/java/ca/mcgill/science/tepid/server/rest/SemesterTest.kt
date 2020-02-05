package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.db.DbUserLayer
import ca.mcgill.science.tepid.server.util.TepidException
import ca.mcgill.science.tepid.server.util.getSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.ws.rs.container.ContainerRequestContext
import kotlin.test.assertTrue

class SemesterTest : Logging {

    val e: Semesters
    val testSemesters = listOf(Semester(Season.FALL, 2020), Semester(Season.FALL, 2019), Semester(Season.WINTER, 2019))
    val tu: FullUser = FullUser(_id = "semesterTestUser", role = "user", semesters = testSemesters.toSet())
    val qu: FullUser = FullUser(_id = "queryingUser")
    val mockDB: DbUserLayer

    lateinit var rc: ContainerRequestContext

    init {
        e = Semesters(tu._id!!)
        mockDB = mockk<DbUserLayer>()
        e.db = mockDB
    }

    fun mockSession(role: String) {
        val session = FullSession(role, qu)

        rc = mockk<ContainerRequestContext>()
        mockkStatic("ca.mcgill.science.tepid.server.util.UtilsKt")
        every {
            rc.getSession()
        } returns session
    }

    fun mockUserQuery() {
        every { mockDB.read(tu._id) } returns tu
        every { mockDB.update(any()) } returnsArgument (0)
    }

    @Test
    fun testRemoveSemester() {
        mockUserQuery()
        mockSession(ELDER)

        e.removeSemester(testSemesters[0], rc)

        val expected = tu.copy(semesters = setOf(testSemesters[1], testSemesters[2]))
        verify { mockDB.update(expected) }
    }

    @Test
    fun testAddSemester() {
        mockUserQuery()
        mockSession(ELDER)

        val addedSemester = Semester(Season.SUMMER, 2019)
        e.addSemester(addedSemester, rc)

        val expected = tu.copy(semesters = setOf(testSemesters[0], testSemesters[1], testSemesters[2], addedSemester))
        verify { mockDB.update(expected) }
    }

    @Test
    fun testBlockOtherUser() {
        mockUserQuery()
        mockSession(USER)

        val e = assertThrows<TepidException> { e.get(rc) }
        assertTrue { e.message!!.contains("403") }
    }
}