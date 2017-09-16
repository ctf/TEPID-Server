package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.SignUp;
import ca.mcgill.science.tepid.common.User;
import ca.mcgill.science.tepid.server.util.SessionManager;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.glassfish.jersey.jackson.JacksonFeature;
import shared.Config;
import shared.ConfigKeys;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Path("/office-hours")
public class OfficeHours {
	private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
	private final WebTarget couchdb = client.target("http://admin:" + Config.getSetting(ConfigKeys.COUCHDB_PASSWORD) + "@localhost:5984/tepid");

	@GET
	@Path("/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed({"ctfer", "elder"})
	public SignUp getSignUp(@PathParam("name") String name) {
		WebTarget tgt = couchdb.path("_design/main/_view").path("signupByName").queryParam("key", "\"" + name + "\"");
		List<SignUpResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
		if (rows.isEmpty()) return null;
		else return rows.get(0).value;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed({"ctfer", "elder"})
	public Collection<SignUp> getSignUps() {
		WebTarget tgt = couchdb.path("_design/main/_view").path("signup");
		List<SignUpResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
		Collection<SignUp> out = new ArrayList<SignUp>();
		for (SignUpResult.Row r : rows) {
			out.add(r.value);
			User u = SessionManager.getInstance().queryUserCache(r.value.getName());
			r.value.setNickname(u.salutation);
		}
		return out;
	}
	
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SignUpResult {
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Row {
			@JsonProperty("value")
			SignUp value;
		}
		@JsonProperty("rows")
		List<Row> rows;
	}
	
	@POST
	@Path("/{name}/{givenName}")
	@RolesAllowed({"ctfer", "elder"})
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response setSignUp(@PathParam("name") String name, @PathParam("givenName") String givenName, Map<String, String[]> slots) {
		WebTarget tgt = couchdb.path("_design/main/_view").path("signupByName").queryParam("key", "\"" + name + "\"");
		List<SignUpResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
		if (rows.isEmpty()) {
			SignUp signup = new SignUp();
			signup.setGivenName(givenName); //givenName
			System.out.println(givenName);
			signup.setName(name);
			signup.setType("signup");
			signup.setSlots(slots);
			couchdb.request().post(Entity.entity(signup, MediaType.APPLICATION_JSON));
			rows = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
		}
		SignUp signupTmp = rows.get(0).value;
		signupTmp.setSlots(slots);
		String id = signupTmp.getId();
		couchdb.path(id).request().put(Entity.entity(signupTmp, MediaType.APPLICATION_JSON));
		return Response.ok("Sign-up successful").build();
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/_changes")
	public void getChanges(@Context UriInfo uriInfo, @Suspended AsyncResponse ar) {
		WebTarget target = couchdb.path("_changes").queryParam("filter", "main/signUp");
		MultivaluedMap<String, String> qp = uriInfo.getQueryParameters();
		if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"));
		if (qp.containsKey("since")) target = target.queryParam("since", qp.getFirst("since"));
		String changes = target.request().get(String.class);
		if (!ar.isDone() && !ar.isCancelled()) {
			ar.resume(changes);
		}
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/on-duty/{timeSlot}")
	public List<SignUp> onDuty (@PathParam("timeSlot") String timeSlot)
	{
		WebTarget tgt = couchdb.path("_design/main/_view")
								.path("onDuty")
								.queryParam("key", "\"" + timeSlot + "\"");
		List<SignUpResult.Row> rows = tgt.request(MediaType.APPLICATION_JSON).get(SignUpResult.class).rows;
		
		List<SignUp> out = new ArrayList<SignUp>(rows.size());
		
		for (SignUpResult.Row r : rows)
		{
			out.add(r.value);
		}
		return out;
	}
	
}
