package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.ldap.LdapManager
import ca.mcgill.science.tepid.ldap.LdapHelperContract
import ca.mcgill.science.tepid.ldap.LdapHelperDelegate
import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import javax.naming.NamingException
import javax.naming.directory.*

object Ldap : WithLogging(), LdapHelperContract by LdapHelperDelegate() {

    private val ldap = LdapManager()

    private val numRegex = Regex("[0-9]+")
    private val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")

    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Adds information relating to the name of a student to a FullUser [user]
     */
    private fun updateUserNameInformation(user: FullUser?) {
        user ?: return
        user.salutation = if (user.nick == null)
            if (!user.preferredName.isEmpty()) user.preferredName[user.preferredName.size - 1]
            else user.givenName else user.nick
        if (!user.preferredName.isEmpty())
            user.realName = user.preferredName.asReversed().joinToString(" ")
        else
            user.realName = "${user.givenName} ${user.lastName}"
    }

    /**
     * Retrieve a [FullUser] from ldap
     * [sam] must be a valid short user or long user
     * The resource account will be used as auth if [pw] is null
     */
    fun queryUserLdap(sam: String, pw: String?): FullUser? {
        if (!Config.LDAP_ENABLED) return null
        val auth = if (pw != null && shortUserRegex.matches(sam)) {
            log.trace("Querying by owner $sam")
            sam to pw
        } else {
            log.trace("Querying by resource")
            Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS
        }
        val user = ldap.queryUser(sam, auth)
        updateUserNameInformation(user)
        return user
    }

    @JvmStatic
    fun autoSuggest(like: String, limit: Int): Promise<List<FullUser>> {
        val q = Q.defer<List<FullUser>>()
        if (!Config.LDAP_ENABLED) {
            q.reject("LDAP disabled in source")
            return q.promise
        }
        object : Thread("LDAP AutoSuggest: " + like) {
            override fun run() {
                try {
                    val out = ldap.autoSuggest(like, auth, limit)
                    q.resolve(out)
                } catch (ne: NamingException) {
                    q.reject("Could not get autosuggest", ne)
                }
            }
        }.start()
        return q.promise
    }

    /**
     * Returns user data, but guarantees a pass through ldap
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        if (sam == "tepidtest") {
            log.debug("Tepid test received with password $pw")
            return null
        }
        log.debug("Authenticating $sam against ldap")

        var shortUser = if (sam.matches(shortUserRegex)) sam else SessionManager.queryUser(sam, null)?.shortUser
        if (shortUser==null) {
            shortUser = ldap.autoSuggest(sam, auth, 1).getOrNull(0)?.shortUser
        }
        if (shortUser==null) return null


        log.info("Authenticating $shortUser")

        return ldap.queryUser(shortUser, shortUser to pw)
    }


    fun setExchangeStudent(sam: String, exchange: Boolean) {
        val longUser = sam.contains(".")
        val ldapSearchBase = Config.LDAP_SEARCH_BASE
        val searchFilter = "(&(objectClass=user)(" + (if (longUser) "userPrincipalName" else "sAMAccountName") + "=" + sam + (if (longUser) ("@" + Config.ACCOUNT_DOMAIN) else "") + "))"
        val ctx = ldap.bindLdap(auth) ?: return
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        var searchResult: SearchResult? = null
        try {
            val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
            searchResult = results.nextElement()
            results.close()
        } catch (e: Exception) {
        }

        if (searchResult == null) return
        val cal = Calendar.getInstance()
        val userDn = searchResult.nameInNamespace
        val year = cal.get(Calendar.YEAR)
        val season = if (cal.get(Calendar.MONTH) < 8) "W" else "F"
        val groupDn = "CN=" + Config.EXCHANGE_STUDENTS_GROUP_BASE + "$year$season,"+ Config.GROUPS_LOCATION
        val mods = arrayOfNulls<ModificationItem>(1)
        val mod = BasicAttribute("member", userDn)
        mods[0] = ModificationItem(if (exchange) DirContext.ADD_ATTRIBUTE else DirContext.REMOVE_ATTRIBUTE, mod)
        try {
            ctx.modifyAttributes(groupDn, mods)
            log.info("Added {} to exchange students.", sam)
        } catch (e: NamingException) {
            log.warn("Error adding {} to exchange students.", sam)
            e.printStackTrace()
        }

    }


}