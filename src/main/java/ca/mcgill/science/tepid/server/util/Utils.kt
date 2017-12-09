package ca.mcgill.science.tepid.server.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val mapper = jacksonObjectMapper()

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