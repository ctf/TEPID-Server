package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Session
import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.server.util.*
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
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun queryLdap(@PathParam("sam") shortUser: String, @QueryParam("pw") pw: String?, @Context crc: ContainerRequestContext, @Context uriInfo: UriInfo): Response {
        val session = crc.getSession(log) ?: return INVALID_SESSION_RESPONSE
        val user = SessionManager.queryUser(shortUser, pw)
        //TODO security wise, should the second check not happen before the first?
        if (user == null) {
            log.warn("Could not find user {}.", shortUser)
            throw NotFoundException(Response.status(404).entity("Could not find user " + shortUser).type(MediaType.TEXT_PLAIN).build())
        }
        if (session.role == "user" && session.user.shortUser != user.shortUser) {
            log.warn("Unauthorized attempt to lookup {} by user {}.", shortUser, session.user.longUser)
            return Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").type(MediaType.TEXT_PLAIN).build()
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
            log.warn("Unauthorized attempt to add a local admin by {}.", (req.getProperty("session") as Session).user.longUser)
            return Response.status(Response.Status.UNAUTHORIZED).entity("Local admin already exists").type(MediaType.TEXT_PLAIN).build()
        }
        val hashedPw = BCrypt.hashpw(newAdmin.password, BCrypt.gensalt())
        newAdmin.password = hashedPw
        newAdmin.role = "admin"
        newAdmin.authType = "local"
        newAdmin.activeSince = Date()
        newAdmin.displayName = "${newAdmin.givenName} ${newAdmin.lastName}"
        newAdmin.salutation = newAdmin.givenName
        newAdmin.longUser = newAdmin.email

        val res = CouchDb.path("u$shortUser").putJsonAndRead(newAdmin)
        log.info("Added local admin {}.", newAdmin.shortUser)
        return Response.ok(res).build()
    }

    @PUT
    @Path("/{sam}/exchange")
    @RolesAllowed("ctfer", "elder")
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
        val session = ctx.getSession(log) ?: return INVALID_SESSION_RESPONSE
        val user = SessionManager.queryUser(sam, null) ?: return Response.Status.NOT_FOUND.text("User $sam not found")
        if (session.role == "user" && session.user.shortUser != user.shortUser)
            return Response.Status.UNAUTHORIZED.text("You cannot change this resource")
        action(user)
        val res = CouchDb.path("u${user.shortUser}").putJsonAndRead(user)
        return Response.ok(res).build()
    }


    @PUT
    @Path("/{sam}/nick")
    @RolesAllowed("user", "ctfer", "elder")
    @Consumes(MediaType.APPLICATION_JSON)
    fun setNick(@PathParam("sam") sam: String, nick: String, @Context ctx: ContainerRequestContext): Response = putUserData(sam, ctx) {
        it.nick = if (nick.isBlank()) null else nick
        println("Setting nick for ${it.shortUser} to ${it.nick}")
    }

    @PUT
    @Path("/{sam}/jobExpiration")
    @RolesAllowed("user", "ctfer", "elder")
    @Consumes(MediaType.APPLICATION_JSON)
    fun setJobExpiration(@PathParam("sam") sam: String, jobExpiration: Long, @Context ctx: ContainerRequestContext): Response = putUserData(sam, ctx) {
        it.jobExpiration = jobExpiration
        println("Job expiration for ${it.shortUser} set to $jobExpiration")
    }

    @PUT
    @Path("/{sam}/color")
    @RolesAllowed("user", "ctfer", "elder")
    @Consumes(MediaType.APPLICATION_JSON)
    fun setColor(@PathParam("sam") sam: String, color: Boolean, @Context ctx: ContainerRequestContext): Response = putUserData(sam, ctx) {
        it.colorPrinting = color
        println("Set color for ${it.shortUser} to ${it.colorPrinting}")
    }

    @GET
    @Path("/{sam}/quota")
    @RolesAllowed("user", "ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun getQuota(@PathParam("sam") shortUser: String, @Context ctx: ContainerRequestContext): Int {
        val session = ctx.getSession(log) ?: return -1
        return if (session.role == "user" && session.user.shortUser != shortUser)
            -1
        else
            getQuota(shortUser)
    }

    fun getQuota(shortUser: String?): Int {
        shortUser ?: return 0
        var totalPrinted = 0
        var earliestJob: Date? = null
        try {
            // todo not sure if this is the best implementation. Any node can be null, but I guess we're just catching it and moving forward afterwards
            val rows = CouchDb.path(CouchDb.MAIN_VIEW, "totalPrinted").query("key" to "\"$shortUser\"").getObject().get("rows")
            totalPrinted = rows.get(0).get("value").get("sum").asInt(0)
            val ej = rows.get(0).get("value").get("earliestJob").asLong(0)
            earliestJob = Date(ej)
        } catch (e: Exception) {
            //			e.printStackTrace();
        }

        val user = SessionManager.queryUser(shortUser, null)
        if (user == null || SessionManager.getRole(user).isEmpty()) return 0

        //todo verify
        if (earliestJob == null) return 1000 // init to 1000 for new users
        val d1 = Calendar.getInstance()
        val d2 = Calendar.getInstance()
        d1.time = earliestJob
        val m1 = d1.get(Calendar.MONTH) + 1
        val y1 = d1.get(Calendar.YEAR)
        val m2 = d2.get(Calendar.MONTH) + 1
        val y2 = d2.get(Calendar.YEAR)
        return (y2 - y1) * 1000 + (if (m2 > 8 && (y1 != y2 || m1 < 8)) 1500 else 1000) - totalPrinted
    }

    @GET
    @Path("/autosuggest/{like}")
    @RolesAllowed("ctfer", "elder")
    @Produces(MediaType.APPLICATION_JSON)
    fun ldapAutoSuggest(@PathParam("like") like: String, @QueryParam("limit") limit: Int): List<User> {
        val resultsPromise = SessionManager.autoSuggest(like, limit)
        return resultsPromise.getResult(20000).map(FullUser::toUser) // todo check if we should further simplify to userquery
    }

    private companion object : WithLogging()

}
