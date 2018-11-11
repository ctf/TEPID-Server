package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.SessionRequest
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.util.failUnauthorized
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.utils.WithLogging
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
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
        log.trace("Getting session $username $token")
        val session = SessionManager[token] ?: failUnauthorized("No session found")
        if (session.user.longUser == username || session.user.shortUser == username)
            return session.toSession()
        log.info("Username mismatch")
        failUnauthorized("No session found")
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun startSession(req: SessionRequest): Session {
        log.trace("Received Start request for ${req.username}")
        try {
            val username = req.username.split("@")[0]
            val user = SessionManager.authenticate(username, req.password)
            //persistent sessions expire in 768 hours (32 days), permanent (printing) sessions expire in 35040 hours (4 years), other sessions expire in 24 hours
            val s = if (user != null) SessionManager.start(user, if (req.permanent) 35040 else if (req.persistent) 768 else 24) else null
            if (s != null) {
                s.persistent = req.persistent
                return s.toSession()
            }
        } catch (e: Exception) {
            log.error("Starting session failed", e)
        }
        failUnauthorized("Failed to start session for user ${req.username}")
    }

    @DELETE
    @RolesAllowed(USER, CTFER, ELDER)
    @Path("/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    fun endSession(@PathParam("id") id: String, @Context ctx: ContainerRequestContext): Response {
        val requestSession = ctx.getSession()
        val targetSession = SessionManager[id] ?: failNotFound("")
        if (requestSession.user == targetSession.user) {
            SessionManager.end(id)
            return Response.ok("ok").build()
        }
        failNotFound("")
    }

    @POST
    @RolesAllowed(ELDER)
    @Path("/invalidate/{sam}")
    @Produces(MediaType.TEXT_PLAIN)
    fun invalidateSessions(@PathParam("sam") sam: String, @Context ctx: ContainerRequestContext) {
        SessionManager.invalidateSessions(sam)
    }

    private companion object : WithLogging()
}
