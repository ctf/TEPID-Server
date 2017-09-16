package ca.mcgill.science.tepid.server.rest;

import org.glassfish.jersey.jackson.JacksonFeature;

import ca.mcgill.science.tepid.server.util.CouchClient;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

@Path("/endpoints")
public class EndpointManager {
	private final WebTarget couchdb = CouchClient.getTemWebTarget();
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed({"ctfer", "elder"})
	public String getAuthorizedEndpoints() {
		WebTarget tgt = couchdb.path("_design/main/_view").path("authorizedEndpoints");
		return tgt.request(MediaType.APPLICATION_JSON).get(String.class);
	}
}
