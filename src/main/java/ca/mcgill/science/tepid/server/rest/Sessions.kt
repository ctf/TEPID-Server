package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.SessionRequest
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.failUnauthorized
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import javax.annotation.security.RolesAllowed
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType

@Path("/sessions")
class Sessions {

    @GET
    @Path("/{user}/{token}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getSession(@PathParam("user") user: String, @PathParam("token") token: String): Session {
        val username = user.split("@")[0]
        logger.trace(logMessage("Getting session", "username" to username, "token" to token))
        val session = SessionManager[token] ?: failUnauthorized("No session found")
        if (session.user.longUser == username || session.user.shortUser == username)
            return session.toSession()
        logger.info { "Username mismatch" }
        failUnauthorized("No session found")
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun startSession(req: SessionRequest): Session {
        logger.trace(logMessage("Received Start request", "for" to req.username))
        try {
            val username = req.username.split("@")[0]
            val user = AuthenticationManager.authenticate(username, req.password)
            // persistent sessions expire in 768 hours (32 days), permanent (printing) sessions expire in 35040 hours (4 years), other sessions expire in 24 hours
            val s = if (user != null) SessionManager.start(
                user,
                if (req.permanent) 35040 else if (req.persistent) 768 else 24
            ) else null
            if (s != null) {
                s.persistent = req.persistent
                return s.toSession()
            }
        } catch (e: Exception) {
            logger.error("Starting session failed", e)
        }
        failUnauthorized("Failed to start session for user ${req.username}")
    }

    @DELETE
    @RolesAllowed(USER, CTFER, ELDER)
    fun endCurrentSession(@Context ctx: ContainerRequestContext): Unit {
        val requestSession = ctx.getSession()
        endSession(requestSession.getId(), ctx)
    }

    @DELETE
    @RolesAllowed(USER, CTFER, ELDER)
    @Path("/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    fun endSession(@PathParam("id") id: String, @Context ctx: ContainerRequestContext): Unit {
        val requestSession = ctx.getSession()
        val targetSession = SessionManager[id] ?: failNotFound("")
        if (requestSession.user.shortUser == targetSession.user.shortUser) {
            logger.info(logMessage("deleting session", "session" to targetSession, "t" to 0))
            SessionManager.end(id)
        } else {
            logger.warn(
                logMessage(
                    "Unauthorized attempt to delete session",
                    "of" to requestSession.user.shortUser,
                    "by" to targetSession.user.shortUser
                )
            )
            // returns failNotFound for uniformity with the case when the session doesn't exist
            failNotFound("")
        }
    }

    @POST
    @RolesAllowed(ELDER)
    @Path("/invalidate/{sam}")
    @Produces(MediaType.TEXT_PLAIN)
    fun invalidateSessions(@PathParam("sam") sam: String, @Context ctx: ContainerRequestContext) {
        SessionManager.invalidateSessions(sam)
    }

    private companion object : Logging
}
