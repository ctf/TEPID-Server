package ca.mcgill.science.tepid.server.printing.loadbalancers;

import ca.mcgill.science.tepid.models.data.Destination;
import ca.mcgill.science.tepid.models.data.FullDestination;
import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.printing.QueueManager;

public class FiftyFifty extends LoadBalancer {

    public static final String name = "fiftyfifty";
    private int currentDest;

    static {
        LoadBalancer.registerLoadBalancer(name, FiftyFifty::new);
    }

    public FiftyFifty(QueueManager qm) {
        super(qm);
    }


    /**
     * Cycle through available {@link Destination} and sends job to the first one available
     *
     * @param j PrintJob
     * @return result
     */
    @Override
    public LoadBalancerResults processJob(PrintJob j) {
        refreshDestinations();
        if (allDown) {
            log.warn("Rejecting job {} as all {} printers are down", j.getId(), destinations.size());
            return null;
        }
        do currentDest = (currentDest + 1) % destinations.size(); while (!destinations.get(currentDest).getUp());

        String destinationId = destinations.get(currentDest).getId();
        FullDestination dest = db.getDestination(destinationId);

        log.trace("Load balancer sending job to destination {\'LoadBalancer\':\'{}\', \'job\':\'{}\', \'destination\':\'{}\'} ", queueManager.printQueue.getName(), j.get_id(), dest.getName());
        return new LoadBalancerResults(destinationId, getEta(j, dest));
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
