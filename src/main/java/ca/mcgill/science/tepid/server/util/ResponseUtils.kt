package ca.mcgill.science.tepid.server.util

import org.apache.logging.log4j.Logger
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Wrapper for the web exception, where the message and response content are the same
 */
class TepidException(message: String,
                     status: Response.Status = Response.Status.BAD_REQUEST) : WebApplicationException(message, status.text(message))

/**
 * Helper to generate text responses with the given status info
 */
fun Response.Status.text(content: Any?): Response =
        Response.status(this).entity(content).type(MediaType.TEXT_PLAIN).build()

/**
 * Helper to throw a [WebApplicationException] and log the error if a [Logger] is supplied
 * Note that Nothing is returned, as the method will always fail
 */
@Throws(WebApplicationException::class)
fun fail(status: Response.Status, message: String): Nothing =
        throw TepidException(message, status)

fun unauthorizedResponse(errorMessage: String): Response =
        Response.Status.UNAUTHORIZED.text("{\"error\":\"$errorMessage\"}")

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

/*
 * Binders for facilitated failures
 */
fun failBadRequest(message: String): Nothing = fail(Response.Status.BAD_REQUEST, message)
fun failUnauthorized(message: String): Nothing = fail(Response.Status.UNAUTHORIZED, message)
fun failForbidden(message: String): Nothing = fail(Response.Status.FORBIDDEN, message)
fun failNotFound(message: String): Nothing = fail(Response.Status.NOT_FOUND, message)
fun failTimeout(message: String): Nothing = fail(Response.Status.REQUEST_TIMEOUT, message)
fun failInternal(message: String): Nothing = fail(Response.Status.INTERNAL_SERVER_ERROR, message)