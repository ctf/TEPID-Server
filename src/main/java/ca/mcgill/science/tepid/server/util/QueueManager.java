package ca.mcgill.science.tepid.server.util;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.models.data.PrintQueue;
import ca.mcgill.science.tepid.server.loadbalancers.LoadBalancer;
import ca.mcgill.science.tepid.server.loadbalancers.LoadBalancer.LoadBalancerResults;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class QueueManager {

    private static final Map<String, QueueManager> instances = new HashMap<>();
    public final PrintQueue queueConfig;
    private static final WebTarget couchdb = CouchDb.INSTANCE.getTarget();
    ;
    private final LoadBalancer loadBalancer;

    public static QueueManager getInstance(String queueName) {
        synchronized (instances) {
            if (!instances.containsKey(queueName)) {
                instances.put(queueName, new QueueManager(queueName));
            }
        }
        return instances.get(queueName);
    }

    public static PrintJob assignDestination(String id) {
        PrintJob j = couchdb.path(id).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
        return getInstance(j.getQueueName()).assignDestination(j);
    }

    private QueueManager(String queueName) {
        System.out.println("Instantiate queue manager for " + queueName);
        this.queueConfig = couchdb.path("q" + queueName).request(MediaType.APPLICATION_JSON).get(PrintQueue.class);
        Class<? extends LoadBalancer> lb = LoadBalancer.getLoadBalancer(queueConfig.getLoadBalancer());
        try {
            this.loadBalancer = lb.getConstructor(QueueManager.class).newInstance(this);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Could not instantiate load balancer " + this.queueConfig.getLoadBalancer(), e);
        }
    }

    public PrintJob assignDestination(PrintJob j) {
        LoadBalancerResults results = this.loadBalancer.processJob(j);
        j.setDestination(results.destination);
        j.setEta(results.eta);
        System.err.println(j.getId() + " setting destination (" + results.destination + ")");
        couchdb.path(j.getId()).request(MediaType.TEXT_PLAIN).put(Entity.entity(j, MediaType.APPLICATION_JSON));
        return couchdb.path(j.getId()).request(MediaType.APPLICATION_JSON).get(PrintJob.class);
    }

    //TODO check use of args
    public long getEta(String destination) {
        long maxEta = 0;
        try {
            maxEta = couchdb
                    .path("_design/main/_view")
                    .path("maxEta")
                    .request(MediaType.APPLICATION_JSON)
                    .get(ObjectNode.class).get("rows").get(0).get("value").asLong(0);
        } catch (Exception ignored) {
        }
        return maxEta;
    }

}
