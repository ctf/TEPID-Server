package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.util.Config
import ca.mcgill.science.tepid.utils.WithLogging
import java.io.IOException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

/**
 * Created by Allan Wang on 2017-11-18.
 */
@Provider
class LoggingFilter : ContainerRequestFilter, ContainerResponseFilter {

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext) {
        if (!Config.DEBUG) return
        requestContext.apply {
//            log.trace("Request for ${uriInfo.path}")
        }
    }

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        if (!Config.DEBUG) return
        responseContext.apply {
            log.trace("Response for ${requestContext.uriInfo.path}: $status")
        }
    }

    companion object : WithLogging()
}
