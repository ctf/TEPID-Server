package ca.mcgill.science.tepid.server.rest;

import ca.mcgill.science.tepid.server.util.CouchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/barcode")
public class Barcode {
    /**
     * Listen for next barcode event
     *
     * @return JsonNode once event is received
     */
    @GET
    @Path("/_wait")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonNode getBarcode() {
        ObjectNode change = CouchClient.getBarcodesWebTarget().path("_changes").queryParam("feed", "longpoll").queryParam("since", "now").queryParam("include_docs", "true").request(MediaType.APPLICATION_JSON).get(ObjectNode.class);
        System.out.println(change.get("results").get(0).get("doc").asText());
        return change.get("results").get(0).get("doc");
    }
}
