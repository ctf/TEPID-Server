package ca.mcgill.science.tepid.server

import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester

object UserFactory {
    // note that the shortUsers are the same, since they are the unique key
    fun makeDbUser(SU: String = "SU"): FullUser {
        val testUser = UserFactory.generateTestUser("db").copy(
            activeSince = 1000,
            shortUser = SU,
            semesters = setOf(Semester(Season.FALL, 4444)),
            studentId = 3333,
            colorPrinting = true,
            jobExpiration = 12
        )
        testUser._id = "0000"
        testUser._rev = "0001"
        return testUser
    }

    fun makeLdapUser(SU: String = "SU"): FullUser {
        val testOtherUser = UserFactory.generateTestUser("ldap").copy(
            activeSince = 9999,
            shortUser = SU,
            semesters = setOf(Semester(Season.FALL, 2222)),
            studentId = 1111,
            jobExpiration = 604800000,
            colorPrinting = false
        )
        return testOtherUser
    }

    fun makeMergedUser(): FullUser {
        val dbUser = makeDbUser()
        val testUser = makeLdapUser().copy(
            colorPrinting = dbUser.colorPrinting,
            jobExpiration = dbUser.jobExpiration,
            preferredName = dbUser.preferredName,
            nick = dbUser.nick
        )
        testUser.updateUserNameInformation()
        return testUser
    }

    fun generateTestUser(prefix: String): FullUser {
        return FullUser(
            displayName = prefix + "DN",
            givenName = prefix + "GN",
            lastName = prefix + "LN",
            shortUser = "${prefix}SU",
            longUser = "$prefix.LU@example.com",
            email = "$prefix.EM@example.com",
            faculty = "${prefix}Faculty",
            groups = setOf(AdGroup("${prefix}Groups")),
            semesters = setOf(Semester(Season.FALL, 4444)),
            studentId = 3333,
            colorPrinting = true,
            jobExpiration = 12,
            nick = prefix + "Nick",
            preferredName = prefix + "PreferredName"
        )
    }
}