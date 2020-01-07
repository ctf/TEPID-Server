package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.ErrorResponse
import ca.mcgill.science.tepid.server.server.mapper
import org.apache.logging.log4j.Logger
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Wrapper for the web exception, where the message and response content are the same
 */
class TepidException(
    message: String,
    status: Response.Status = Response.Status.BAD_REQUEST
) : WebApplicationException(message, status.text(message))

/**
 * Helper to generate text responses with the given status info
 */
fun Response.Status.text(content: Any?): Response =
    Response.status(this).entity(content).type(MediaType.TEXT_PLAIN).build()

val Response.isSuccessful: Boolean
    get() = status in 200 until 300

/**
 * Helper to throw a [WebApplicationException] and log the error if a [Logger] is supplied
 * Note that Nothing is returned, as the method will always fail
 */
@Throws(WebApplicationException::class)
fun fail(status: Response.Status, data: ErrorResponse): Nothing =
    throw TepidException(mapper.writeValueAsString(data), status)

fun fail(status: Response.Status, message: String): Nothing =
    fail(status, ErrorResponse(status.statusCode, message))

/*
 * Binders for facilitated failures
 */

fun failBadRequest(message: String): Nothing = fail(Response.Status.BAD_REQUEST, message)
fun failUnauthorized(message: String = "Authentication required"): Nothing = fail(Response.Status.UNAUTHORIZED, message)
fun failForbidden(message: String = "You cannot access this resource"): Nothing =
    fail(Response.Status.FORBIDDEN, message)

fun failNotFound(message: String = "Resource not found"): Nothing = fail(Response.Status.NOT_FOUND, message)
fun failTimeout(message: String): Nothing = fail(Response.Status.REQUEST_TIMEOUT, message)
fun failInternal(message: String): Nothing = fail(Response.Status.INTERNAL_SERVER_ERROR, message)
