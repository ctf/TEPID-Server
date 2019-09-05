package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging

object Ldap : WithLogging() {

    private val ldapManager = LdapManager()

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

    /**
     * Returns user data, but guarantees a pass through ldap
     */
    fun authenticate(sam: String, pw: String): FullUser? {
        log.debug("Authenticating against ldap {\"sam\":\"$sam\"}")

        val shortUser = if (sam.matches(shortUserRegex)) sam else SessionManager.queryUser(sam, null)?.shortUser
                ?: AutoSuggest.queryLdap(sam, auth, 1).getOrNull(0)?.shortUser // TODO: pull this up higher
        if (shortUser == null) return null

        log.info("Authenticating {\"sam\":\"$sam\", \"shortUser\":\"$shortUser\"}")

        return ldapManager.queryUser(shortUser, shortUser to pw)
    }
}