package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.utils.WithLogging
import java.io.File
import java.io.IOException

/**
 * Singleton holder to be used for production
 */
object Gs : GsContract by GsDelegate()

interface GsContract {
    /**
     * Given a postscript file, output the an inkcoverage for each page
     * Returns null if the process fails to launch
     * If the process does launch, then any output not matching our expected format
     * will be ignored
     */
    fun inkCoverage(f: File): List<InkCoverage>?
}

/**
 * Underlying delegate that exposes methods for unit testing
 */
internal class GsDelegate : WithLogging(), GsContract {
    private val gsBin = if (System.getProperty("os.name").startsWith("Windows"))
        "C:/Program Files/gs/gs9.20/bin/gswin64c.exe" else "gs"

    private fun run(vararg args: String): Process? {
        val pb = ProcessBuilder(listOf(gsBin, *args))
        return try {
            pb.start()
        } catch (e: IOException) {
            log.error("Could not launch gs", e)
            null
        }
    }

    override fun inkCoverage(f: File): List<InkCoverage>? =
            run("-sOutputFile=%stdout%",
                    "-dBATCH", "-dNOPAUSE", "-dQUIET", "-q",
                    "-sDEVICE=inkcov", f.absolutePath)
                    ?.inputStream?.bufferedReader()?.useLines {
                it.mapNotNull(this::lineToInkCov).toList()
            }

    private val cmykRegex: Regex by lazy { Regex("(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+CMYK OK") }

    /**
     * Expected input format:
     * 0.06841  0.41734  0.17687  0.04558 CMYK OK
     * See [cmykRegex] for matching regex
     */
    fun lineToInkCov(line: String): InkCoverage? {
        val match = cmykRegex.matchEntire(line) ?: return null
        val (_, c, y, m, k) = match.groupValues
        return InkCoverage(c.toFloat(), y.toFloat(), m.toFloat(), k.toFloat())
    }

}

/**
 * Holds the distribution of
 * cyan, magenta, yellow, and black in a given page
 * If the first three values are equal, then the page is monochrome
 */
data class InkCoverage(val c: Float, val m: Float, val y: Float, val k: Float) {

    val monochrome = c == m && c == y

}