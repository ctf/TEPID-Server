package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.server.util.SessionManager
import ca.mcgill.science.tepid.server.util.text
import ca.mcgill.science.tepid.utils.WithLogging
import org.glassfish.jersey.internal.util.Base64
import javax.annotation.Priority
import javax.annotation.security.DenyAll
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Priorities
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.ResourceInfo
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider

@Provider
@Priority(Priorities.HEADER_DECORATOR)
class AuthenticationFilter : ContainerRequestFilter {

    @Context
    private var resourceInfo: ResourceInfo? = null

    override fun filter(requestContext: ContainerRequestContext) {
        log.trace("AuthFilter at " + System.currentTimeMillis() + " : " + requestContext.uriInfo.path)
        val method = resourceInfo?.resourceMethod ?: return log.warn("Skipping null method")
        if (method.isAnnotationPresent(DenyAll::class.java)) {
            log.warn("Method annotated with DenyAll")
            requestContext.abortWith(ACCESS_FORBIDDEN)
            return
        }
        if (method.isAnnotationPresent(RolesAllowed::class.java)) {
            //method roles; possible roles: user, ctfer, elder
            val roles = method.getAnnotation(RolesAllowed::class.java).value.toSet()
            val headers = requestContext.headers
            val authorization = headers[AUTHORIZATION_PROPERTY]
            log.info("Auth $authorization")
            if (authorization == null || authorization.isEmpty()) {
                log.warn("Empty or null authorization")
                requestContext.abortWith(AUTH_REQUIRED)
                return
            }
            val parts = authorization[0].split(WHITESPACE_REGEX)
            if (parts.size < 2) {
                log.warn("Authorization is only of size ${parts.size}; expected 2")
                requestContext.abortWith(ACCESS_FORBIDDEN)
                return
            }
            val authScheme = parts[0]
            val credentials = parts[1]
            val session: Session?
            when (authScheme) {
                "Token" -> {
                    val samAndToken = String(Base64.decode(credentials.toByteArray())).split(":")
                    val sam = samAndToken.getOrNull(0)?.split("@")?.getOrNull(0)
                    val token = samAndToken.getOrNull(1)
                    if (sam == null || token == null) {
                        sam ?: log.warn("Null sam passed")
                        token ?: log.warn("Null token passed")
                        requestContext.abortWith(AUTH_REQUIRED)
                        return
                    }
                    var s: Session? = SessionManager[token]
                    if (s != null && !s.user.isMatch(sam))
                        s = null
                    session = s
                }
                "Basic" -> {
                    val samAndPassword = String(Base64.decode(credentials.toByteArray())).split(":")
                    val username = samAndPassword.getOrNull(0)?.split("@")?.getOrNull(0)
                    val password = samAndPassword.getOrNull(1)
                    if (username == null || password == null) {
                        username ?: log.warn("Null username passed")
                        password ?: log.warn("Null password passed")
                        requestContext.abortWith(AUTH_REQUIRED)
                        return
                    }
                    val user = SessionManager.authenticate(username, password)
                    session = if (user != null) SessionManager.start(user, 24) else null
                }
                else -> {
                    log.warn("Unsupported auth scheme $authScheme")
                    requestContext.abortWith(ACCESS_DENIED)
                    return
                }
            }
            if (session == null) {
                log.warn("Null session output")
                requestContext.abortWith(AUTH_REQUIRED)
                return
            }
            val role = SessionManager.getRole(session.user)
            if (role.isBlank() || !roles.contains(role)) {
                log.warn("User does not have enough privileges")
                requestContext.abortWith(ACCESS_DENIED)
                return
            }
            session.role = role
            requestContext.setProperty("session", session)
        }
    }

    private companion object : WithLogging() {
        private const val AUTHORIZATION_PROPERTY = "Authorization"

        private val WHITESPACE_REGEX = Regex("\\s")

        private val ACCESS_DENIED: Response
            get() = Response.Status.FORBIDDEN.text("403 You cannot access this resource")
        private val AUTH_REQUIRED: Response
            get() = Response.status(Response.Status.UNAUTHORIZED).entity("401 Please authenticate to access this resource")
                    .header("WWW-Authenticate", "Basic realm=\"Restricted Resource\"").type(MediaType.TEXT_PLAIN).build()
        private val ACCESS_FORBIDDEN: Response
            get() = Response.Status.FORBIDDEN.text("403 No access to this resource")
    }
}
