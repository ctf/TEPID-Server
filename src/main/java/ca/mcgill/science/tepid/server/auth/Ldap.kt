package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import javax.naming.directory.SearchControls

object Ldap : WithLogging() {

    private val ldapConnector = LdapConnector();

    private val shortUserRegex = Regex("[a-zA-Z]+[0-9]*")
    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Retrieve a [FullUser] from ldap
     * [sam] must be a valid short user or long user
     * The resource account will be used as auth if [pw] is null
     */
    fun queryUser(sam: String, pw: String?): FullUser? {
        if (!Config.LDAP_ENABLED) return null
        val auth = if (pw != null && shortUserRegex.matches(sam)) {
            log.trace("Querying user from LDAP {\"sam\":\"$sam\", \"by\":\"$sam\"}")
            sam to pw
        } else {
            log.trace("Querying user from LDAP {\"sam\":\"$sam\", \"by\":\"resource\"}")
            Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS
        }
        val user = queryUserLdap(sam, auth)
        user?.updateUserNameInformation()
        return user
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
    fun queryUserLdap(username: String?, auth: Pair<String, String>): FullUser? {
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

    /**
     * Returns user data, but guarantees a pass through ldap
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        log.debug("Authenticating against ldap {\"sam\":\"$sam\"}")

        val shortUser = if (sam.matches(shortUserRegex)) sam else AuthenticationManager.queryUser(sam, null)?.shortUser
                ?: AutoSuggest.queryLdap(sam, auth, 1).getOrNull(0)?.shortUser // TODO: pull this up higher
        if (shortUser == null) return null

        log.info("Authenticating {\"sam\":\"$sam\", \"shortUser\":\"$shortUser\"}")

        return queryUserLdap(shortUser, shortUser to pw)
    }
}