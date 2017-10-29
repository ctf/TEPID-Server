package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.server.util.CouchClientKt;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

@Path("/endpoints")
public class EndpointManager {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ctfer", "elder"})
    public String getAuthorizedEndpoints() {
        WebTarget tgt = CouchClientKt.getTemdb().path("_design/main/_view").path("authorizedEndpoints");
        return tgt.request(MediaType.APPLICATION_JSON).get(String.class);
    }
}
