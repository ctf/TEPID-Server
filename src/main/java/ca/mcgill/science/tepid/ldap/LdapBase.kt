package ca.mcgill.science.tepid.ldap

import ca.mcgill.science.tepid.models.data.Course
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.utils.WithLogging
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.naming.Context.*
import javax.naming.NamingException
import javax.naming.directory.Attributes
import javax.naming.directory.SearchControls
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext

/**
 * Collection of functions that can be exposed
 * Use this to hide unneeded functions
 */
interface LdapContract {
    fun queryUser(username: String?, auth: Pair<String, String>): FullUser?
    fun autoSuggest(like: String, auth: Pair<String, String>, limit: Int): List<FullUser>
}

open class LdapBase : LdapContract, LdapHelperContract by LdapHelperDelegate() {

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
        val ldapSearchBase = ***REMOVED***
        val searchName = if (username.contains(".")) "userPrincipalName=$username@mail.mcgill.ca" else "sAMAccountName=$username"
        val searchFilter = "(&(objectClass=user)($searchName))"
        val ctx = bindLdap(auth) ?: return null
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
    protected fun Attributes.toUser(ctx: LdapContext): FullUser {
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
        try {
            out.activeSince = SimpleDateFormat("yyyyMMddHHmmss.SX").parse(attr("whenCreated")).time
        } catch (e: ParseException) {

        }

        val members = get("memberOf")?.toList()?.mapNotNull {
            try {
                val cn = ctx.getAttributes(it, arrayOf("CN"))?.get("CN")?.get()?.toString()
                val groupValues = semesterRegex.find(it.toLowerCase(Locale.CANADA))?.groupValues
                val semester = if (groupValues != null) Semester(Season(groupValues[1]), groupValues[2].toInt())
                else null
                cn to semester
            } catch (e: NamingException) {
                null
            }
        }

        val groups = mutableListOf<String>()

        val courses = mutableListOf<Course>()

        members?.forEach { (name, semester) ->
            if (name == null) return@forEach
            if (semester == null) groups.add(name)
            else courses.add(Course(name, semester.season, semester.year))
        }

        out.groups = groups
        out.courses = courses

        return out
    }

    /**
     * Defines the environment necessary for [InitialLdapContext]
     */
    fun createAuthMap(user: String, password: String) = Hashtable<String, String>().apply {
        put(SECURITY_AUTHENTICATION, "simple")
        put(INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        put(PROVIDER_URL, ***REMOVED***)
        put(SECURITY_PRINCIPAL, "***REMOVED***\\$user")
        put(SECURITY_CREDENTIALS, password)
        put("com.sun.jndi.ldap.read.timeout", "5000")
        put("com.sun.jndi.ldap.connect.timeout", "500")
    }

    fun bindLdap(auth: Pair<String, String>) = bindLdap(auth.first, auth.second)

    /**
     * Create [LdapContext] for given credentials
     */
    fun bindLdap(user: String, password: String): LdapContext? {
        try {
            val auth = createAuthMap(user, password)
            return InitialLdapContext(auth, null)
        } catch (e: Exception) {
            log.error("Failed to bind to LDAP for $user", e)
            return null
        }
    }

    override fun autoSuggest(like: String, auth: Pair<String, String>, limit: Int): List<FullUser> {
        try {
            val ldapSearchBase = ***REMOVED***
            val searchFilter = "(&(objectClass=user)(|(userPrincipalName=$like*)(samaccountname=$like*)))"
            val ctx = bindLdap(auth) ?: return emptyList()
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
