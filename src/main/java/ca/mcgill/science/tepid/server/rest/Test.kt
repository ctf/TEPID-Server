package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.ErrorResponse
import ca.mcgill.science.tepid.server.util.fail
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Created by Allan Wang on 2018-02-12.
 *
 * Testing sandbox
 */
@Path("/test")
class Test {

    @GET
    @Path("/unauthorized")
    @Produces(MediaType.APPLICATION_JSON)
    fun getUnauthorized(): Nothing =
            fail(Response.Status.UNAUTHORIZED, ErrorResponse(401, "unauthorized message", listOf("optional extra data", "potato")))

}