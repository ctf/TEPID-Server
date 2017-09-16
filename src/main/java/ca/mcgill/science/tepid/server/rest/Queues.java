package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.PrintJob;
import ca.mcgill.science.tepid.common.PrintQueue;
import ca.mcgill.science.tepid.common.Utils;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.loadbalancers.LoadBalancer;
import ca.mcgill.science.tepid.server.util.CouchClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Path("/queues")
public class Queues {
	private final WebTarget couchdb = CouchClient.getTepidWebTarget();

	@PUT
	@RolesAllowed({"elder"})
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public String putQueues(List<PrintQueue> queues) {
		ObjectNode root = JsonNodeFactory.instance.objectNode();
		root.putArray("docs").addAll(new ObjectMapper().convertValue(queues, ArrayNode.class));
		return couchdb.path("_bulk_docs").request().post(Entity.entity(root, MediaType.APPLICATION_JSON)).readEntity(String.class);
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<PrintQueue> getQueues() {
		List<QueueResultSet.Row> rows = couchdb.path("_design/main/_view").path("queues")
		.request(MediaType.APPLICATION_JSON).get(QueueResultSet.class).rows;
		List<PrintQueue> out = new ArrayList<>();
		for (QueueResultSet.Row r : rows) {
			out.add(r.value);
		}
		return out;
	}
	
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class QueueResultSet {
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Row {
			@JsonProperty("value")
			PrintQueue value;
		}
		@JsonProperty("rows")
		List<Row> rows;
	}
	
	@GET
	@Path("/{queue}")
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<PrintJob> listJobs(@PathParam("queue") String queue, @QueryParam("limit") @DefaultValue("-1") int limit) {
		//TODO limit param no longer user, should be replaced by from param in client
		// this should get all jobs in "queue" from the past 2 days
		long from = (new Date()).getTime() - 1000*60*60*24*2; // from 2 days ago
		WebTarget tgt = couchdb
				.path("_design/temp/_view")
				.path("JobsByQueueAndTime")
				.queryParam("descending", true)
				.queryParam("startkey", "[\"" + queue + "\",%7B%7D]")
				.queryParam("endkey", "[\"" + queue + "\"," + from + "]");
		List<Row<List<String>,PrintJob>> rows = tgt.request(MediaType.APPLICATION_JSON).get(JobResultSet.class).rows;

		Collection<PrintJob> out = new ArrayList<>();
		for (Row<List<String>,PrintJob> r : rows) {
			out.add(r.value); 
		}
		return out;
	}
	
	@GET
	@Path("/{queue}/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public PrintJob getJob(@PathParam("queue") String queue, @PathParam("id") String id) {
		PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
		if (!j.getQueueName().equalsIgnoreCase(queue)) return null;
		return j;
	}	
	
	@DELETE
	@Path("/{queue}")
	@RolesAllowed("elder")
	@Produces(MediaType.APPLICATION_JSON)
	public String deleteQueue(@PathParam("queue") String queue) {
		String rev = couchdb.path(queue).request(MediaType.APPLICATION_JSON).get().readEntity(ObjectNode.class).get("_rev").asText();
		return couchdb.path(queue).queryParam("rev", rev).request().delete().readEntity(String.class);
	}
	
	@GET
	@Path("/{queue}/{id}/{file}")
	public Response getAttachment(@PathParam("queue") String queue, @PathParam("id") String id, @PathParam("file") String file) {
		try {
			PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
			if (j == null || !j.getQueueName().equalsIgnoreCase(queue)) {
				return Response.status(404).entity("Could not find job " + id + " in queue " + queue).type(MediaType.TEXT_PLAIN).build();
			}
			Response resp = couchdb.path(id).path(file).request().get();
			if (resp.getStatus() == 200) {
				return Response.ok(resp.readEntity(InputStream.class), resp.getMediaType()).build();
			}
		} catch (Exception e) {
		}
		return Response.status(404).entity("Could not find " + file + " for job " + id).type(MediaType.TEXT_PLAIN).build();
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{queue}/_changes")
	public void getChanges(@PathParam("queue") String queue, @Context UriInfo uriInfo, @Suspended AsyncResponse ar) {
		WebTarget target = couchdb.path("_changes").queryParam("filter", "main/byQueue")
				.queryParam("queue", queue);
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
	@Path("/_changes")
	public String getChanges(@Context UriInfo uriInfo) {
		WebTarget target = couchdb.path("_changes").queryParam("filter", "main/byQueue");
		MultivaluedMap<String, String> qp = uriInfo.getQueryParameters();
		if (qp.containsKey("feed")) target = target.queryParam("feed", qp.getFirst("feed"));
		int since = Utils.intValue(qp.getFirst("since"), -1);
		if (since < 0) {
			since = couchdb.path("_changes").queryParam("filter", "main/byQueue").queryParam("since", 0).request().get(ObjectNode.class).get("last_seq").asInt();
		}
		target = target.queryParam("since", since);
		return target.request().get(String.class);
	}
	
	private static class JobResultSet extends ViewResultSet<List<String>,PrintJob> {}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/loadbalancers")
	public List<String> getLoadBalancers() {
		List<String> out = new ArrayList<>();
		for (Class<? extends LoadBalancer> c : LoadBalancer.loadBalancers) {
			if (!LoadBalancer.class.isAssignableFrom(c)) continue;
			out.add(c.getSimpleName());
		}
		return out;
	}
}
