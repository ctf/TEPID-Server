package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.common.Destination;
import ca.mcgill.science.tepid.common.Destination.DestinationTicket;
import ca.mcgill.science.tepid.common.Session;
import ca.mcgill.science.tepid.common.ViewResultSet;
import ca.mcgill.science.tepid.common.ViewResultSet.Row;
import ca.mcgill.science.tepid.server.util.CouchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/destinations")
public class Destinations {
    private final WebTarget couchdb = CouchClient.getTepidWebTarget();

    /**
     * Put destination map to CouchDb
     *
     * @param destinations map of destinations
     * @return post result
     */
    @PUT
    @RolesAllowed({"elder"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String putDestinations(Map<String, Destination> destinations) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putArray("docs").addAll(new ObjectMapper().convertValue(destinations.values(), ArrayNode.class));
        return couchdb.path("_bulk_docs").request().post(Entity.entity(root, MediaType.APPLICATION_JSON)).readEntity(String.class);
    }

    /**
     * Retrieves map of room names as {@link String} and their details in {@link Destination}
     *
     * @param ctx context
     * @return Map of data
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"elder", "ctfer", "user"})
    public Map<String, Destination> getDestinations(@Context ContainerRequestContext ctx) {
        Session session = (Session) ctx.getProperty("session");
        List<Row<String, Destination>> rows = couchdb.path("_design/main/_view").path("destinations")
                .request(MediaType.APPLICATION_JSON).get(DestinationResultSet.class).rows;
        Map<String, Destination> out = new HashMap<>();
        for (Row<String, Destination> r : rows) {
            Destination d = r.value;
            if (!session.getRole().equals("elder")) {
                d.setDomain(null);
                d.setUsername(null);
                d.setPassword(null);
            }
            out.put(r.value.getId(), d);
        }
        return out;
    }

    @POST
    @Path("/{dest}")
    @RolesAllowed({"ctfer", "elder"})
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setStatus(@PathParam("dest") String id, DestinationTicket ticket, @Context ContainerRequestContext crc) {
        Session session = (Session) crc.getProperty("session");
        ticket.user = session.getUser();
        Destination dest = null;
        try {
            dest = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(Destination.class);
        } catch (Exception ignored) {
        }
        if (dest == null) return Response.status(404).entity("Could not find destination " + id).build();
        if (ticket.up) {
            dest.setUp(true);
            dest.setTicket(null);
        } else {
            dest.setUp(false);
            dest.setTicket(ticket);
        }
        couchdb.path(id).request().put(Entity.entity(dest, MediaType.APPLICATION_JSON));
        return Response.ok(id + " marked as " + (ticket.up ? "up" : "down")).build();
    }


    @DELETE
    @Path("/{dest}")
    @RolesAllowed("elder")
    @Produces(MediaType.APPLICATION_JSON)
    public String deleteQueue(@PathParam("dest") String destination) {
        String rev = couchdb.path(destination).request(MediaType.APPLICATION_JSON).get().readEntity(ObjectNode.class).get("_rev").asText();
        return couchdb.path(destination).queryParam("rev", rev).request().delete().readEntity(String.class);
    }

    private static class DestinationResultSet extends ViewResultSet<String, Destination> {
    }

}
