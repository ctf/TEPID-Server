package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
import ca.mcgill.science.tepid.server.auth.AuthenticationManager
import ca.mcgill.science.tepid.server.auth.AutoSuggest
import ca.mcgill.science.tepid.server.auth.ExchangeManager
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.text
import ca.mcgill.science.tepid.utils.WithLogging
import java.net.URI
import java.net.URISyntaxException
import javax.annotation.security.RolesAllowed
import javax.ws.rs.Consumes
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.NotFoundException
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
import kotlin.math.max

@Path("/users")
class Users {

    @GET
    @Path("/configured")
    @Produces(MediaType.APPLICATION_JSON)
    fun adminConfigured(): Boolean = try {
        DB.isAdminConfigured()
    } catch (e: Exception) {
        log.error("localAdmin check failed", e)
        false
    }

    @GET
    @Path("/{sam}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun queryLdap(@PathParam("sam") sam: String, @QueryParam("pw") pw: String?, @Context crc: ContainerRequestContext, @Context uriInfo: UriInfo): Response {
        val session = crc.getSession()

        val returnedUser: FullUser // an explicit return, so that nothing is accidentally returned

        when (session.role) {
            USER -> {
                val queriedUser = AuthenticationManager.queryUser(sam, pw)
                if (queriedUser == null || session.user.shortUser != queriedUser.shortUser) {
                    return Response.Status.FORBIDDEN.text("You cannot access this resource")
                }
                // queried user is the querying user

                returnedUser = queriedUser
            }
            CTFER, ELDER -> {
                val queriedUser = AuthenticationManager.queryUser(sam, pw)
                if (queriedUser == null) {
                    log.warn("Could not find user {}.", sam)
                    throw NotFoundException(Response.status(404).entity("Could not find user " + sam).type(MediaType.TEXT_PLAIN).build())
                }
                returnedUser = queriedUser
            }
            else -> {
                return Response.Status.FORBIDDEN.text("You cannot access this resource")
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
        val user = AuthenticationManager.queryUser(sam, null)
            ?: return Response.Status.NOT_FOUND.text("User $sam not found")
        if (session.role == USER && session.user.shortUser != user.shortUser)
            return Response.Status.UNAUTHORIZED.text("You cannot change this resource")
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
            log.debug("Setting nick for ${it.shortUser} to ${it.nick}")
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
            log.trace("Job expiration for ${it.shortUser} set to $jobExpiration")
        }

    @PUT
    @Path("/{sam}/color")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    fun setColor(@PathParam("sam") sam: String, color: Boolean, @Context ctx: ContainerRequestContext): Response =
        putUserData(sam, ctx) {
            it.colorPrinting = color
            log.trace("Set color for ${it.shortUser} to ${it.colorPrinting}")
        }

    @GET
    @Path("/{sam}/quota")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getQuota(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext): Int {
        val session = ctx.getSession()
        return if (session.role == USER && session.user.shortUser != shortUser)
            -1
        else {
            val user = AuthenticationManager.queryUser(shortUser, null)
            getQuota(user)
        }
    }

    @GET
    @Path("/{sam}/quota/debug")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getQuotaDebug(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext): QuotaData {
        val user = AuthenticationManager.queryUser(shortUser, null)
        return getQuotaData(user) ?: failNotFound("Could not calculate quota")
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
            throw NotFoundException(Response.status(404).entity("Could not find user " + shortUser).type(MediaType.TEXT_PLAIN).build())
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

    companion object : WithLogging() {

        data class QuotaData(
            val shortUser: String,
            val quota: Int,
            val maxQuota: Int,
            val totalPrinted: Int,
            val semesters: List<Semester>
        )

        fun getQuotaData(user: FullUser?): QuotaData? {
            val shortUser = user?.shortUser ?: return null

            if (AuthenticationFilter.getCtfRole(user).isEmpty()) return null

            val totalPrinted = getTotalPrinted(shortUser)

            val currentSemester = Semester.current
            // TODO: incorporate summer escape into mapper
            val semesters = user.getSemesters()
                .filter { it.season != Season.SUMMER } // we don't add quota for the summer
                .filter { it >= Semester.fall(2016) } // TEPID didn't exist before fall 2016
                .filter { it <= currentSemester } // only add quota for valid semesters

            val newMaxQuota = semesters.map { semester ->
                /*
                 * The following mapper allows you to customize
                 * The quota/semester
                 *
                 * Granted that semesters are comparable,
                 * you may specify ranges (inclusive) when matching
                 */

                // for NUS, which has a separate contract
                if (user.groups.contains(AdGroup("520-NUS Users")) && semester > Semester.fall(2018)) {
                    return@map 1000
                }

                when {
                    semester == Semester.fall(2016) -> 500 // the first semester had 500 pages only
                    (semester > Semester.fall(2016) && semester < Semester.fall(2019)) -> 1000 // semesters used to add 1000 pages to the base quota
                    else -> 250 // then we came to our senses
                }
            }.sum()

            val quota = max(newMaxQuota - totalPrinted, 0)

            return QuotaData(
                shortUser = shortUser,
                quota = quota,
                maxQuota = newMaxQuota,
                totalPrinted = totalPrinted,
                semesters = semesters
            )
        }

        /**
         * Given a shortUser, query for the number of pages remaining
         * Returns 0 if an error has occurred
         */
        fun getQuota(user: FullUser?): Int = getQuotaData(user)?.quota ?: 0

        fun getTotalPrinted(shortUser: String?) =
            if (shortUser == null) 0
            else DB.getTotalPrintedCount(shortUser)
    }
}
