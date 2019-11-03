package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Semester

object QuotaCounter {

    fun addSemester(user: FullUser, semester: Semester=Semester.current) {
        user.semesters = user.semesters.plus(semester)
    }
}