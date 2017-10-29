package ca.mcgill.science.tepid.server.util

import javax.ws.rs.core.Response

/**
 * Created by Allan Wang on 2017-09-29.
 */
fun Exception.tepidLog() {
    System.err.println("WebServer Exception Caught: ${javaClass.canonicalName}")
    printStackTrace()
}

fun unauthorizedResponse(errorMessage: String): Response = Response.status(Response.Status.UNAUTHORIZED)
        .entity("{\"error\":\"$errorMessage\"}").build()

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
