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
        checkOutMidnightChecker(scheduler);
        scheduler.scheduleAtFixedRate(new MoveLateCheckOuts(), 0, 30, TimeUnit.MINUTES);
    }

    /**
     * Schedule event at the next midnight
     *
     * @param scheduler service to schedule
     */
    private void checkOutMidnightChecker(ScheduledExecutorService scheduler) {
        Calendar startTime = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        startTime.set(Calendar.HOUR_OF_DAY, 0);
        startTime.set(Calendar.MINUTE, 0);
        startTime.set(Calendar.SECOND, 0);
        startTime.set(Calendar.MILLISECOND, 0);

        if (startTime.before(now) || startTime.equals(now)) {
            startTime.add(Calendar.DATE, 1);
        }

        startTime.getTime();

        long initialDelay = (startTime.getTimeInMillis() - Calendar.getInstance().getTimeInMillis()) / 1000;

        scheduler.scheduleAtFixedRate(new UncheckedOHMonitor(), initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    public void contextDestroyed(ServletContextEvent event) {
        scheduler.shutdownNow();
    }

}
