package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.util.temdb
import javax.annotation.security.RolesAllowed
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/endpoints")
class EndpointManager {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ctfer", "elder")
    fun getAuthorizedEndpoints(): String {
        val tgt = temdb.path("_design/main/_view").path("authorizedEndpoints")
        return tgt.request(MediaType.APPLICATION_JSON).get(String::class.java)
    }
}
