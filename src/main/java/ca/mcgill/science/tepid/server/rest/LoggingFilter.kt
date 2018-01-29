package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.util.Config
import ca.mcgill.science.tepid.utils.WithLogging
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

/**
 * Created by Allan Wang on 2017-11-18.
 *
 * Note that you may log requests by implementing
 * ContainerRequestFilter
 */
@Provider
class LoggingFilter : ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val isSuccessful = responseContext.status in 200 until 300
        if (isSuccessful && !Config.DEBUG) return
        val entity = responseContext.entity
        val content: String = when (entity) {
            null -> "null"
            is String -> if (entity.length < 50) entity else "${entity.substring(0, 49)}\u2026"
            is Number, is Boolean -> entity.toString()
            is Collection<*> -> "[${entity::class.java.simpleName} (${entity.size})]"
            else -> "[${entity::class.java.simpleName}]"
        }
        val msg = "Response for ${requestContext.uriInfo.path}: ${responseContext.status}: $content"
        if (isSuccessful) log.trace(msg)
        else log.error(msg)
    }

    companion object : WithLogging()
}
