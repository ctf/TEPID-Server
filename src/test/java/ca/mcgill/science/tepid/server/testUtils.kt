package ca.mcgill.science.tepid.server

import ca.mcgill.science.tepid.models.data.Course
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season

fun generateTestUser(prefix:String):FullUser{
    return FullUser(displayName = prefix+"DN", givenName = prefix+"GN", lastName = prefix+"LN", shortUser = prefix+"SU", longUser = prefix+".LU@example.com", email = prefix+".EM@example.com", faculty = prefix+"Faculty", groups = listOf(prefix+"Groups"), courses = listOf(Course(prefix+"CourseName", Season.FALL, 4444)), studentId = 3333, colorPrinting = true, jobExpiration = 12)
}