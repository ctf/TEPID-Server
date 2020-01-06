package ca.mcgill.science.tepid.server

import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.db.DbLayer
import io.mockk.spyk

object TestHelpers {
    // note that the shortUsers are the same, since they are the unique key
    fun makeDbUser(): FullUser {
        val testUser = TestHelpers.generateTestUser("db").copy(
            activeSince = 1000,
            semesters = setOf(Semester(Season.FALL, 4444)),
            studentId = 3333,
            colorPrinting = true,
            jobExpiration = 12
        )
        testUser._rev = "0001"
        testUser._id = "SU"
        return testUser
    }

    fun makeLdapUser(): FullUser {
        val testOtherUser = TestHelpers.generateTestUser("ldap").copy(
            activeSince = 9999,
            semesters = setOf(Semester(Season.FALL, 2222)),
            studentId = 1111,
            jobExpiration = 604800000,
            colorPrinting = false
        )
        testOtherUser._id = "SU"
        return testOtherUser
    }

    fun makeMergedUser(): FullUser {
        val dbUser = makeDbUser()
        val ldapUser = makeLdapUser()
        val testUser = ldapUser.copy(
            colorPrinting = dbUser.colorPrinting,
            jobExpiration = dbUser.jobExpiration,
            preferredName = dbUser.preferredName,
            semesters = ldapUser.semesters.plus(dbUser.semesters),
            nick = dbUser.nick
        )
        testUser.updateUserNameInformation()
        return testUser
    }

    fun generateTestUser(prefix: String): FullUser {
        val u = FullUser(
            displayName = prefix + "DN",
            givenName = prefix + "GN",
            lastName = prefix + "LN",
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
        u._id = "${prefix}SU"
        return u
    }

    fun makeMockDb(): DbLayer {
        return DbLayer(spyk(), spyk(), spyk(), spyk(), spyk(), spyk(), spyk())
    }
}