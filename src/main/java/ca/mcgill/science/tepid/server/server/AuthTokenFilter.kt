package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.util.getSessionSafely
import org.apache.logging.log4j.kotlin.Logging
import javax.annotation.Priority
import javax.ws.rs.Priorities
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

@Provider
@Priority(Priorities.HEADER_DECORATOR)
class AuthTokenFilter : ContainerResponseFilter {

    override fun filter(req: ContainerRequestContext, res: ContainerResponseContext) {
        val session = req.getSessionSafely() ?: return
        res.headers.add(HEADER_SESSION, session.getId())
        res.headers.add(HEADER_ROLE, session.role)
        if (req.headers.containsKey(HEADER_TIMEOUT)) {
            val hours = req.headers.getFirst(HEADER_TIMEOUT).toInt()
            session.expiration = System.currentTimeMillis() + hours * HOUR_IN_MILLIS
        }
        // todo perhaps do not make no timeout the default? eg do not count -1 as no expiration

        // our default header is persistent, so we will check against the "false" string rather than the "true" string
        if (req.headers.containsKey(HEADER_PERSISTENT)) {
            session.persistent = !req.headers.getFirst(HEADER_PERSISTENT).equals("false", ignoreCase = true)
        }
    }

    private companion object : Logging {

        private const val HOUR_IN_MILLIS = 60 * 60 * 1000
        private const val HEADER_SESSION = "X-TEPID-Session"
        private const val HEADER_ROLE = "X-TEPID-Role"
        private const val HEADER_TIMEOUT = "X-TEPID-Session-Timeout"
        private const val HEADER_PERSISTENT = "X-TEPID-Session-Persistent"
    }
}
