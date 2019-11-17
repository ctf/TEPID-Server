package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import org.apache.logging.log4j.kotlin.Logging

/**
 * Monitor which grants the semester to all users who are eligible
 * This is part of our effort to preserve a historical record of eligibility
 */

class UserMembershipMonitor : Runnable {
    override fun run() {
        logger.info { "granting semester to eligible users" }

        AuthenticationManager.addAllCurrentlyEligible()
    }

    private companion object : Logging
}