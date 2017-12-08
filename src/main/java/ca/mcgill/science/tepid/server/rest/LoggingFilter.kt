package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.util.Config
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
            println("Request for ${uriInfo.path}")
            println("Headers:")
            headers.forEach { (s, o) -> println("\t$s - $o") }
        }
    }

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        if (!Config.DEBUG) return
        responseContext.apply {
            println("Response $status for ${location?.path}")
            println("Headers:")
            stringHeaders.forEach { (s, o) -> println("\t$s - $o") }
        }
    }
}
