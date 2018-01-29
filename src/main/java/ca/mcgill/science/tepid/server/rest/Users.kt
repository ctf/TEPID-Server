package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.bindings.CTFER
import ca.mcgill.science.tepid.models.bindings.ELDER
import ca.mcgill.science.tepid.models.bindings.USER
import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.util.*
import ca.mcgill.science.tepid.server.util.SessionManager.ADMIN
import ca.mcgill.science.tepid.server.util.SessionManager.LOCAL
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
        val rows = CouchDb.path(CouchDb.MAIN_VIEW, "localAdmins").getObject().get("rows")
        rows.size() > 0
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
        newAdmin.password = hashedPw
        newAdmin.role = ADMIN
        newAdmin.authType = LOCAL
        newAdmin.activeSince = System.currentTimeMillis()
        newAdmin.displayName = "${newAdmin.givenName} ${newAdmin.lastName}"
        newAdmin.salutation = newAdmin.givenName
        newAdmin.longUser = newAdmin.email
        return CouchDb.path("u$shortUser").putJson(newAdmin)
    }

    @PUT
    @Path("/{sam}/exchange")
    @RolesAllowed(CTFER, ELDER)
    @Consumes(MediaType.APPLICATION_JSON)
    fun setExchange(@PathParam("sam") sam: String, exchange: Boolean) {
        SessionManager.setExchangeStudent(sam, exchange)
    }

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
        return CouchDb.path("u${user.shortUser}").putJson(user)
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
        else
            getQuota(shortUser)
    }

    fun getQuota(shortUser: String?): Int {
        shortUser ?: return 0

        val user = SessionManager.queryUser(shortUser, null)
        if (user == null || SessionManager.getRole(user).isEmpty()) return 0

        val totalPrinted = CouchDb.path(CouchDb.MAIN_VIEW, "totalPrinted").query("key" to "\"$shortUser\"").getObject()
                .get("rows").get(0).get("value").get("sum").asInt(0)

        val oldMaxQuota = oldMaxQuota(shortUser)

        val currentSemester = Semester.current

        val newMaxQuota = user.getSemesters()
                .filter { it.season != Season.SUMMER } // we don't add quota for the summer
                .filter { it >= fall(2016) }      // TEPID didn't exist before fall 2016
                .filter { it <= currentSemester }      // only add quota for valid semesters
                .map { semester ->
                    /*
                     * The following mapper allows you to customize
                     * The quota/semester
                     *
                     * Granted that semesters are comparable,
                     * you may specify ranges (inclusive) when matching
                     */
                    when (semester) {
                        fall(2016) -> 500         // the first semester had 500 pages only
                        else -> 1000                   // to date, every semester will add 1000 pages to the base quota
                    }
                }.sum()

        if (oldMaxQuota > newMaxQuota)
            log.warn("Old quota $oldMaxQuota > new quota $newMaxQuota for $shortUser")
        return Math.max(Math.max(oldMaxQuota, newMaxQuota) - totalPrinted, 0)
    }

    private fun fall(year: Int) = Semester(Season.FALL, year)
    private fun winter(year: Int) = Semester(Season.WINTER, year)

    private fun oldMaxQuota(shortUser: String): Int {
        try {
            val rows = CouchDb.path(CouchDb.MAIN_VIEW, "totalPrinted").query("key" to "\"$shortUser\"").getObject().get("rows")
            val ej = rows.get(0).get("value").get("earliestJob").asLong(-1)
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

    @GET
    @Path("/autosuggest/{like}")
    @RolesAllowed(CTFER, ELDER)
    @Produces(MediaType.APPLICATION_JSON)
    fun ldapAutoSuggest(@PathParam("like") like: String, @QueryParam("limit") limit: Int): List<User> {
        val resultsPromise = SessionManager.autoSuggest(like, limit)
        return resultsPromise.getResult(20000).map(FullUser::toUser) // todo check if we should further simplify to userquery
    }

    private companion object : WithLogging()

}
