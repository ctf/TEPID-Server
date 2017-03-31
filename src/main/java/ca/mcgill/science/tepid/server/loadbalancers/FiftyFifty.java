package ca.mcgill.science.tepid.server.loadbalancers;

import ca.mcgill.science.tepid.common.Destination;
import ca.mcgill.science.tepid.common.PrintJob;
import ca.mcgill.science.tepid.server.util.QueueManager;
import org.glassfish.jersey.jackson.JacksonFeature;
import shared.Config;
import shared.ConfigKeys;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

public class FiftyFifty extends LoadBalancer {

    private final Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
    private final WebTarget couchdb = client.target("http://admin:" + Config.getSetting(ConfigKeys.DB_PASSWORD) + "@localhost:5984/tepid");
    private final List<Destination> destinations;
    private int currentDest;
    private boolean allDown = true;

    public FiftyFifty(QueueManager qm) {
        super(qm);
        this.destinations = new ArrayList<>(qm.queueConfig.destinations.size());
        for (String d : qm.queueConfig.destinations) {
            Destination dest = couchdb.path(d).request(MediaType.APPLICATION_JSON).get(Destination.class);
            destinations.add(dest);
            if (dest.isUp()) this.allDown = false;
        }
    }

    /**
     * Cycle through available {@link Destination} and sends job to the first one available
     *
     * @param j PrintJob
     * @return result
     */
    @Override
    public LoadBalancerResults processJob(PrintJob j) {
        if (allDown) return null;
        do currentDest = (currentDest + 1) % destinations.size(); while (!destinations.get(currentDest).isUp());
        LoadBalancerResults lbr = new LoadBalancerResults();
        lbr.destination = destinations.get(currentDest).getId();
        Destination dest = couchdb.path(lbr.destination).request(MediaType.APPLICATION_JSON).get(Destination.class);
        lbr.eta = getEta(j, dest);
        return lbr;
    }

    /**
     * Retrieve estimated eta based on existing queue
     *
     * @param j current print job
     * @param d destination for print
     * @return long for estimation
     */
    private long getEta(PrintJob j, Destination d) {
        long eta = Math.max(queueManager.getEta(d.getId()), System.currentTimeMillis());
        System.out.println("current max: " + eta);
        eta += Math.round(j.getPages() / (double) d.getPpm() * 60.0 * 1000.0);
        System.out.println("new eta (" + d.getPpm() + "ppm): " + eta);
        return eta;
    }

}
