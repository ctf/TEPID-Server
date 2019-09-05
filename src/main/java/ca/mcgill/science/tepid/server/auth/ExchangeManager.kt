package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging

object ExchangeManager : WithLogging(){

    /**
     * Sets exchange student status.
     * Also updates user information from LDAP.
     * This refreshes the groups and courses of a user,
     * which allows for thier role to change
     *
     * @param sam      shortUser
     * @param exchange boolean for exchange status
     * @return updated status of the user; false if anything goes wrong
     */
    fun setExchangeStudent(sam: String, exchange: Boolean): Boolean {
        if (Config.LDAP_ENABLED) {
            log.info("Setting exchange status {\"sam\":\"$sam\", \"exchange_status\":\"$exchange\"}")
            val success = Ldap.setExchangeStudent(sam, exchange)
            val dbUser = SessionManager.queryUserDb(sam)
            val ldapUser = Ldap.queryUserLdap(sam, null) ?: return false
            val mergedUser = SessionManager.mergeUsers(ldapUser, dbUser)
            SessionManager.updateDbWithUser(mergedUser)
            return success
        } else return false
    }


}