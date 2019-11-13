package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.printing.QuotaCounter
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging

/**
 * Monitor which grants the semester to all users who are eligible
 * This is part of our effort to preserve a historical record of eligibility
 */

class UserMembershipMonitor : Runnable {
    override fun run() {
        logger.info { "granting semester to eligible users" }

        // Gets all users who are eligible and do not already have the current semester
        val needing = QuotaCounter.getAllNeedingGranting()

        // Partitions the list into users which are already in the DB and those which aren't
        // GetExistingUsers returns the users from the DB while we're at it
        val inDb = DB.getExistingUsers(needing.mapNotNull { u -> u._id }.toSet())
        val notInDb = needing subtract inDb

        val fromLdap = needing.associateBy { it -> it.shortUser ?: "" }

        // The FullUsers who were from the DB already have some history in the DB.
        // We therefore need to add the semester to its existing list of semesters
        inDb.forEach {
            val su = it.shortUser ?: return@forEach
            val ldapUser = fromLdap[su] ?: run {
                logMessage(
                    "failed granting user quota, could not get user we already got",
                    "id" to su,
                    "wait" to "what"
                ); return@forEach
            }
            DB.putUser(
                // While we're at it, we might as well merge in the latest updates from LDAP
                AuthenticationManager.mergeUsers(ldapUser, QuotaCounter.withCurrentSemester(it))
            )
        }

        // For the ones which were not in the DB, we just insert them
        notInDb.forEach {
            val su = it.shortUser ?: return@forEach
            DB.putUser(QuotaCounter.withCurrentSemester(it))
        }
        return
    }

    private companion object : Logging
}