package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging

object Ldap : Logging {

    private val ldapConnector = LdapConnector()

    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Type for defining the query string used for searching by specific attributes
     */
    enum class SearchBy(private val query: String) {
        sAMAccountName("sAMAccountName"),
        longUser("userPrincipalName"),
        email("mail");

        override fun toString(): String {
            return query
        }
    }

    internal fun queryByShortUser(username: ShortUser): FullUser? {
        return queryLdap(username, auth, SearchBy.sAMAccountName)
    }

    internal fun queryByLongUser(username: String): FullUser? {
        return queryLdap("$username@${Config.ACCOUNT_DOMAIN}", auth, SearchBy.longUser)
    }

    internal fun queryByEmail(userEmail: String): FullUser? {
        return queryLdap(userEmail, auth, SearchBy.email)
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
    private fun queryLdap(userName: PersonalIdentifier, auth: Pair<String, String>, searchName: SearchBy): FullUser? {
        val ctx = ldapConnector.bindLdap(auth.first, auth.second) ?: return null
        return ldapConnector.executeSearch("(&(objectClass=user)($searchName=$userName))", 1, ctx).firstOrNull()
    }

    /**
     * Returns user data, but guarantees a pass through ldap
     */
    fun authenticate(shortUser: ShortUser, pw: String): FullUser? {
        logger.info { logMessage("authenticating", "shortUser" to shortUser) }
        return queryLdap(shortUser, shortUser to pw, SearchBy.sAMAccountName)
    }

    /**
     * Gets all currently eligible users
     */
    fun getAllCurrentlyEligible(): Set<FullUser> {
        val filter =
            "(&(objectClass=user)(|${Config.QUOTA_GROUP.map { "(memberOf:1.2.840.113556.1.4.1941:=cn=${it.name},${Config.GROUPS_LOCATION})" }.joinToString()}))"
        return LdapConnector(0).executeSearch(filter, Long.MAX_VALUE)
    }
}