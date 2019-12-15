package ca.mcgill.science.tepid.server.printing.loadbalancers;

import ca.mcgill.science.tepid.models.data.Destination;
import ca.mcgill.science.tepid.models.data.FullDestination;
import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.db.DbLayer;
import ca.mcgill.science.tepid.server.db.DbLayerKt;
import ca.mcgill.science.tepid.server.printing.QueueManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class FiftyFifty extends LoadBalancer {

    private final Logger log;

    public static final String name = "fiftyfifty";
    public static final DbLayer db = DbLayerKt.getDB();
    private final List<FullDestination> destinations;
    private int currentDest;
    private boolean allDown = true;
    private QueueManager qM;

    static {
        LoadBalancer.registerLoadBalancer(name, FiftyFifty.class);
    }

    public FiftyFifty(QueueManager qm) {
        super(qm);
        qM = qm;
        log = LogManager.getLogger("Queue - " + qm.printQueue.getName());
        this.destinations = new ArrayList<>(qm.printQueue.getDestinations().size());
        for (String d : qm.printQueue.getDestinations()) {
            FullDestination dest = db.getDestination(d);
            destinations.add(dest);
            if (dest.getUp()) this.allDown = false;
        }
        log.trace("Initialized with {}; allDown {}", destinations.size(), allDown);
    }

    // hack fix until we rewrite the load balancer, prevents needing to restart TEPID when printer status changes
    private void refreshDestinationsStatus() {
        this.allDown = true;
        destinations.clear(); // clear out the old Destination objects
        for (String d : qM.printQueue.getDestinations()) {
            FullDestination dest = db.getDestination(d);
            destinations.add(dest); // replace with shiny new Destination objects

            boolean up = dest.getUp();
            if (up) this.allDown = false;
            log.trace("Checking status {\'dest\':\'{}\', \'getUp\':\'{}\'}", dest.getName(), up);

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
        if (allDown) {
            log.warn("Rejecting job {} as all {} printers are down", j.getId(), destinations.size());
            return null;
        }
        do currentDest = (currentDest + 1) % destinations.size(); while (!destinations.get(currentDest).getUp());
        LoadBalancerResults lbr = new LoadBalancerResults();
        lbr.destination = destinations.get(currentDest).getId();
        FullDestination dest = db.getDestination(lbr.destination);
        lbr.eta = getEta(j, dest);
        log.trace("Load balancer sending job to destination {\'LoadBalancer\':\'{}\', \'job\':\'{}\', \'destination\':\'{}\'} ", qM.printQueue.getName(), j.get_id(), dest.getName());
        return lbr;
    }

    /**
     * Retrieve estimated eta based on existing queue
     *
     * @param j current print job
     * @param d destination for print
     * @return long for estimation
     */
    private long getEta(PrintJob j, FullDestination d) {
        long eta = Math.max(queueManager.getEta(d.getId()), System.currentTimeMillis());
        log.trace("current max: " + eta);
        eta += Math.round(j.getPages() / (double) d.getPpm() * 60.0 * 1000.0);
        log.debug("new eta (" + d.getPpm() + "ppm): " + eta);
        return eta;
    }

}
