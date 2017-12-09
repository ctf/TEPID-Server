package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.User;
import ca.mcgill.science.tepid.server.util.SessionManager;
import org.glassfish.jersey.internal.util.Base64;

import javax.annotation.Priority;
import javax.annotation.security.DenyAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final String AUTHORIZATION_PROPERTY = "Authorization";

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        final Response ACCESS_DENIED = Response.status(Response.Status.FORBIDDEN).entity("403 You cannot access this resource").type(MediaType.TEXT_PLAIN).build(),
                AUTH_REQUIRED = Response.status(Response.Status.UNAUTHORIZED).entity("401 Please authenticate to access this resource")
                        .header("WWW-Authenticate", "Basic realm=\"Restricted Resource\"").type(MediaType.TEXT_PLAIN).build(),
                ACCESS_FORBIDDEN = Response.status(Response.Status.FORBIDDEN).entity("403 No access to this resource").type(MediaType.TEXT_PLAIN).build();
        Method method = resourceInfo.getResourceMethod();
        //Access denied for all
        if (method.isAnnotationPresent(DenyAll.class)) {
            requestContext.abortWith(ACCESS_FORBIDDEN);
            return;
        }
        if (method.isAnnotationPresent(RolesAllowed.class)) {
            //method roles; possible roles: user, ctfer, elder
            Set<String> roles = new HashSet<>(Arrays.asList(method.getAnnotation(RolesAllowed.class).value()));
            MultivaluedMap<String, String> headers = requestContext.getHeaders();
            final List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);
            if (authorization == null || authorization.isEmpty()) {
                requestContext.abortWith(AUTH_REQUIRED);
                return;
            }
            final String[] parts = authorization.get(0).split("\\s");
            if (parts.length < 2) {
                requestContext.abortWith(ACCESS_FORBIDDEN);
                return;
            }
            final String authScheme = parts[0], credentials = parts[1];
            Session session;
            switch (authScheme) {
                case "Token":
                    String[] samAndToken = new String(Base64.decode(credentials.getBytes())).split(":");
                    String sam = samAndToken[0].split("@")[0],
                            token = samAndToken[1];
                    boolean longUser = sam.contains(".");
                    Session s = null;
                    if (SessionManager.getInstance().valid(token)) {
                        s = SessionManager.getInstance().get(token);
                        if (!(longUser ? s.getUser().longUser : s.getUser().shortUser).equals(sam) || s.getExpiration().getTime() < System.currentTimeMillis()) {
                            s = null;
                        }
                    }
                    session = s;
                    break;
                case "Basic":
                    String[] samAndPassword = new String(Base64.decode(credentials.getBytes())).split(":");
                    String username = samAndPassword[0].split("@")[0];
                    User user = SessionManager.getInstance().authenticate(username, samAndPassword[1]);
                    session = user != null ? SessionManager.getInstance().start(user, 24) : null;
                    break;
                default:
                    //Someone is trying to use an unsupported auth scheme
                    requestContext.abortWith(ACCESS_DENIED);
                    return;
            }
            if (session == null) {
                //authentication failed
                requestContext.abortWith(AUTH_REQUIRED);
                return;
            }
            requestContext.setProperty("session", session);
            String role = SessionManager.getInstance().getRole(session.getUser());
            if (role == null) {
                //user is not allow to access the system at all
                requestContext.abortWith(ACCESS_DENIED);
                return;
            }
            session.setRole(role);
            if (!roles.contains(role)) {
                //user does not have the privileges to access the resource
                requestContext.abortWith(ACCESS_DENIED);
            }
        }
    }
}
