package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.logError
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import java.util.*
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult
import javax.naming.ldap.Control
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext
import javax.naming.ldap.PagedResultsControl
import javax.naming.ldap.PagedResultsResponseControl

class LdapConnector(val timeout: Int? = 5000) : Logging {

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
        logger.trace {
            logMessage(
                "attempting bind to LDAP",
                "PROVIDER_URL" to Config.PROVIDER_URL,
                "SECURITY_PRINCIPAL" to Config.SECURITY_PRINCIPAL_PREFIX + shortUser
            )
        }
        try {
            val auth = createAuthMap(shortUser, password)
            return InitialLdapContext(auth, null)
        } catch (e: Exception) {
            // TODO: propagate up the auth stack, currently lots of `?: return null`
            logger.logError("failed to bind to LDAP", e, "shortUser" to shortUser)
            return null
        }
    }

    fun executeSearch(
        searchFilter: String,
        limit: Long = 10,
        ctx: LdapContext? = bindLdapWithResource()
    ): Set<SearchResult> {
        ctx ?: return emptySet()
        val results: List<SearchResult>
        try {
            val searchControls = SearchControls()
            searchControls.searchScope = SearchControls.SUBTREE_SCOPE
            searchControls.countLimit = limit

            if (limit > 1000 || limit <= 0) {
                results = executePagedSearch(
                    ctx,
                    { c: LdapContext -> c.search(Config.LDAP_SEARCH_BASE, searchFilter, searchControls) })
            } else {
                results = ctx.search(Config.LDAP_SEARCH_BASE, searchFilter, searchControls).toList()
            }

            return results.toSet()
        } finally {
            ctx.close()
        }
    }

    protected fun executePagedSearch(
        ctx: LdapContext,
        todo: (LdapContext) -> NamingEnumeration<SearchResult>,
        pageSize: Int = 1000
    ): List<SearchResult> {

        var cookie: kotlin.ByteArray? = null
        ctx.requestControls = arrayOf<Control>(
            PagedResultsControl(
                pageSize,
                Control.NONCRITICAL
            )
        )
        var out: List<SearchResult> = listOf()

        do {
            val results = todo(ctx)
            out = out.plus(results.toList())

            val controls = ctx.responseControls

            if (controls != null) {
                for (c in controls) {
                    if (c is PagedResultsResponseControl) {
                        cookie = c.cookie
                    }
                }
            }
            ctx.requestControls = arrayOf<Control>(
                PagedResultsControl(
                    pageSize, cookie, Control.CRITICAL
                )
            )
        } while (cookie != null)

        return out
    }

    /**
     * Defines the environment necessary for [InitialLdapContext]
     */
    private fun createAuthMap(user: ShortUser, password: String) = Hashtable<String, String>().apply {
        put(Context.SECURITY_AUTHENTICATION, "simple")
        put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        put(Context.PROVIDER_URL, Config.PROVIDER_URL)
        put(Context.SECURITY_PRINCIPAL, Config.SECURITY_PRINCIPAL_PREFIX + user)
        put(Context.SECURITY_CREDENTIALS, password)
        put("com.sun.jndi.ldap.read.timeout", timeout.toString())
        put("com.sun.jndi.ldap.connect.timeout", "500")
    }
}
