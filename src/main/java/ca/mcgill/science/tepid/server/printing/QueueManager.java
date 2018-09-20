package ca.mcgill.science.tepid.server.printing;

import ca.mcgill.science.tepid.models.bindings.PrintError;
import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.models.data.PrintQueue;
import ca.mcgill.science.tepid.server.db.CouchDb;
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer;
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer.LoadBalancerResults;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class QueueManager {

    private final Logger log;

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
        log = LogManager.getLogger("Queue - " + queueName);
        log.trace("Instantiate queue manager {\'queueName\':\'{}\'}", queueName);
        if (queueName == null)
            throw new RuntimeException("Could not instantiate null queue manager");
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
        if (results == null) {
            j.fail(PrintError.INVALID_DESTINATION);
            log.info("LoadBalancer did not assign a destination {\'PrintJob\':\'{}\', \'LoadBalancer\':\'{}\'}", j.getId(), this.queueConfig.getName());
        } else {
            j.setDestination(results.destination);
            j.setEta(results.eta);
            log.info(j.getId() + " setting destination (" + results.destination + ")");
        }
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
