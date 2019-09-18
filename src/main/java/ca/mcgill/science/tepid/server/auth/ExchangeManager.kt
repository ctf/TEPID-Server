package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import javax.naming.NamingException
import javax.naming.directory.BasicAttribute
import javax.naming.directory.DirContext
import javax.naming.directory.ModificationItem
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

object ExchangeManager : WithLogging() {

    private val ldapConnector = LdapConnector()

    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Sets exchange student status.
     * Also updates user information from LDAP.
     * This refreshes the groups and courses of a user,
     * which allows for thier role to change
     *
     * @param shortUser shortUser
     * @param exchange boolean for exchange status
     * @return updated status of the user; false if anything goes wrong
     */
    fun setExchangeStudent(shortUser: ShortUser, exchange: Boolean): Boolean {
        log.info("Setting exchange status {\"shortUser\":\"$shortUser\", \"exchange_status\":\"$exchange\"}")
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
        val ctx = ldapConnector.bindLdap(auth) ?: return false
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        var searchResult: SearchResult? = null
        try {
            val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
            searchResult = results.nextElement()
            results.close()
        } catch (e: Exception) {
            log.error("Error getting user while modifying exchange status: {\"shortUser\":\"$shortUser\", \"cause\":\"${e.message}\"}")
        }

        if (searchResult == null) return false

        val userDn = searchResult.nameInNamespace ?: throw NamingException("userDn not found {\"shortUser\":\"$shortUser\"}")
        val groupDn = "CN=${Config.CURRENT_EXCHANGE_GROUP.name}, ${Config.GROUPS_LOCATION}"

        val mod = BasicAttribute("member", userDn)
        // todo check if we should ignore modification action if the user is already in/not in the exchange group?
        val mods =
            arrayOf(ModificationItem(if (exchange) DirContext.ADD_ATTRIBUTE else DirContext.REMOVE_ATTRIBUTE, mod))

        return parseLdapResponse(shortUser) { ctx.modifyAttributes(groupDn, mods); exchange }
    }

    private fun parseLdapResponse(
        shortUser: ShortUser,
        action: () -> Boolean
    ): Boolean {

        return try {
            val targetState = action()
            log.info("${if (targetState) "Added $shortUser to" else "Removed $shortUser from"} exchange students.")
            targetState
        } catch (e: NamingException) {
            if (e.message?.contains("LDAP: error code 53") == true) {
                log.info("Error removing user from Exchange: {\"shortUser\":\"$shortUser\", \"cause\":\"not in group\"}")
                false
            } else if (e.message!!.contains("LDAP: error code 68")) {
                log.info("Error adding user from Exchange: {\"shortUser\":\"$shortUser\", \"cause\":\"already in group\"}")
                true
            } else {
                log.error("Error adding to exchange students. {\"shortUser\":\"$shortUser\", \"cause\":\"${e.message}\"}")
                throw e
            }
        }
    }
}