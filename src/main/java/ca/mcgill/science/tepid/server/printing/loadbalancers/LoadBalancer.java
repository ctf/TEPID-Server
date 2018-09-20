package ca.mcgill.science.tepid.server.printing.loadbalancers;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.server.printing.QueueManager;

public abstract class LoadBalancer {

    @SuppressWarnings("unchecked")
    public static final Class<? extends LoadBalancer>[] loadBalancers = new Class[]{FiftyFifty.class};

    //todo check usage. Surely there's a better way such as enums
    @SuppressWarnings("unchecked")
    public static Class<? extends LoadBalancer> getLoadBalancer(String name) {
        try {
            return (Class<? extends LoadBalancer>) Class.forName("ca.science.tepid.server.loadbalancer." + name);
        } catch (ClassNotFoundException e) {
            return LoadBalancer.loadBalancers[0];
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
