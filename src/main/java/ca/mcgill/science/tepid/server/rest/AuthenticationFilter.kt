package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.*
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.server.util.Config
import ca.mcgill.science.tepid.server.util.SessionManager
import ca.mcgill.science.tepid.server.util.text
import ca.mcgill.science.tepid.utils.WithLogging
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

    /**
     * Validates the request and rejects immediately if roles or other conditions are not met
     * Note that in cases of finding the sam, token, password, etc,
     * A null value may either indicate that none was passed, or that
     * the value passed was not properly encoded
     */
    override fun filter(requestContext: ContainerRequestContext) {
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
            val session: FullSession?
            when (authScheme) {
                TOKEN -> {
                    val samAndToken = Session.decodeHeader(credentials)?.split(":")
                    val sam = samAndToken?.getOrNull(0)?.split("@")?.getOrNull(0)
                    val token = samAndToken?.getOrNull(1)
                    if (sam == null || token == null) {
                        sam ?: log.warn("Bad sam passed")
                        token ?: log.warn("Bad token passed")
                        requestContext.abortWith(AUTH_REQUIRED)
                        return
                    }
                    var s: FullSession? = SessionManager[token]
                    if (s != null && !s.user.isMatch(sam)) {
                        log.warn("Session retrieved does not match $sam")
                        s = null
                    }
                    session = s
                }
                BASIC -> {
                    val samAndPassword = Session.decodeHeader(credentials)?.split(":")
                    val username = samAndPassword?.getOrNull(0)?.split("@")?.getOrNull(0)
                    val password = samAndPassword?.getOrNull(1)
                    if (username == null || password == null) {
                        username ?: log.warn("Bad username passed")
                        password ?: log.warn("Bad password passed")
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
            val role = session.user.getCtfRole()
            if (role.isEmpty() || !roles.contains(role)) {
                log.warn("User does not have enough privileges")
                requestContext.abortWith(ACCESS_DENIED)
                return
            }
            session.role = role
            requestContext.setProperty(SESSION, session)
        }
    }

    fun getCtfRole(user: FullUser) : String {
        if (user.groups.isEmpty())
            return ""
        if (user.authType == null || user.authType != LOCAL) {
            val g = user.groups.toSet()
            if (Config.ELDERS_GROUP.any(g::contains)) return ELDER
            if (Config.CTFERS_GROUP.any(g::contains)) return CTFER
            if (Config.USERS_GROUP.any(g::contains)) return USER
            return ""
        } else {
            return if (user.role == ADMIN) ELDER else USER
        }
    }

    companion object : WithLogging() {
        private const val AUTHORIZATION_PROPERTY = "Authorization"
        private const val BASIC = "Basic"
        private const val TOKEN = "Token"
        const val SESSION = "session"

        private val WHITESPACE_REGEX = Regex("\\s")

        private inline val ACCESS_DENIED: Response
            get() = Response.Status.FORBIDDEN.text("403 You cannot access this resource")
        private inline val AUTH_REQUIRED: Response
            get() = Response.status(Response.Status.UNAUTHORIZED).entity("401 Please authenticate to access this resource")
                    .header("WWW-Authenticate", "Basic realm=\"Restricted Resource\"").type(MediaType.TEXT_PLAIN).build()
        private inline val ACCESS_FORBIDDEN: Response
            get() = Response.Status.FORBIDDEN.text("403 No access to this resource")
    }
}
