package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.util.Config
import ca.mcgill.science.tepid.server.util.PublicConfig
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

/**
 * Created by Allan Wang on 2017-11-19.
 *
 * Public endpoint to get immediate information about the current build
 * This is also unaffected by CouchDb, and will help with debugging
 */
@Path("/about")
class About {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getAbout(): PublicConfig = Config.PUBLIC

}