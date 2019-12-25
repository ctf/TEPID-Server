package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.DTO.QuotaData
import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.models.data.UserQuery
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.auth.AutoSuggest
import ca.mcgill.science.tepid.server.auth.ExchangeManager
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.Id
import ca.mcgill.science.tepid.server.db.Order
import ca.mcgill.science.tepid.server.db.remapExceptions
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
    @Path("/{personalIdentifier}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun queryLdap(@PathParam("personalIdentifier") personalIdentifier: PersonalIdentifier, @Context crc: ContainerRequestContext, @Context uriInfo: UriInfo): Response {
        val session = crc.getSession()

        val returnedUser: FullUser // an explicit return, so that nothing is accidentally returned

        when (session.role) {
            USER -> {
                val queriedUser = AuthenticationManager.queryUser(personalIdentifier)
                if (queriedUser == null || session.user.shortUser != queriedUser.shortUser) {
                    failForbidden()
                }
                // queried user is the querying user

                returnedUser = queriedUser
            }
            CTFER, ELDER -> {
                val queriedUser = AuthenticationManager.queryUser(personalIdentifier)
                if (queriedUser == null) {
                    logger.warn(logMessage("could not find user", "sam" to personalIdentifier))
                    failNotFound("Could not find user $personalIdentifier")
                }
                returnedUser = queriedUser
            }
            else -> {
                failForbidden()
            }
        }

        // A PersonalIdentifier can be used as the query, but the url should be for the _id
        if (personalIdentifier != returnedUser._id && !uriInfo.queryParameters.containsKey("noRedirect")) {
            try {
                return Response.seeOther(URI("users/" + returnedUser._id)).build()
            } catch (ignored: URISyntaxException) {
            }
        }
        return Response.ok(returnedUser).build()
    }

    @PUT
    @Path("/{id}/color")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    fun setColor(@PathParam("id") id: Id, color: Boolean, @Context ctx: ContainerRequestContext): PutResponse =
        putUserData(id, ctx) {
            it.colorPrinting = color
            logger.trace { "Set color for ${it._id} to ${it.colorPrinting}" }
        }

    /**
     * Attempts to add the supplied ShortUser to the exchange group
     * @return updated status of the user; false if anything goes wrong
     */
    @PUT
    @Path("/{id}/exchange")
    @RolesAllowed(CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setExchange(@PathParam("id") id: Id, exchange: Boolean): Boolean =
        ExchangeManager.setExchangeStudent(remapExceptions { DB.users.read(id).shortUser ?: "" }, exchange)

    @PUT
    @Path("/{id}/jobExpiration")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setJobExpiration(@PathParam("id") id: Id, jobExpiration: Long, @Context ctx: ContainerRequestContext): PutResponse =
        putUserData(id, ctx) {
            it.jobExpiration = jobExpiration
            logger.trace { "Job expiration for ${it._id} set to $jobExpiration" }
        }

    @PUT
    @Path("/{id}/nick")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setNick(@PathParam("id") id: Id, nick: String, @Context ctx: ContainerRequestContext): PutResponse =
        putUserData(id, ctx) {
            it.nick = if (nick.isBlank()) null else nick
            logger.debug { "Setting nick for ${it._id} to ${it.nick}" }
            it.updateUserNameInformation()
        }

    @GET
    @Path("/{id}/quota")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getQuota(@PathParam("id") id: Id, @Context ctx: ContainerRequestContext): QuotaData {
        val session = ctx.getSession()
        if (session.role == USER && session.user.shortUser != id)
            failForbidden()
        else {
            val user = AuthenticationManager.queryUser(id) ?: failNotFound()
            return quotaCounter.getQuotaData(user)
        }
    }

    @GET
    @Path("/{id}/jobs")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun listJobs(@PathParam("id") id: Id, @Context req: ContainerRequestContext): Collection<PrintJob> {
        val session = req.getSession()
        if (session.role == USER && session.user.shortUser != id) {
            return emptyList()
        }
        return DB.printJobs.getJobsByUser(id, Order.DESCENDING)
    }

    @GET
    @Path("/autosuggest/{like}")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun ldapAutoSuggest(@PathParam("like") like: String, @DefaultValue("10") @QueryParam("limit") limit: Int): List<UserQuery> {
        val resultsPromise = AutoSuggest.autoSuggest(like, limit)
        return resultsPromise.getResult(20000)
            .map { it.toUser().toUserQuery() }
    }

    @POST
    @Path("/{id}/refresh")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    fun forceRefresh(@PathParam("id") id: Id, @Context ctx: ContainerRequestContext) {
        try {
            AuthenticationManager.refreshUser(id)
            SessionManager.invalidateSessions(id)
        } catch (e: Exception) {
            failNotFound("Could not find user $id")
        }
    }

    /**
     * Abstract implementation of modifying user data
     * Note that this is called from an endpoint where [ctx] holds a session (valid or not),
     * and where the roles allowed are at least of level "user"
     */
    private inline fun putUserData(
        id: Id,
        ctx: ContainerRequestContext,
        action: (user: FullUser) -> Unit
    ): PutResponse {
        val session = ctx.getSession()
        val user = DB.users.read(id)
        if ((session.role.isBlank()) || (session.role == USER && session.user.shortUser != user.shortUser))
            failUnauthorized()
        action(user)
        return remapExceptions { DB.users.put(user) }
    }

    companion object : Logging {
        val quotaCounter: IQuotaCounter = QuotaCounter
    }
}
