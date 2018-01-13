package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.SessionRequest
import ca.mcgill.science.tepid.server.util.SessionManager
import ca.mcgill.science.tepid.utils.WithLogging
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/sessions")
class Sessions {

    @GET
    @Path("/{user}/{token}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getSession(@PathParam("user") user: String, @PathParam("token") token: String): Response {
        try {
            val username = user.split("@")[0]
            val longUser = username.contains(".")
            var s: Session? = null
            if (SessionManager.valid(token)) {
                s = SessionManager.get(token)
                if (s != null) {
                    if (s.expiration?.time ?: -1 < System.currentTimeMillis()
                            || longUser && s.user.longUser != username
                            || !longUser && s.user.shortUser != username)
                        s = null
                }
            }
            if (s != null) {
                s.role = SessionManager.getRole(s.user)
                return Response.ok(s).build()
            }
        } catch (e: Exception) {
            System.err.println("Exception caught: " + e.javaClass.canonicalName)
        }

        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Session token is no longer valid\"}").build()
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun startSession(req: SessionRequest): Response {
        try {
            val username = req.username.split("@")[0]
            val user = SessionManager.authenticate(username, req.password)
            //persistent sessions expire in 768 hours (32 days), permanent (printing) sessions expire in 35040 hours (4 years), other sessions expire in 24 hours
            val s = if (user != null) SessionManager.start(user, if (req.permanent) 35040 else if (req.persistent) 768 else 24) else null
            if (s != null) {
                s.persistent = req.persistent
                s.role = SessionManager.getRole(user)
                log.debug("Started session for user {}.", req.username)
                return Response.ok(s).build()
            }
        } catch (e: Exception) {
            System.err.println("Exception caught: " + e.javaClass.canonicalName)
            e.printStackTrace()
        }

        log.debug("Failed to start session for user {}.", req.username)
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Username or password incorrect\"}").build()
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    fun endSession(@PathParam("id") id: String) {
        SessionManager.end(id)
    }

    companion object : WithLogging()
}
