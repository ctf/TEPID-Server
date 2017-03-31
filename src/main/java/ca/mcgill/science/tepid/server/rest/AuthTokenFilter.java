package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.Session;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.util.Date;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class AuthTokenFilter implements ContainerResponseFilter {
	
    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
    	Session session = (Session) req.getProperty("session");
        if (session != null) {
        	res.getHeaders().add("X-TEPID-Session", session.getId());
        	res.getHeaders().add("X-TEPID-Role", session.getRole());
        	if (req.getHeaders().containsKey("X-TEPID-Session-Timeout")) {
        		int hours = Integer.parseInt(req.getHeaders().getFirst("X-TEPID-Session-Timeout"));
        		session.setExpiration(new Date(System.currentTimeMillis() + (hours * 60 * 60 * 1000)));
        	}
        	if (req.getHeaders().containsKey("X-TEPID-Session-Persistent")) {
        		session.setPersistent(Boolean.valueOf(req.getHeaders().getFirst("X-TEPID-Session-Persisten")));
        	}
        }
    }

}
