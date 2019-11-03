package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.util.logMessage
import ca.mcgill.science.tepid.server.util.text
import org.apache.logging.log4j.kotlin.Logging
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
        val method = resourceInfo?.resourceMethod ?: return logger.warn("skipping null method")
        if (method.isAnnotationPresent(DenyAll::class.java)) {
            logger.warn { "method annotated with DenyAll" }
            requestContext.abortWith(ACCESS_FORBIDDEN)
            return
        }
        if (method.isAnnotationPresent(RolesAllowed::class.java)) {
            // method roles; possible roles: user, ctfer, elder
            val roles = method.getAnnotation(RolesAllowed::class.java).value.toSet()
            val headers = requestContext.headers
            val authorization = headers[AUTHORIZATION_PROPERTY]
            if (authorization == null || authorization.isEmpty()) {
                logger.warn { "Empty or null authorization" }
                requestContext.abortWith(AUTH_REQUIRED)
                return
            }
            val parts = authorization[0].split(WHITESPACE_REGEX)
            if (parts.size < 2) {
                logger.warn { logMessage("authorization of incorrect size", "size" to parts.size, "expected" to 2) }
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
                        sam ?: logger.warn { "Bad sam passed" }
                        token ?: logger.warn { "Bad token passed" }
                        requestContext.abortWith(AUTH_REQUIRED)
                        return
                    }
                    var s: FullSession? = SessionManager[token]
                    if (s != null && !s.user.isMatch(sam)) {
                        logger.warn { logMessage("session retrieved does not match", "ident" to sam) }
                        s = null
                    }
                    session = s
                }
                BASIC -> {
                    val samAndPassword = Session.decodeHeader(credentials)?.split(":")
                    val username = samAndPassword?.getOrNull(0)?.split("@")?.getOrNull(0)
                    val password = samAndPassword?.getOrNull(1)
                    if (username == null || password == null) {
                        username ?: logger.warn { "Bad username passed" }
                        password ?: logger.warn { "Bad password passed" }
                        requestContext.abortWith(AUTH_REQUIRED)
                        return
                    }
                    val user = AuthenticationManager.authenticate(username, password)
                    session = if (user != null) SessionManager.start(user, 0) else null
                }
                else -> {
                    logger.warn { "Unsupported auth scheme $authScheme" }
                    requestContext.abortWith(ACCESS_DENIED)
                    return
                }
            }
            if (session == null) {
                logger.warn { "Null session output" }
                requestContext.abortWith(AUTH_REQUIRED)
                return
            }

            if (session.role.isEmpty() || !roles.contains(session.role)) {
                logger.warn { "User does not have enough privileges" }
                requestContext.abortWith(ACCESS_DENIED)
                return
            }
            requestContext.setProperty(SESSION, session)
        }
    }

    companion object : Logging {
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

        /**
         * Returns either an empty role, or one of
         * [USER], [CTFER], or [ELDER]
         */
        fun getCtfRole(user: FullUser): String {
            val g = user.groups
            if (Config.ELDERS_GROUP.any(g::contains)) return ELDER
            if (Config.CTFERS_GROUP.any(g::contains)) return CTFER
            if (Config.USERS_GROUP.any(g::contains)) return USER
            return ""
        }

        /**
         * Says if the current semester is eligible for granting quota.
         * Checks that the student is in one of the User groups and that they are enrolled in courses.
         * I've put it here because it involves checking against LDAP groups to determine privileges
         */
        fun hasCurrentSemesterEligible(user: FullUser, registeredSemesters: Set<Semester>): Boolean {
            return (Config.USERS_GROUP.any(user.groups::contains) && registeredSemesters.contains(Semester.current))
        }
    }
}
