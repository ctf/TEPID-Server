package ca.mcgill.science.tepid.server.printing.loadbalancers;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.printing.QueueManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class LoadBalancer {

    private static final Map<String, Class<? extends LoadBalancer>> loadBalancers = new HashMap<>();
    public static void registerLoadBalancer(String name, Class<? extends LoadBalancer> loadBalancerClass){
        loadBalancers.put(name, loadBalancerClass);
    }
    public static Set<Map.Entry<String, Class<? extends LoadBalancer>>> getLoadBalancers(){
        return loadBalancers.entrySet();
    }

    public static Class<? extends LoadBalancer> getLoadBalancer(String name) {
        return loadBalancers.getOrDefault(name, FiftyFifty.class);
    }

    protected final QueueManager queueManager;

    public LoadBalancer(QueueManager qm) {
        this.queueManager = qm;
    }

    public abstract LoadBalancerResults processJob(PrintJob j);

    public static class LoadBalancerResults {
        public String destination;
        public long eta;
    }

}
