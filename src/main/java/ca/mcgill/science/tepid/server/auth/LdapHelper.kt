package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.util.logError
import org.apache.logging.log4j.kotlin.Logging
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.naming.NamingException
import javax.naming.directory.Attribute
import javax.naming.directory.Attributes
import javax.naming.ldap.LdapContext
import javax.naming.ldap.LdapName

class LdapHelper {
    companion object : Logging {
        /**
         * Convert attribute to string list
         */
        fun AttributeToList(attribute: Attribute) = (0 until attribute.size()).map { attribute.get(it).toString() }

        /**
         * Make sure that the regex matches values located in [Semester]
         */
        private val semesterRegex: Regex by lazy { Regex("ou=(fall|winter|summer) (2[0-9]{3})[^0-9]") }

        val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")

        /**
         * Creates a blank user and attempts to retrieve as many attributes
         * as possible from the specified attributes
         */
        fun AttributesToUser(attributes: Attributes, ctx: LdapContext): FullUser {
            fun attr(name: String) = attributes.get(name)?.get()?.toString() ?: ""
            val out = FullUser(
                    displayName = attr("displayName"),
                    givenName = attr("givenName"),
                    lastName = attr("sn"),
                    shortUser = attr("sAMAccountName"),
                    longUser = attr("userPrincipalName").toLowerCase(),
                    email = attr("mail"),
                    middleName = attr("middleName"),
                    faculty = attr("department"),
                    studentId = attr("employeeID").toIntOrNull() ?: -1
            )
            out._id = "u${attr("sAMAccountName")}"
            try {
                out.activeSince = SimpleDateFormat("yyyyMMddHHmmss.SX").parse(attr("whenCreated")).time
            } catch (e: ParseException) {
            }

            fun getCn(ldapQuery: String): String {
                val dn = LdapName(ldapQuery)
                val cn = dn.get(dn.size() - 1)
                return cn.substringAfter("=")
            }

            val ldapGroups = LdapHelper.AttributeToList(attributes.get("memberOf")).mapNotNull {
                try {
                    val cn = getCn(it)
                    val groupValues = semesterRegex.find(it.toLowerCase(Locale.CANADA))?.groupValues
                    val semester = if (groupValues != null) Semester(Season(groupValues[1]), groupValues[2].toInt())
                    else null
                    cn to semester
                } catch (e: NamingException) {
                    logger.logError("error instantiating LDAP Groups", e, "user" to out, "memberOF" to attributes.get("memberOf"))
                    null
                }
            }

            val groups = mutableSetOf<AdGroup>()
            val courses = mutableSetOf<Course>()

            ldapGroups.forEach { (name, semester) ->
                if (semester == null) groups.add(AdGroup(name))
                else courses.add(Course(name, semester.season, semester.year))
            }
            out.groups = groups
            out.courses = courses
            out.semesters = courses.map { c -> c.semester() }.toSet()

            out.role = AuthenticationFilter.getCtfRole(out)

            return out
        }
    }
}