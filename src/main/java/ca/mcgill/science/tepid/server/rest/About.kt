package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.models.data.About
import ca.mcgill.science.tepid.server.server.Config
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType

/**
 * Endpoint to get information about the current build
 * Useful for debugging
 */
@Path("/about")
class About {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getAbout(): About = Config.PUBLIC

    @GET
    @Path("/api")

    fun api(@Context ctxRequest: HttpServletRequest, @Context ctxResponse: HttpServletResponse) {

            ctxRequest.getRequestDispatcher("/swagger-ui.jsp").forward(ctxRequest,ctxResponse)
    }

}
