package ca.mcgill.science.tepid.server.util

import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Helper to generate text responses with the given status info
 */
fun Response.Status.text(message: String) =
        Response.status(this).entity("$statusCode $message").type(MediaType.TEXT_PLAIN).build()

fun unauthorizedResponse(errorMessage: String): Response = Response.status(Response.Status.UNAUTHORIZED)
        .entity("{\"error\":\"$errorMessage\"}").build()

inline val INVALID_SESSION_RESPONSE: Response
    get() = unauthorizedResponse("Invalid Session")

/**
 * Helper function to wrap a typical tepid response
 * Calls [action] while failing safely
 * If any error occurs, emit [Response.Status.UNAUTHORIZED]
 * else wrap the action result with [Response.ok]
 */
inline fun tepidResponse(errorMessage: String, action: () -> Any?): Response {
    try {
        val result: Any = action() ?: return unauthorizedResponse(errorMessage)
        return Response.ok(result).build()
    } catch (e: Exception) {
        e.tepidLog()
        return unauthorizedResponse(errorMessage)
    }
}