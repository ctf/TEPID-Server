package ca.mcgill.science.tepid.server.auth

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import java.util.*
import javax.naming.NamingException
import javax.naming.directory.*

object Ldap : WithLogging() {

    private val ldapManager = LdapManager()
    private val autoSuggest = AutoSuggest();

    private val ldapConnector = LdapConnector();

    private val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")

    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS


    /**
     * Retrieve a [FullUser] from ldap
     * [sam] must be a valid short user or long user
     * The resource account will be used as auth if [pw] is null
     */
    fun queryUserLdap(sam: String, pw: String?): FullUser? {
        if (!Config.LDAP_ENABLED) return null
        val auth = if (pw != null && shortUserRegex.matches(sam)) {
            log.trace("Querying user from LDAP {\"sam\":\"$sam\", \"by\":\"$sam\"}")
            sam to pw
        } else {
            log.trace("Querying user from LDAP {\"sam\":\"$sam\", \"by\":\"resource\"}")
            Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS
        }
        val user = ldapManager.queryUser(sam, auth)
        user?.updateUserNameInformation()
        return user
    }

    fun autoSuggest(like: String, limit: Int): Promise<List<FullUser>> {
        val q = Q.defer<List<FullUser>>()
        object : Thread("LDAP AutoSuggest: " + like) {
            override fun run() {
                val out = autoSuggest.autoSuggest(like, auth, limit)
                q.resolve(out)
            }
        }.start()
        return q.promise
    }

    /**
     * Returns user data, but guarantees a pass through ldap
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        log.debug("Authenticating against ldap {\"sam\":\"$sam\"}")

        val shortUser = if (sam.matches(shortUserRegex)) sam else SessionManager.queryUser(sam, null)?.shortUser
                ?: autoSuggest.autoSuggest(sam, auth, 1).getOrNull(0)?.shortUser
        if (shortUser == null) return null

        log.info("Authenticating {\"sam\":\"$sam\", \"shortUser\":\"$shortUser\"}")

        return ldapManager.queryUser(shortUser, shortUser to pw)
    }


    /**
     * Adds the supplied user to the exchange group
     *
     * @return updated status of the user; false if anything goes wrong
     */
    fun setExchangeStudent(sam: String, exchange: Boolean): Boolean {
        val longUser = sam.contains(".")
        val ldapSearchBase = Config.LDAP_SEARCH_BASE
        val searchFilter = "(&(objectClass=user)(" + (if (longUser) "userPrincipalName" else "sAMAccountName") + "=" + sam + (if (longUser) ("@" + Config.ACCOUNT_DOMAIN) else "") + "))"
        val ctx = ldapConnector.bindLdap(auth) ?: return false
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        var searchResult: SearchResult? = null
        try {
            val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
            searchResult = results.nextElement()
            results.close()
        } catch (e: Exception) {
        }

        if (searchResult == null) return false
        val cal = Calendar.getInstance()
        val userDn = searchResult.nameInNamespace
        val year = cal.get(Calendar.YEAR)
        val season = if (cal.get(Calendar.MONTH) < 8) "W" else "F"
        val groupDn = "CN=" + Config.EXCHANGE_STUDENTS_GROUP_BASE + "$year$season, " + Config.GROUPS_LOCATION
        val mods = arrayOfNulls<ModificationItem>(1)
        val mod = BasicAttribute("member", userDn)
        // todo check if we should ignore modification action if the user is already in/not in the exchange group?
        mods[0] = ModificationItem(if (exchange) DirContext.ADD_ATTRIBUTE else DirContext.REMOVE_ATTRIBUTE, mod)
        return try {
            ctx.modifyAttributes(groupDn, mods)
            log.info("${if (exchange) "Added $sam to" else "Removed $sam from"} exchange students.")
            exchange
        } catch (e: NamingException) {
            if (e.message!!.contains("LDAP: error code 53")) {
                log.warn("Error removing user from Exchange: {\"sam\":\"$sam\", \"cause\":\"not in group\")")
                false
            } else if (e.message!!.contains("LDAP: error code 68")) {
                log.warn("Error adding user from Exchange: {\"sam\":\"$sam\", \"cause\":\"already in group\")")
                true
            } else {
                log.warn("Error adding to exchange students. {\"sam\":\"$sam\", \"userDN\":\"$userDn\",\"groupDN\":\"$groupDn\", \"cause\":null}")
                e.printStackTrace()
                false
            }
        }
    }


}