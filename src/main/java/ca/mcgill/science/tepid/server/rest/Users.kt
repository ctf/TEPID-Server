package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.DTO.QuotaData
import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.auth.AutoSuggest
import ca.mcgill.science.tepid.server.auth.ExchangeManager
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.printing.IQuotaCounter
import ca.mcgill.science.tepid.server.printing.QuotaCounter
import ca.mcgill.science.tepid.server.util.failForbidden
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.failUnauthorized
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging
import java.net.URI
import java.net.URISyntaxException
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Consumes
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo

@Path("/users")
class Users {

    @GET
    @Path("/configured")
    @Produces(MediaType.APPLICATION_JSON)
    fun adminConfigured(): Boolean = try {
        DB.isAdminConfigured()
    } catch (e: Exception) {
        logger.error("localAdmin check failed", e)
        false
    }

    @GET
    @Path("/{sam}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun queryLdap(@PathParam("sam") sam: String, @Context crc: ContainerRequestContext, @Context uriInfo: UriInfo): Response {
        val session = crc.getSession()

        val returnedUser: FullUser // an explicit return, so that nothing is accidentally returned

        when (session.role) {
            USER -> {
                val queriedUser = AuthenticationManager.queryUser(sam)
                if (queriedUser == null || session.user.shortUser != queriedUser.shortUser) {
                    failForbidden()
                }
                // queried user is the querying user

                returnedUser = queriedUser
            }
            CTFER, ELDER -> {
                val queriedUser = AuthenticationManager.queryUser(sam)
                if (queriedUser == null) {
                    logger.warn(logMessage("could not find user", "sam" to sam))
                    failNotFound("Could not find user $sam")
                }
                returnedUser = queriedUser
            }
            else -> {
                failForbidden()
            }
        }

        // A SAM can be used as the query, but the url should be for the uname 
        if (sam != returnedUser.shortUser && !uriInfo.queryParameters.containsKey("noRedirect")) {
            try {
                return Response.seeOther(URI("users/" + returnedUser.shortUser)).build()
            } catch (ignored: URISyntaxException) {
            }
        }
        return Response.ok(returnedUser).build()
    }

    /**
     * Attempts to add the supplied ShortUser to the exchange group
     * @return updated status of the user; false if anything goes wrong
     */
    @PUT
    @Path("/{sam}/exchange")
    @RolesAllowed(CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setExchange(@PathParam("sam") shortUser: ShortUser, exchange: Boolean): Boolean =
        ExchangeManager.setExchangeStudent(shortUser, exchange)

    /**
     * Abstract implementation of modifying user data
     * Note that this is called from an endpoint where [ctx] holds a session (valid or not),
     * and where the roles allowed are at least of level "user"
     */
    private inline fun putUserData(
        sam: String,
        ctx: ContainerRequestContext,
        action: (user: FullUser) -> Unit
    ): Response {
        val session = ctx.getSession()
        val user = AuthenticationManager.queryUser(sam)
            ?: failNotFound("Could not find user $sam")
        if (session.role == USER && session.user.shortUser != user.shortUser)
            failUnauthorized()
        action(user)
        return DB.putUser(user)
    }

    @PUT
    @Path("/{sam}/nick")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setNick(@PathParam("sam") sam: String, nick: String, @Context ctx: ContainerRequestContext): Response =
        putUserData(sam, ctx) {
            it.nick = if (nick.isBlank()) null else nick
            logger.debug { "Setting nick for ${it.shortUser} to ${it.nick}" }
            it.updateUserNameInformation()
        }

    @PUT
    @Path("/{sam}/jobExpiration")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setJobExpiration(@PathParam("sam") sam: String, jobExpiration: Long, @Context ctx: ContainerRequestContext): Response =
        putUserData(sam, ctx) {
            it.jobExpiration = jobExpiration
            logger.trace { "Job expiration for ${it.shortUser} set to $jobExpiration" }
        }

    @PUT
    @Path("/{sam}/color")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    fun setColor(@PathParam("sam") sam: String, color: Boolean, @Context ctx: ContainerRequestContext): Response =
        putUserData(sam, ctx) {
            it.colorPrinting = color
            logger.trace { "Set color for ${it.shortUser} to ${it.colorPrinting}" }
        }

    @GET
    @Path("/{sam}/quota")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getQuota(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext): QuotaData {
        val session = ctx.getSession()
        if (session.role == USER && session.user.shortUser != shortUser)
            failForbidden()
        else {
            val user = AuthenticationManager.queryUser(shortUser) ?: failNotFound()
            return quotaCounter.getQuotaData(user)
        }
    }

    @POST
    @Path("/{sam}/refresh")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    fun forceRefresh(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext) {
        try {
            AuthenticationManager.refreshUser(shortUser)
            SessionManager.invalidateSessions(shortUser)
        } catch (e: Exception) {
            failNotFound("Could not find user $shortUser")
        }
    }

    @GET
    @Path("/autosuggest/{like}")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun ldapAutoSuggest(@PathParam("like") like: String, @DefaultValue("10") @QueryParam("limit") limit: Int): List<User> {
        val resultsPromise = AutoSuggest.autoSuggest(like, limit)
        return resultsPromise.getResult(20000)
            .map(FullUser::toUser) // todo check if we should further simplify to userquery
    }

    companion object : Logging {
        val quotaCounter: IQuotaCounter = QuotaCounter
    }
}
