package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.TestHelpers
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.DbLayer
import ca.mcgill.science.tepid.server.db.DbQuotaLayer
import ca.mcgill.science.tepid.server.db.createDb
import ca.mcgill.science.tepid.server.server.Config
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.Ignore
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuotaTest : Logging {

    val s2019f = Semester(Season.FALL, 2019)
    val s2018s = Semester(Season.SUMMER, 2018)
    val s1066f = Semester(Season.FALL, 1066)
    val s2016f = Semester(Season.FALL, 2016)
    val s2018f = Semester(Season.FALL, 2018)
    val s2018w = Semester(Season.WINTER, 2018)
    val s2018w0 = Semester(Season.WINTER, 2018)

    private fun userGetQuotaTest(tailoredUser: FullUser, expected: Int, message: String) {
        val actual = QuotaCounter.getQuotaData(tailoredUser).quota
        assertEquals(expected, actual, message)
    }

    private fun userGetQuotaTest(tailoredUser: FullUser, tailoredUserRole: String, expected: Int, message: String) {
        val u = tailoredUser.copy(role = tailoredUserRole, nick = "tailoredUser")
        u._id = "tailoredUser"
        userGetQuotaTest(u, expected, message)
    }

    private fun setPrintedPages(printedPages: Int) {
        every {
            mockDbQ.getTotalPrintedCount(ofType(String::class))
        } returns printedPages
    }

    @Test
    fun testGetQuotaQueriedUserNoRole() {
        userGetQuotaTest(FullUser(), "", 0, "Null user is not assigned 0 quota")
    }

    @Test
    fun testGetQuotaElder() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = ELDER, semesters = setOf(s2019f)),
            ELDER,
            250,
            "Elder is not given correct quota"
        )
    }

    @Test
    fun testGetQuotaCTFer() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = CTFER, semesters = setOf(s2019f)),
            CTFER,
            250,
            "CTFER is not given correct quota"
        )
    }

    @Test
    fun testGetQuotaNUS() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2019f), groups = setOf(AdGroup("520-NUS Users"))),
            CTFER,
            1000,
            "NUS is not given correct quota"
        )
    }

    @Test
    fun testGetQuotaUserIgnoreSummerSemester() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role = USER, semesters = setOf(s2018s)), USER, 0, "Summer gives quota")
    }

    @Test
    fun testGetQuotaUserSemesterPre2016() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role = USER, semesters = setOf(s1066f)), USER, 0, "Ancient semester gives quota")
    }

    @Test
    fun testGetQuotaUserSemester2016F() {
        setPrintedPages(0)
        userGetQuotaTest(FullUser(role = USER, semesters = setOf(s2016f)), USER, 500, "500 pages not given for 2016F")
    }

    @Test
    fun testGetQuotaUserSemesterPost2016F() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018f)),
            USER,
            1000,
            "1000 pages not given for semester"
        )
    }

    @Test
    fun testGetQuotaUserSemesterPost2019F() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2019f)),
            USER,
            250,
            "250 pages not given for semester post 2019f"
        )
    }

    @Test
    fun testGetQuotaUserSpanMultipleSemesters() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018f, s2018w)),
            USER,
            2000,
            "multiple semesters not counted"
        )
    }

    @Test
    fun testGetQuotaTotalPrintedSubtracted() {
        setPrintedPages(300)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018f)),
            USER,
            700,
            "Printed pages not subtracted (you had one job)"
        )
    }

    // Tests that if there are multiple courses in the same semester they only contribute as one semester
    @Test
    fun testGetQuotaMultipleCoursesReduced() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = USER, semesters = setOf(s2018w0, s2018w)),
            USER,
            1000,
            "multiple courses in same semester counted as other semesters"
        )
    }

    @Test
    fun testGetQuotaNoCurrentRole() {
        setPrintedPages(0)
        userGetQuotaTest(
            FullUser(role = "", semesters = setOf(s2018w)),
            "",
            1000,
            "former user is not granted pages"
        )
    }

    companion object {
        lateinit var mockDb: DbLayer
        lateinit var mockDbQ: DbQuotaLayer

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockDbQ = spyk()
            QuotaCounter.dbQuotaLayer = mockDbQ
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
            DB = createDb()
        }
    }
}

class QuotaEligibilityTest : Logging {

    @Test
    fun testEligibilityElder() {
        QuotaCounter.hasCurrentSemesterEligible(TestHelpers.generateTestUser("tu").copy(groups = setOf(quotaGroup), role = ELDER))
    }

    @Test
    fun testEligibilityUserInGroup() {
        every { Config.QUOTA_GROUP } returns listOf(quotaGroup)
        QuotaCounter.hasCurrentSemesterEligible(TestHelpers.generateTestUser("tu").copy(groups = setOf(quotaGroup), role = USER))
    }

    @Ignore("I'm too tired to deal with this as part of #135. ref #151")
    @Test
    fun testEligibilityTransitivelyInGroup() {
        // not sure how I would test this...
        // maybe more appropriate in whatever we use for transitive membership handling?
    }

    @Test
    fun testEligibilityNotInGroup() {
        every { Config.QUOTA_GROUP } returns listOf(quotaGroup)
        QuotaCounter.hasCurrentSemesterEligible(TestHelpers.generateTestUser("tu").copy(groups = setOf(), role = USER))
    }

    companion object : Logging {
        val quotaGroup = AdGroup("quotaGroup")

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}