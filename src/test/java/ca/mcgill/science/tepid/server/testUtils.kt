package ca.mcgill.science.tepid.server

import ca.mcgill.science.tepid.models.data.Course
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season



object UserFactory {
    //note that the shortUsers are the same, since they are the unique key
    fun makeDbUser(): FullUser {
        val testUser = FullUser(displayName = "dbDN", givenName = "dbGN", lastName = "dbLN", shortUser = "SU", longUser = "db.lu@example.com", email = "db.EM@example.com", faculty = "dbFaculty", groups = listOf("dbGroups"), courses = listOf(Course("dbCourseName", Season.FALL, 4444)), studentId = 3333, colorPrinting = true, jobExpiration = 12, nick = "dbNick", preferredName = listOf("dbPreferredName"))
        testUser._id = "0000"
        testUser._rev = "0001"
        testUser.activeSince = 1000
        return testUser
    }
    fun makeLdapUser(): FullUser {
        val testOtherUser = FullUser(displayName = "ldapDN", givenName = "ldapGN", lastName = "ldapLN", shortUser = "SU", longUser = "ldap.lu@example.com", email = "ldap.EM@example.com", faculty = "ldapFaculty", groups = listOf("ldapGroups"), courses = listOf(Course("ldapCourseName", Season.FALL, 2222)), studentId = 1111, nick = "ldapNick", preferredName = listOf("ldapPreferredName"))
        testOtherUser.activeSince = 9999
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

    fun generateTestUser(prefix:String):FullUser{
        return FullUser(displayName = prefix+"DN", givenName = prefix+"GN", lastName = prefix+"LN", shortUser = prefix+"SU", longUser = prefix+".LU@example.com", email = prefix+".EM@example.com", faculty = prefix+"Faculty", groups = listOf(prefix+"Groups"), courses = listOf(Course(prefix+"CourseName", Season.FALL, 4444)), studentId = 3333, colorPrinting = true, jobExpiration = 12)
    }
}