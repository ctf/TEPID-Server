package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.logError
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import javax.naming.NamingException
import javax.naming.directory.BasicAttribute
import javax.naming.directory.DirContext
import javax.naming.directory.ModificationItem
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

object ExchangeManager : Logging {

    private val ldapConnector = LdapConnector()

    /**
     * Sets exchange student status.
     * Also updates user information from LDAP.
     * This refreshes the groups and courses of a user,
     * which allows for their role to change
     *
     * @param shortUser shortUser
     * @param exchange boolean for exchange status
     * @return updated status of the user
     */
    fun setExchangeStudent(shortUser: ShortUser, exchange: Boolean): Boolean {
        logger.info { logMessage("setting exchange status", "shortUser" to shortUser, "exchange_status" to exchange) }
        val success = setExchangeStudentLdap(shortUser, exchange)
        AuthenticationManager.refreshUser(shortUser)
        return success
    }

    /**
     * Adds the supplied user to the exchange group
     *
     * @return updated status of the user
     */
    fun setExchangeStudentLdap(shortUser: ShortUser, exchange: Boolean): Boolean {
        val ldapSearchBase = Config.LDAP_SEARCH_BASE
        val searchFilter = "(&(objectClass=user)(sAMAccountName=$shortUser))"
        val ctx = ldapConnector.bindLdapWithResource() ?: return false
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        var searchResult: SearchResult? = null
        try {
            val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
            searchResult = results.nextElement()
            results.close()
        } catch (e: Exception) {
            logger.logError("error getting user while modifying exchange status", e, "shortUser" to shortUser)
        }

        if (searchResult == null) return false

        val userDn =
            searchResult.nameInNamespace ?: throw NamingException("userDn not found {\"shortUser\":\"$shortUser\"}")
        val groupDn = "CN=${Config.CURRENT_EXCHANGE_GROUP.name}, ${Config.GROUPS_LOCATION}"

        val mod = BasicAttribute("member", userDn)
        // todo check if we should ignore modification action if the user is already in/not in the exchange group?
        val mods =
            arrayOf(ModificationItem(if (exchange) DirContext.ADD_ATTRIBUTE else DirContext.REMOVE_ATTRIBUTE, mod))

        return parseLdapResponse(shortUser) { ctx.modifyAttributes(groupDn, mods); exchange }
    }

    private fun parseLdapResponse(shortUser: ShortUser, action: () -> Boolean): Boolean {

        return try {
            val targetState = action()
            logger.info {
                logMessage(
                    "modified exchange status",
                    "shortUser" to shortUser,
                    "status" to if (targetState) "added" else "removed"
                )
            }
            targetState
        } catch (e: NamingException) {
            if (e.message?.contains("LDAP: error code 53") == true) {
                logger.info {
                    logMessage(
                        "error removing user from Exchange",
                        "shortUser" to shortUser,
                        "cause" to "not in group"
                    )
                }
                false
            } else if (e.message?.contains("LDAP: error code 68") == true) {
                logger.info {
                    logMessage(
                        "error adding user from Exchange",
                        "shortUser" to shortUser,
                        "cause" to "already in group"
                    )
                }
                true
            } else {
                logger.logError("error adding to exchange students", e, "shortUser" to shortUser)
                throw e
            }
        }
    }
}
