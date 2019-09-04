package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import javax.naming.directory.SearchControls

/**
 * Collection of functions that can be exposed
 * Use this to hide unneeded functions
 */

open class LdapManager {

    private val ldapConnector = LdapConnector();

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
    fun queryUser(username: String?, auth: Pair<String, String>): FullUser? {
        if (username == null) return null
        val ldapSearchBase = Config.LDAP_SEARCH_BASE
        val searchName = if (username.contains(".")) "userPrincipalName=$username${Config.ACCOUNT_DOMAIN}" else "sAMAccountName=$username"
        val searchFilter = "(&(objectClass=user)($searchName))"
        val ctx = ldapConnector.bindLdap(auth) ?: return null
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
        val searchResultAttributes = results.nextElement()?.attributes
        results.close()
        val user = if (searchResultAttributes != null) LdapHelper.AttributesToUser(searchResultAttributes , ctx) else null
        ctx.close()
        return user
    }

}
