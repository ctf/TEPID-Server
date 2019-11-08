package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.logError
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import java.util.*
import javax.naming.Context
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext

class LdapConnector {

    /**
     * Create [LdapContext] with the resource credentials
     */
    fun bindLdapWithResource(): LdapContext? {
        return bindLdap(Config.RESOURCE_USER, Config.RESOURCE_CREDENTIALS)
    }

    /**
     * Create [LdapContext] for given credentials
     */

    fun bindLdap(shortUser: ShortUser, password: String): LdapContext? {
        logger.trace { logMessage("attempting bind to LDAP", "PROVIDER_URL" to Config.PROVIDER_URL, "SECURITY_PRINCIPAL" to Config.SECURITY_PRINCIPAL_PREFIX + shortUser) }
        try {
            val auth = createAuthMap(shortUser, password)
            return InitialLdapContext(auth, null)
        } catch (e: Exception) {
            // TODO: propagate up the auth stack, currently lots of `?: return null`
            logger.logError("failed to bind to LDAP", e, "shortUser" to shortUser)
            return null
        }
    }

    private companion object : Logging {
        /**
         * Defines the environment necessary for [InitialLdapContext]
         */
        private fun createAuthMap(user: ShortUser, password: String) = Hashtable<String, String>().apply {
            put(Context.SECURITY_AUTHENTICATION, "simple")
            put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
            put(Context.PROVIDER_URL, Config.PROVIDER_URL)
            put(Context.SECURITY_PRINCIPAL, Config.SECURITY_PRINCIPAL_PREFIX + user)
            put(Context.SECURITY_CREDENTIALS, password)
            put("com.sun.jndi.ldap.read.timeout", "5000")
            put("com.sun.jndi.ldap.connect.timeout", "500")
        }
    }
}
