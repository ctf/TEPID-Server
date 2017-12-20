package ca.mcgill.science.tepid.server.loadbalancers;

import ca.mcgill.science.tepid.models.data.Destination;
import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.util.QueueManager;
import ca.mcgill.science.tepid.server.util.WebTargetsKt;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

public class FiftyFifty extends LoadBalancer {

    private final WebTarget couchdb = WebTargetsKt.getCouchdbOld();
    private final List<Destination> destinations;
    private int currentDest;
    private boolean allDown = true;
    private QueueManager qM;

    public FiftyFifty(QueueManager qm) {
        super(qm);
        qM = qm;
        this.destinations = new ArrayList<>(qm.queueConfig.destinations.size());
        for (String d : qm.queueConfig.destinations) {
            Destination dest = couchdb.path(d).request(MediaType.APPLICATION_JSON).get(Destination.class);
            destinations.add(dest);
            if (dest.isUp()) this.allDown = false;
        }
    }

    // hack fix until we rewrite the load balancer, prevents needing to restart TEPID when printer status changes
    private void refreshDestinationsStatus() {
        destinations.clear(); // clear out the old Destination objects
        for (String d : qM.queueConfig.destinations) {
            Destination dest = couchdb.path(d).request(MediaType.APPLICATION_JSON).get(Destination.class);
            destinations.add(dest); // replace with shiny new Destination objects
            if (dest.isUp()) this.allDown = false;
        }
        // maybe we should be concerned about the efficiency of a db query for every dest in the queue on every print job...
    }


    /**
     * Cycle through available {@link Destination} and sends job to the first one available
     *
     * @param j PrintJob
     * @return result
     */
    @Override
    public LoadBalancerResults processJob(PrintJob j) {
        refreshDestinationsStatus();
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
