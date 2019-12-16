package ca.mcgill.science.tepid.server.printing;

import ca.mcgill.science.tepid.models.data.PrintJob;
import ca.mcgill.science.tepid.models.data.PrintQueue;
import ca.mcgill.science.tepid.models.enums.PrintError;
import ca.mcgill.science.tepid.server.db.DbLayer;
import ca.mcgill.science.tepid.server.db.DbLayerKt;
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer;
import ca.mcgill.science.tepid.server.printing.loadbalancers.LoadBalancer.LoadBalancerResults;
import kotlin.Unit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class QueueManager {

    private final Logger log;

    private static final Map<String, QueueManager> instances = new HashMap<>();
    public final PrintQueue printQueue;
    public static final DbLayer db = DbLayerKt.getDB();
    ;
    private final LoadBalancer loadBalancer;

    public static QueueManager getInstance(String id) {
        synchronized (instances) {
            if (!instances.containsKey(id)) {
                instances.put(id, new QueueManager(id));
            }
        }
        return instances.get(id);
    }

    public static PrintJob assignDestination(PrintJob job) {
        return getInstance(job.getQueueId()).assignDestination(job.getId());
    }

    private QueueManager(String id) {
        try {
            this.printQueue = db.getQueue(id);
        } catch (Exception e) {
            throw new RuntimeException("Could not instantiate queue manager", e);
        }
        log = LogManager.getLogger("Queue - " + this.printQueue.getName());
        log.trace("Instantiate queue manager {\'queueName\':\'{}\'}", this.printQueue.getName());
        Class<? extends LoadBalancer> lb = LoadBalancer.getLoadBalancer(printQueue.getLoadBalancer());
        try {
            this.loadBalancer = lb.getConstructor(QueueManager.class).newInstance(this);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Could not instantiate load balancer " + this.printQueue.getLoadBalancer(), e);
        }
    }

    public PrintJob assignDestination(String id) {
        return db.updateJob(id, (j) -> {
            LoadBalancerResults results = this.loadBalancer.processJob(j);
            if (results == null) {
                j.fail(PrintError.INVALID_DESTINATION);
                log.info("LoadBalancer did not assign a destination {\'PrintJob\':\'{}\', \'LoadBalancer\':\'{}\'}", j.getId(), this.printQueue.getId());
            } else {
                j.setDestination(results.destination);
                j.setEta(results.eta);
                log.info(j.getId() + " setting destination (" + results.destination + ")");
            }
            return Unit.INSTANCE;
        });
    }

    //TODO check use of args
    public long getEta(String destination) {
        long maxEta = 0;
        try {
            maxEta = db.getEta(destination);
        } catch (Exception ignored) {
        }
        return maxEta;
    }

}
