package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.utils.WithLogging
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
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
class LoggingFilter : ContainerRequestFilter, ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext) {
//        log.trace("Request ${requestContext.uriInfo.path}")
    }

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val isSuccessful = responseContext.status in 200 until 300
        if (isSuccessful && !Config.DEBUG) return
        val entity = responseContext.entity
        val content: String = when (entity) {
            null -> "null"
            is Throwable -> entity.localizedMessage
            is String -> entity
            is Number, is Boolean, is PutResponse -> entity.toString()
            is Collection<*> -> "[${entity::class.java.simpleName} (${entity.size})]"
            is Map<*, *> -> "{${entity::class.simpleName} (${entity.size}}"
            else -> responseContext.entityType.typeName
        }
        val msg = "Response for ${requestContext.uriInfo.path}: ${responseContext.status}: $content"
        if (isSuccessful) log.trace(msg)
        else log.error(msg)
    }

    companion object : WithLogging()
}
