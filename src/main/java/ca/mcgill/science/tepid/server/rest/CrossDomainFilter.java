package ca.mcgill.science.tepid.server.rest;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class CrossDomainFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext creq, ContainerResponseContext cres) {
        cres.getHeaders().add("Access-Control-Allow-Origin", "*");
        cres.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, x-tepid-no-redirect, x-tepid-session, x-tepid-role");
        cres.getHeaders().add("Access-Control-Allow-Credentials", "true");
        String allow = cres.getHeaderString("Allow");
        if (allow != null && !allow.contains("HEAD")) allow += ",HEAD";
        cres.getHeaders().add("Access-Control-Allow-Methods", (allow == null ? "OPTIONS,GET,POST,PUT,DELETE,HEAD" : allow));
        cres.getHeaders().add("Access-Control-Max-Age", "1209600");
    }

}
