package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.server.db.DB
import org.apache.logging.log4j.kotlin.Logging
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

class BackgroundTasks : ServletContextListener {

    private lateinit var scheduler: ScheduledExecutorService

    init {
        logger.info("Starting BackgroundTasks")
        // initialising other things.
        logger.info(Config)
        logger.info(DB)
    }

    override fun contextInitialized(event: ServletContextEvent?) {
        scheduler = Executors.newScheduledThreadPool(2)
        scheduler.scheduleAtFixedRate(JobMonitor(), 1, 30, TimeUnit.MINUTES)
        scheduler.scheduleAtFixedRate(JobDataMonitor(), 1, 12, TimeUnit.HOURS)
        scheduler.scheduleAtFixedRate(SessionMonitor(), 1, 4, TimeUnit.HOURS)
        scheduler.scheduleAtFixedRate(UserMembershipMonitor(), 1, 7, TimeUnit.DAYS)
        logger.info("BackgroundTasks initialized")
    }

    override fun contextDestroyed(event: ServletContextEvent?) {
        scheduler.shutdownNow()
        logger.info("BackgroundTasks destroyed")
    }

    private companion object : Logging
}
