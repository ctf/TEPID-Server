package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.rest.JobDataMonitor
import ca.mcgill.science.tepid.server.rest.JobMonitor
import ca.mcgill.science.tepid.utils.WithLogging
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

class BackgroundTasks : ServletContextListener {

    private lateinit var scheduler: ScheduledExecutorService

    init {
        log.info("Starting BackgroundTasks")
    }

    override fun contextInitialized(event: ServletContextEvent?) {
        scheduler = Executors.newScheduledThreadPool(2)
        scheduler.scheduleAtFixedRate(JobMonitor(), 0, 30, TimeUnit.MINUTES)
        scheduler.scheduleAtFixedRate(JobDataMonitor(), 0, 12, TimeUnit.HOURS)
        scheduler.scheduleAtFixedRate(SessionMonitor(), 0, 4, TimeUnit.HOURS)
        log.info("BackgroundTasks initialized")
    }

    override fun contextDestroyed(event: ServletContextEvent?) {
        scheduler.shutdownNow()
        log.info("BackgroundTasks destroyed")
    }

    private companion object : WithLogging()

}
