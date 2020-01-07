package ca.mcgill.science.tepid.server.auth

import `in`.waffl.q.Promise
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.server.util.logError
import org.apache.logging.log4j.kotlin.Logging
import javax.naming.NamingException

object AutoSuggest : Logging {

    private val ldapConnector = LdapConnector()

    /**
     * Sends list of matching [User]s based on current query
     *
     * @param like prefix
     * @param limit max list size
     * @return list of matching users
     */
    fun autoSuggest(like: String, limit: Int): Promise<List<FullUser>> {
        // TODO: maybe query the DB first
        val q = Q.defer<List<FullUser>>()
        object : Thread("LDAP AutoSuggest: " + like) {
            override fun run() {
                val out = queryLdap(like, limit)
                q.resolve(out)
            }
        }.start()
        return q.promise
    }

    fun queryLdap(like: String, limit: Int): List<FullUser> {
        try {
            return ldapConnector.executeSearch(
                "(&(objectClass=user)(|(userPrincipalName=$like*)(samaccountname=$like*)))",
                limit.toLong()
            ).toList()
        } catch (ne: NamingException) {
            logger.logError("could not get autosuggest", ne, "like" to like)
            return emptyList()
        }
    }
}