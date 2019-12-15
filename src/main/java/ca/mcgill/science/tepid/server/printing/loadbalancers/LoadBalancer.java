package ca.mcgill.science.tepid.server.printing.loadbalancers;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.printing.QueueManager;

import java.util.HashMap;
import java.util.Map;

public abstract class LoadBalancer {

    private static final Map<String, Class<? extends LoadBalancer>> loadBalancers = new HashMap<>();
    public static void registerLoadBalancer(String name, Class<? extends LoadBalancer> loadBalancerClass){
        loadBalancers.put(name, loadBalancerClass);
    }

    @SuppressWarnings("unchecked")
    public static final Class<? extends LoadBalancer>[] _loadBalancers = new Class[]{FiftyFifty.class};

    public static Class<? extends LoadBalancer> getLoadBalancer(String name) {
        try {
            return (Class<? extends LoadBalancer>) Class.forName("ca.science.tepid.server.loadbalancer." + name);
        } catch (ClassNotFoundException e) {
            return LoadBalancer._loadBalancers[0];
        }
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
