package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.auth.LdapConnector
import ca.mcgill.science.tepid.server.server.Config

object QuotaCounter {

    private val ldapConnector = LdapConnector()

    fun addSemester(user: FullUser, semester: Semester = Semester.current) {
        user.semesters = user.semesters.plus(semester)
    }

    /**
     * Says if the current semester is eligible for granting quota.
     * Checks that the student is in one of the User groups and that they are enrolled in courses.
     */
    fun hasCurrentSemesterEligible(user: FullUser, registeredSemesters: Set<Semester>): Boolean {
        return (Config.USERS_GROUP.any(user.groups::contains) && registeredSemesters.contains(Semester.current))
    }

    fun getAllCurrentlyEligible(): Set<FullUser>? {
        val filter = "(|${Config.USERS_GROUP.map { "(memberOf:1.2.840.113556.1.4.1941:=cn=${it.name},${Config.GROUPS_LOCATION})" }.joinToString()})"
        return ldapConnector.executeSearch(filter, Long.MAX_VALUE)
    }
}