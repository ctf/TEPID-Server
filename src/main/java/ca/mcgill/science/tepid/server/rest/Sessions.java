package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.SessionRequest;
import ca.mcgill.science.tepid.common.User;
import ca.mcgill.science.tepid.server.util.SessionManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/sessions")
public class Sessions {
	
	@GET
	@Path("/{user}/{token}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSession(@PathParam("user") String user, @PathParam("token") String token) {
		try {
			String username = user.split("@")[0];
			boolean longUser = username.contains(".");
			Session s = null;
			if (SessionManager.getInstance().valid(token)) {
				s = SessionManager.getInstance().get(token);
				if (!(longUser?s.getUser().longUser:s.getUser().shortUser).equals(username) || s.getExpiration().getTime() < System.currentTimeMillis()) {
					s = null;
				}
			}
			if (s != null) {
				s.setRole(SessionManager.getInstance().getRole(s.getUser()));
				return Response.ok(s).build();
			}
		} catch (Exception e) {
			System.err.println("Exception caught: " + e.getClass().getCanonicalName()); 
		}
		return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Session token is no longer valid\"}").build();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response startSession(SessionRequest req) {
		try {
			String username = req.username.split("@")[0];
			User user = SessionManager.getInstance().authenticate(username, req.password);
			//persistent sessions expire in 768 hours (32 days), permanent (printing) sessions expire in 35040 hours (4 years), other sessions expire in 24 hours
			Session s = user != null ? SessionManager.getInstance().start(user, req.permanent ? 35040 : (req.persistent ? 768 : 24)) : null;
			if (s != null) { 
				s.setPersistent(req.persistent);
				s.setRole(SessionManager.getInstance().getRole(user));
				return Response.ok(s).build();
			}
		} catch (Exception e) {
			System.err.println("Exception caught: " + e.getClass().getCanonicalName());
			e.printStackTrace();
		}
		return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Username or password incorrect\"}").build();
	}
	
	@DELETE
	@Path("/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public void endSession(@PathParam("id") String id) { 
		SessionManager.getInstance().end(id);
	}
}
