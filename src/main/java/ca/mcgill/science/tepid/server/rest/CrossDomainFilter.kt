package ca.mcgill.science.tepid.server.rest

import javax.annotation.Priority
import javax.ws.rs.Priorities
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

@Provider
@Priority(Priorities.HEADER_DECORATOR)
class CrossDomainFilter : ContainerResponseFilter {

    override fun filter(creq: ContainerRequestContext, cres: ContainerResponseContext) {
        cres.headers.add("Access-Control-Allow-Origin", "*")
        cres.headers.add("Access-Control-Allow-Headers",
                "origin, content-type, accept, authorization, x-tepid-no-redirect, x-tepid-session, x-tepid-role")
        cres.headers.add("Access-Control-Allow-Credentials", "true")
        var allow: String? = cres.getHeaderString("Allow")
        if (allow != null && !allow.contains("HEAD")) allow += ",HEAD"
        cres.headers.add("Access-Control-Allow-Methods",
                if (allow == null) "OPTIONS,GET,POST,PUT,DELETE,HEAD" else allow)
        cres.headers.add("Access-Control-Max-Age", "1209600")
    }

}
