package ca.mcgill.science.tepid.server.printing.loadbalancers;

import ca.mcgill.science.tepid.models.data.FullDestination;
import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.db.DbLayer;
import ca.mcgill.science.tepid.server.db.DbLayerKt;
import ca.mcgill.science.tepid.server.printing.QueueManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class LoadBalancer {

    protected final Logger log;
    protected static final DbLayer db = DbLayerKt.getDB();

    protected final QueueManager queueManager;
    protected final List<FullDestination> destinations;
    protected boolean allDown = true;

    public void refreshDestinations(){
        allDown = true;
        destinations.clear(); // clear out the old Destination objects
        for (String d : queueManager.printQueue.getDestinations()) {
            FullDestination dest = db.getDestination(d);
            destinations.add(dest); // replace with shiny new Destination objects

            boolean up = dest.getUp();
            if (up) this.allDown = false;
            log.trace("Checking status {\'dest\':\'{}\', \'getUp\':\'{}\'}", dest.getName(), up);
        }
        // maybe we should be concerned about the efficiency of a db query for every dest in the queue on every print job...
    }

    public LoadBalancer(QueueManager qm) {
        this.queueManager = qm;
        log = LogManager.getLogger("Queue - " + qm.printQueue.getName());
        this.destinations = new ArrayList<>(qm.printQueue.getDestinations().size());
        refreshDestinations();
        log.trace("Initialized with {}; allDown {}", destinations.size(), allDown);
    }

    public abstract LoadBalancerResults processJob(PrintJob j);

    public static class LoadBalancerResults {
        public String destination;
        public long eta;
    }

    /*
     * Registry
     */

    private static final Map<String, Function<QueueManager, ? extends LoadBalancer>> loadBalancerFactories = new HashMap<>();
    public static void registerLoadBalancer(String name, Function<QueueManager,? extends LoadBalancer> loadBalancerFactory){
        loadBalancerFactories.put(name, loadBalancerFactory);
    }
    public static Set<Map.Entry<String, Function<QueueManager, ? extends LoadBalancer>>> getLoadBalancerFactories(){
        return loadBalancerFactories.entrySet();
    }

    public static Function<QueueManager,? extends LoadBalancer> getLoadBalancerFactory(String name) {
        return loadBalancerFactories.getOrDefault(name, FiftyFifty::new);
    }
}
