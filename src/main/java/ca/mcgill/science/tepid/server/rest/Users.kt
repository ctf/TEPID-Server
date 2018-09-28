package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.*
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Season
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.getSession
import ca.mcgill.science.tepid.server.util.text
import ca.mcgill.science.tepid.utils.WithLogging
import org.mindrot.jbcrypt.BCrypt
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import javax.annotation.security.RolesAllowed
import javax.ws.rs.*
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
        log.error("localAdmin check failed", e)
        false
    }

    @GET
    @Path("/{sam}")
    @RolesAllowed(USER, CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun queryLdap(@PathParam("sam") shortUser: String, @QueryParam("pw") pw: String?, @Context crc: ContainerRequestContext, @Context uriInfo: UriInfo): Response {
        val session = crc.getSession()
        val user = SessionManager.queryUser(shortUser, pw)
        //TODO security wise, should the second check not happen before the first?
        if (user == null) {
            log.warn("Could not find user {}.", shortUser)
            throw NotFoundException(Response.status(404).entity("Could not find user " + shortUser).type(MediaType.TEXT_PLAIN).build())
        }
        if (session.role == USER && session.user.shortUser != user.shortUser) {
            log.warn("Unauthorized attempt to lookup {} by user {}.", shortUser, session.user.longUser)
            return Response.Status.UNAUTHORIZED.text("You cannot access this resource")
        }
        try {
            if (user.shortUser != shortUser && !uriInfo.queryParameters.containsKey("noRedirect")) {
                return Response.seeOther(URI("users/" + user.shortUser)).build()
            }
        } catch (ignored: URISyntaxException) {
        }
        return Response.ok(user).build()
    }

    @PUT
    @Path("/{sam}")
    @Produces(MediaType.APPLICATION_JSON)
    fun createLocalAdmin(@PathParam("sam") shortUser: String, newAdmin: FullUser, @Context req: ContainerRequestContext, @Context uriInfo: UriInfo): Response {
        if (this.adminConfigured()) {
            val session = req.getSession()
            log.warn("Unauthorized attempt to add a local admin by {}.", session.user.longUser)
            return Response.Status.UNAUTHORIZED.text("Local admin already exists")
        }
        val hashedPw = BCrypt.hashpw(newAdmin.password, BCrypt.gensalt())
        newAdmin.shortUser = shortUser
        newAdmin.password = hashedPw
        newAdmin.role = ADMIN
        newAdmin.authType = LOCAL
        newAdmin.activeSince = System.currentTimeMillis()
        newAdmin.displayName = "${newAdmin.givenName} ${newAdmin.lastName}"
        newAdmin.salutation = newAdmin.givenName
        newAdmin.longUser = newAdmin.email
        return DB.putUser(newAdmin)
    }

    /**
     * Attempts to add the supplied sam to the exchange group
     * @return updated status of the user; false if anything goes wrong
     */
    @PUT
    @Path("/{sam}/exchange")
    @RolesAllowed(CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setExchange(@PathParam("sam") sam: String, exchange: Boolean): Boolean =
            SessionManager.setExchangeStudent(sam, exchange)

    /**
     * Abstract implementation of modifying user data
     * Note that this is called from an endpoint where [ctx] holds a session (valid or not),
     * and where the roles allowed are at least of level "user"
     */
    private inline fun putUserData(sam: String, ctx: ContainerRequestContext, action: (user: FullUser) -> Unit): Response {
        val session = ctx.getSession()
        val user = SessionManager.queryUser(sam, null) ?: return Response.Status.NOT_FOUND.text("User $sam not found")
        if (session.role == USER && session.user.shortUser != user.shortUser)
            return Response.Status.UNAUTHORIZED.text("You cannot change this resource")
        action(user)
        return DB.putUser(user)
    }


    @PUT
    @Path("/{sam}/nick")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setNick(@PathParam("sam") sam: String, nick: String, @Context ctx: ContainerRequestContext): Response = putUserData(sam, ctx) {
        it.nick = if (nick.isBlank()) null else nick
        log.debug("Setting nick for ${it.shortUser} to ${it.nick}")
    }

    @PUT
    @Path("/{sam}/jobExpiration")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setJobExpiration(@PathParam("sam") sam: String, jobExpiration: Long, @Context ctx: ContainerRequestContext): Response = putUserData(sam, ctx) {
        it.jobExpiration = jobExpiration
        log.trace("Job expiration for ${it.shortUser} set to $jobExpiration")
    }

    @PUT
    @Path("/{sam}/color")
    @RolesAllowed(USER, CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setColor(@PathParam("sam") sam: String, color: Boolean, @Context ctx: ContainerRequestContext): Response = putUserData(sam, ctx) {
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
            val user = SessionManager.queryUser(shortUser, null)
            getQuota(user)
        }
    }

    @GET
    @Path("/{sam}/quota/debug")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun getQuotaDebug(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext): QuotaData {
        val user = SessionManager.queryUser(shortUser, null)
        return getQuotaData(user) ?: failNotFound("Could not calculate quota")
    }

    @POST
    @Path("/{sam}/refresh")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.TEXT_PLAIN)
    fun forceRefresh(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext) {
        try {
            SessionManager.refreshUser(shortUser)
            SessionManager.invalidateSessions(shortUser)
        } catch (e:Exception){
            throw NotFoundException(Response.status(404).entity("Could not find user " + shortUser).type(MediaType.TEXT_PLAIN).build())
        }
    }

    @GET
    @Path("/autosuggest/{like}")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun ldapAutoSuggest(@PathParam("like") like: String, @QueryParam("limit") limit: Int): List<User> {
        val resultsPromise = SessionManager.autoSuggest(like, limit)
        return resultsPromise.getResult(20000).map(FullUser::toUser) // todo check if we should further simplify to userquery
    }

    companion object : WithLogging() {

        data class QuotaData(val shortUser: String,
                             val quota: Int,
                             val maxQuota: Int,
                             val oldMaxQuota: Int,
                             val totalPrinted: Int,
                             val semesters: List<Semester>)

        fun getQuotaData(user: FullUser?): QuotaData? {
            val shortUser = user?.shortUser ?: return null

            if (AuthenticationFilter.getCtfRole(user).isEmpty()) return null

            val totalPrinted = getTotalPrinted(shortUser)

            val oldMaxQuota = oldMaxQuota(shortUser)

            val currentSemester = Semester.current
            // TODO: incorporate summer escape into mapper
            val semesters = user.getSemesters()
                    .filter { it.season != Season.SUMMER } // we don't add quota for the summer
                    .filter { it >= Semester.fall(2016) }      // TEPID didn't exist before fall 2016
                    .filter { it <= currentSemester }      // only add quota for valid semesters

            val newMaxQuota = semesters.map { semester ->
                /*
                 * The following mapper allows you to customize
                 * The quota/semester
                 *
                 * Granted that semesters are comparable,
                 * you may specify ranges (inclusive) when matching
                 */
                when (semester) {
                    Semester.fall(2016) -> 500         // the first semester had 500 pages only
                    else -> 1000                   // to date, every semester will add 1000 pages to the base quota
                }
            }.sum()

            if (oldMaxQuota > newMaxQuota)
                log.warn("Old quota $oldMaxQuota > new quota $newMaxQuota for $shortUser")
            val quota = Math.max(newMaxQuota - totalPrinted, 0)

            return QuotaData(shortUser = shortUser,
                    quota = quota,
                    maxQuota = newMaxQuota,
                    oldMaxQuota = oldMaxQuota,
                    totalPrinted = totalPrinted,
                    semesters = semesters)
        }

        /**
         * Given a shortUser, query for the number of pages remaining
         * Returns 0 if an error has occurred
         */
        fun getQuota(user: FullUser?): Int = getQuotaData(user)?.quota ?: 0

        fun getTotalPrinted(shortUser: String?) =
                if (shortUser == null) 0
                else DB.getTotalPrintedCount(shortUser)

        private fun oldMaxQuota(shortUser: String): Int {
            try {
                val ej = DB.getEarliestJobTime(shortUser)
                if (ej == -1L) {
                    log.debug("Old quota for new user $shortUser")
                    return 1000
                }
                val d1 = Calendar.getInstance()
                val d2 = Calendar.getInstance()
                d1.timeInMillis = ej
                val m1 = d1.get(Calendar.MONTH) + 1
                val y1 = d1.get(Calendar.YEAR)
                val m2 = d2.get(Calendar.MONTH) + 1
                val y2 = d2.get(Calendar.YEAR)
                return (y2 - y1) * 1000 + (if (m2 > 8 && (y1 != y2 || m1 < 8)) 1500 else 1000)
            } catch (e: Exception) {
                log.error("Old quota fetch failed", e)
                return 1000
            }
        }
    }

}
