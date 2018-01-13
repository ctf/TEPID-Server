package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.Session
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.ws.rs.container.ContainerRequestContext

val mapper = jacksonObjectMapper()

/**
 * Safely attempts to extract a session from a container context
 * If a session could not be found, it will be logged inside the [logger]
 */
fun ContainerRequestContext.getSession(logger: Logger): Session? {
    val session = getProperty("session") as? Session
    if (session == null)
        logger.error("Failed to retrieve session")
    return null
}

/**
 * Collection of helper methods
 * May be extensive, which is why it is bound within the Utils singleton
 */
object Utils {

    /**
     * Returns string representing current time
     * Optionally format the date before return
     */
    fun now(pattern: String? = null): String {
        val t = Calendar.getInstance().time
        return if (pattern == null) t.toString()
        else SimpleDateFormat(pattern).format(t)
    }

    /**
     * Returns a nonnull file only if it exists
     */
    fun existingFile(path: String?): File? {
        if (path == null) return null
        val f = File(path)
        return if (f.exists()) f else null
    }

    /**
     * Create and run a thread with try catch
     */
    inline fun startCaughtThread(name: String, logErrors: Boolean = false, crossinline action: () -> Unit) = object : Thread(name) {
        override fun run() {
            try {
                action()
            } catch (e: Exception) {
                if (logErrors)
                    System.err.println("Caught exception from thread: ${e.message}")
            }
        }
    }.start()
}