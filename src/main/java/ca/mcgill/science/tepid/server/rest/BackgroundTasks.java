package ca.mcgill.science.tepid.server.rest;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackgroundTasks implements ServletContextListener {

    private ScheduledExecutorService scheduler;

    public void contextInitialized(ServletContextEvent event) {
        scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(new JobMonitor(), 0, 30, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(new JobDataMonitor(), 0, 12, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(new SessionMonitor(), 0, 4, TimeUnit.HOURS);
    }


    public void contextDestroyed(ServletContextEvent event) {
        scheduler.shutdownNow();
    }

}
