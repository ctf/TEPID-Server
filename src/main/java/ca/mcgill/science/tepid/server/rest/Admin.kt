package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.server.server.JobDataMonitor
import ca.mcgill.science.tepid.server.server.JobMonitor
import ca.mcgill.science.tepid.server.server.SessionMonitor
import ca.mcgill.science.tepid.server.server.UserMembershipMonitor
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.logMessage
import javax.annotation.security.RolesAllowed
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

@Path("/admin")
class Admin {

    fun logAction(crc: ContainerRequestContext, jobName: String, action: () -> Unit) {
        val session = crc.getSession()
        logMessage("manually triggering job", "id" to session.user._id, "jobName" to jobName, "time" to System.currentTimeMillis())
        action()
        logMessage("manually triggering job", "id" to session.user._id, "jobName" to jobName, "time" to System.currentTimeMillis())
    }

    @POST
    @Path("/actions/jobmonitor")
    @RolesAllowed(ELDER)
    fun launchJobMonitor(@Context crc: ContainerRequestContext) {
        logAction(crc, "JobMonitor") { JobMonitor().run() }
    }

    @POST
    @Path("/actions/jobdatamonitor")
    @RolesAllowed(ELDER)
    fun launchJobDataMonitor(@Context crc: ContainerRequestContext) {
        logAction(crc, "JobDataMonitor") { JobDataMonitor().run() }
    }

    @POST
    @Path("/actions/sessionmonitor")
    @RolesAllowed(ELDER)
    fun launchSessionMonitor(@Context crc: ContainerRequestContext) {
        logAction(crc, "SessionMonitor") { SessionMonitor().run() }
    }

    @POST
    @Path("/actions/usermembershipmonitor")
    @RolesAllowed(ELDER)
    fun launchUserMembershipMonitor(@Context crc: ContainerRequestContext) {
        logAction(crc, "UserMembershipMonitor") { UserMembershipMonitor().run() }
    }
}