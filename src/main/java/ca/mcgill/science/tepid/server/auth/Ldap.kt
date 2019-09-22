package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Sam
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import javax.naming.directory.SearchControls

object Ldap : WithLogging() {

    private val ldapConnector = LdapConnector()

    val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Type for defining the query string used for searching by specific attributes
     */
    enum class SearchBy(private val query: String) {
        sAMAccountName("sAMAccountName"),
        longUser("userPrincipalName");

        override fun toString(): String {
            return query
        }
    }

    internal fun queryByShortUser(username: String): FullUser? {
        return queryLdap(username, auth, SearchBy.sAMAccountName)
    }

    internal fun queryByLongUser(username: String): FullUser? {
        return queryLdap("$username${Config.ACCOUNT_DOMAIN}", auth, SearchBy.longUser)
    }

    /**
     * Queries [userName] (short user or long user)
     * with [auth] credentials (username to password).
     * Resulting user is nonnull if it exists
     *
     * Note that [auth] may use different credentials than the [userName] in question.
     * However, if a different auth is provided (eg from our science account),
     * the studentId cannot be queried
     */
    private fun queryLdap(userName: Sam, auth: Pair<String, String>, searchName: SearchBy): FullUser? {
        val searchFilter = "(&(objectClass=user)($searchName=$userName))"
        val ctx = ldapConnector.bindLdap(auth.first, auth.second) ?: return null
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        val results = ctx.search(Config.LDAP_SEARCH_BASE, searchFilter, searchControls)
        val searchResultAttributes = results.nextElement()?.attributes
        results.close()
        val user = searchResultAttributes?.let { LdapHelper.AttributesToUser(searchResultAttributes, ctx) }
        ctx.close()
        user?.updateUserNameInformation()
        return user
    }

    /**
     * Returns user data, but guarantees a pass through ldap
     */
    fun authenticate(shortUser: ShortUser, pw: String): FullUser? {
        log.info("Authenticating {\"shortUser\":\"$shortUser\", \"shortUser\":\"$shortUser\"}")
        return queryLdap(shortUser, shortUser to pw, SearchBy.sAMAccountName)
    }
}