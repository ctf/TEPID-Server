package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import java.util.*
import javax.naming.Context
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext

class LdapConnector {

    /**
     * Create [LdapContext] for given credentials
     */

    fun bindLdap(shortUser: String, password: String): LdapContext? {
        log.trace("Attempting bind to LDAP: {'PROVIDER_URL':'${Config.PROVIDER_URL}', 'SECURITY_PRINCIPAL':'${Config.SECURITY_PRINCIPAL_PREFIX + shortUser}'}")
        try {
            val auth = createAuthMap(shortUser, password)
            return InitialLdapContext(auth, null)
        } catch (e: Exception) {
            // TODO: propagate up the auth stack, currently lots of `?: return null`
            log.error("Failed to bind to LDAP {\"shortUser\":\"$shortUser\"}", e)
            return null
        }
    }

    private companion object : WithLogging() {
        /**
         * Defines the environment necessary for [InitialLdapContext]
         */
        private fun createAuthMap(user: String, password: String) = Hashtable<String, String>().apply {
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
