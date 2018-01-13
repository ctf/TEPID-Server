package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.utils.WithLogging
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

class BackgroundTasks : ServletContextListener {

    init {
        println("BackgroundTask started in Kotlin 2")
    }

    private lateinit var scheduler: ScheduledExecutorService

    override fun contextInitialized(event: ServletContextEvent?) {
        scheduler = Executors.newScheduledThreadPool(2)
        scheduler.scheduleAtFixedRate(JobMonitor(), 0, 30, TimeUnit.MINUTES)
        scheduler.scheduleAtFixedRate(JobDataMonitor(), 0, 12, TimeUnit.HOURS)
        scheduler.scheduleAtFixedRate(SessionMonitor(), 0, 4, TimeUnit.HOURS)
        println("BackgroundTask postinit")
    }

    override fun contextDestroyed(event: ServletContextEvent?) {
        scheduler.shutdownNow()
        println("BackgroundTask shutdown")
    }

    companion object : WithLogging()

}
