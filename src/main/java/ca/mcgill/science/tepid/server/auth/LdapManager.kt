package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.naming.NamingException
import javax.naming.directory.Attributes
import javax.naming.directory.SearchControls
import javax.naming.ldap.LdapContext
import javax.naming.ldap.LdapName

/**
 * Collection of functions that can be exposed
 * Use this to hide unneeded functions
 */
interface LdapContract {
    fun queryUser(username: String?, auth: Pair<String, String>): FullUser?
    fun autoSuggest(like: String, auth: Pair<String, String>, limit: Int): List<FullUser>
}

open class LdapManager : LdapContract {

    val ldapConnector = LdapConnector();

    private companion object : WithLogging() {

        /**
         * Make sure that the regex matches values located in [Semester]
         */
        private val semesterRegex: Regex by lazy { Regex("ou=(fall|winter|summer) (2[0-9]{3})[^0-9]") }

    }

    /**
     * Queries [username] (short user or long user)
     * with [auth] credentials (username to password).
     * Resulting user is nonnull if it exists
     *
     * Note that [auth] may use different credentials than the [username] in question.
     * However, if a different auth is provided (eg from our science account),
     * the studentId cannot be queried
     */
    override fun queryUser(username: String?, auth: Pair<String, String>): FullUser? {
        if (username == null) return null
        val ldapSearchBase = Config.LDAP_SEARCH_BASE
        val searchName = if (username.contains(".")) "userPrincipalName=$username${Config.ACCOUNT_DOMAIN}" else "sAMAccountName=$username"
        val searchFilter = "(&(objectClass=user)($searchName))"
        val ctx = ldapConnector.bindLdap(auth) ?: return null
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
        val searchResult = results.nextElement()
        results.close()
        val user = searchResult?.attributes?.toUser(ctx)
        ctx.close()
        return user
    }

    /**
     * Creates a blank user and attempts to retrieve as many attributes
     * as possible from the specified attributes
     *
     * Note that when converting
     */
    private fun Attributes.toUser(ctx: LdapContext): FullUser {
        fun attr(name: String) = get(name)?.get()?.toString() ?: ""
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

        fun getCn (ldapQuery:String): String {
            val dn = LdapName(ldapQuery)
            val cn = dn.get(dn.size()-1)
            return cn.substringAfter("=")
        }
        val ldapGroups = LdapHelper.AttributeToList(get("memberOf")).mapNotNull {
            try {
                val cn = getCn(it)
                val groupValues = semesterRegex.find(it.toLowerCase(Locale.CANADA))?.groupValues
                val semester = if (groupValues != null) Semester(Season(groupValues[1]), groupValues[2].toInt())
                else null
                cn to semester
            } catch (e: NamingException) {
                log.warn("Error instantiating LDAP Groups {\"user\":\"$out\", \"memberOF\":\"${get("memberOf")}\"}: $e")
                null
            }
        }

        val groups = mutableSetOf<AdGroup>()
        val courses = mutableSetOf<Course>()

        ldapGroups?.forEach { (name, semester) ->
            if (semester == null) groups.add(AdGroup(name))
            else courses.add(Course(name, semester.season, semester.year))
        }
        out.groups = groups
        out.courses = courses

        out.role = AuthenticationFilter.getCtfRole(out)

        return out
    }

    override fun autoSuggest(like: String, auth: Pair<String, String>, limit: Int): List<FullUser> {
        try {
            val ldapSearchBase = Config.LDAP_SEARCH_BASE
            val searchFilter = "(&(objectClass=user)(|(userPrincipalName=$like*)(samaccountname=$like*)))"
            val ctx = ldapConnector.bindLdap(auth) ?: return emptyList()
            val searchControls = SearchControls()
            searchControls.searchScope = SearchControls.SUBTREE_SCOPE
            val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
            val out = mutableListOf<FullUser>()
            var res = 0
            val iter = results.iterator()
            while (iter.hasNext() && res++ < limit) {
                val user = iter.next().attributes.toUser(ctx)
                if (user.longUser?.split("@")?.getOrNull(0)?.indexOf(".") ?: -1 > 0)
                    out.add(user)
            }
            //todo update; a crash here will lead to the contents not closing
            results.close()
            ctx.close()
            return out
        } catch (ne: NamingException) {
            log.error("Could not get autosuggest", ne)
            return emptyList()
        }
    }
}
