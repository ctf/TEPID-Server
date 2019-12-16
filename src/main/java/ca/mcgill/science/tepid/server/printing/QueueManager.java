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

import java.util.HashMap;
import java.util.Map;

public class QueueManager {

    private final Logger log;
    public static final DbLayer db = DbLayerKt.getDB();

    private static final Map<String, QueueManager> instances = new HashMap<>();

    public final PrintQueue printQueue;
    public PrintQueue getPrintQueue() {
        return printQueue;
    }

    private final LoadBalancer loadBalancer;

    public static QueueManager getInstance(String queueId) {
        synchronized (instances) {
            return instances.computeIfAbsent(queueId, QueueManager::new);
        }
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
        this.loadBalancer = LoadBalancer.getLoadBalancerFactory(printQueue.getLoadBalancer()).apply(this);
    }

    public PrintJob assignDestination(String id) {
        return db.updateJob(id, (j) -> {
            LoadBalancerResults results = this.loadBalancer.processJob(j);
            if (results == null) {
                j.fail(PrintError.INVALID_DESTINATION);
                log.info("LoadBalancer did not assign a destination {\'PrintJob\':\'{}\', \'LoadBalancer\':\'{}\'}", j.getId(), this.printQueue.getId());
            } else {
                j.setDestination(results.getDestination());
                j.setEta(results.getEta());
                log.info(j.getId() + " setting destination (" + results.getDestination() + ")");
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
