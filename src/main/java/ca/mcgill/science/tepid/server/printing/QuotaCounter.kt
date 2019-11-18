package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.DTO.QuotaData
import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.db.DB
import kotlin.math.max

interface IQuotaCounter {
    val nullQuotaData: QuotaData
        get() = QuotaData(0, 0, 0)

    fun getQuotaData(user: FullUser): QuotaData
}

object QuotaCounter : IQuotaCounter {

    override fun getQuotaData(user: FullUser): QuotaData {
        val shortUser = user.shortUser ?: return nullQuotaData

        val totalPrinted = DB.getTotalPrintedCount(shortUser)

        val currentSemester = Semester.current
        // TODO: incorporate summer escape into mapper
        val semesters = user.semesters
            .filter { it.season != Season.SUMMER } // we don't add quota for the summer
            .filter { it >= Semester.fall(2016) } // TEPID didn't exist before fall 2016
            .filter { it <= currentSemester } // only add quota for valid semesters

        val newMaxQuota = semesters.map { semester ->
            /*
             * The following mapper allows you to customize
             * The quota/semester
             *
             * Granted that semesters are comparable,
             * you may specify ranges (inclusive) when matching
             */

            // for NUS, which has a separate contract
            if (user.groups.contains(AdGroup("520-NUS Users")) && semester > Semester.fall(2018)) {
                return@map 1000
            }

            when {
                semester == Semester.fall(2016) -> 500 // the first semester had 500 pages only
                (semester > Semester.fall(2016) && semester < Semester.fall(2019)) -> 1000 // semesters used to add 1000 pages to the base quota
                else -> 250 // then we came to our senses
            }
        }.sum()

        val quota = max(newMaxQuota - totalPrinted, 0)

        return QuotaData(
            quota = quota,
            maxQuota = newMaxQuota,
            totalPrinted = totalPrinted
        )
    }

    private fun withCurrentSemester(user: FullUser): FullUser {
        return user.copy(semesters = user.semesters.plus(Semester.current))
    }

    /**
     * Says if the current semester is eligible for granting quota.
     * Checks that the student is in one of the User groups and that they are enrolled in courses.
     */
    private fun hasCurrentSemesterEligible(user: FullUser, registeredSemesters: Set<Semester>): Boolean {
        return (setOf<String>(USER, CTFER, ELDER).contains(user.role) && registeredSemesters.contains(Semester.current))
    }

    fun withCurrentSemesterIfEligible(user: FullUser, registeredSemesters: Set<Semester>): FullUser {
        if (hasCurrentSemesterEligible(user, registeredSemesters)) {
            return withCurrentSemester(user)
        } else {
            return user
        }
    }
}