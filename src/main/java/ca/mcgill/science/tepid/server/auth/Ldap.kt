package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Sam
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import javax.naming.directory.SearchControls

object Ldap : WithLogging() {

    private val ldapConnector = LdapConnector()

    private val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")
    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Retrieve a [FullUser] from ldap
     * [sam] must be a valid short user or long user
     * The resource account will be used as auth if [pw] is null
     */
    fun queryUser(sam: Sam, pw: String?): FullUser? {
        val auth = if (pw != null && shortUserRegex.matches(sam)) {
            log.trace("Querying user from LDAP {\"sam\":\"$sam\", \"by\":\"$sam\"}")
            sam to pw
        } else {
            log.trace("Querying user from LDAP {\"sam\":\"$sam\", \"by\":\"resource\"}")
            Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS
        }
        val user = if (sam.contains(".")) queryLdapByLongUser(sam, auth) else queryByShortUser(sam, auth)
        return user
    }

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

    fun queryByShortUser(username: String, auth: Pair<String, String>): FullUser? {
        return queryLdap(username, auth, SearchBy.sAMAccountName)
    }

    fun queryLdapByLongUser(username: String, auth: Pair<String, String>): FullUser? {
        return queryLdap("$username${Config.ACCOUNT_DOMAIN}", auth, SearchBy.longUser)
    }

    fun effectBindByUser(username: ShortUser, password: String): FullUser? {
        return queryLdap(username, username to password,  SearchBy.sAMAccountName)
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
    fun queryLdap(userName: Sam, auth: Pair<String, String>, searchName: SearchBy): FullUser? {
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
    fun authenticate(sam: Sam, pw: String): FullUser? {
        log.debug("Authenticating against ldap {\"sam\":\"$sam\"}")

        val shortUser = if (sam.matches(shortUserRegex)) sam else AuthenticationManager.queryUser(sam, null)?.shortUser
            ?: AutoSuggest.queryLdap(sam, 1).getOrNull(0)?.shortUser // TODO: pull this up higher
        if (shortUser == null) return null

        log.info("Authenticating {\"sam\":\"$sam\", \"shortUser\":\"$shortUser\"}")

        return effectBindByUser(shortUser, pw)
    }
}