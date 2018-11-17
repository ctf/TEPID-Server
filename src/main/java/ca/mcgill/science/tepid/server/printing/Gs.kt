package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.utils.WithLogging
import java.io.File
import java.io.IOException

/**
 * Singleton holder to be used for production
 */
object Gs : GsContract by GsDelegate()

interface GsContract {
    /**
     * Given a postscript file, output the ink coverage for each page
     * Returns null if the process fails to launch
     * If the process does launch, then any output not matching our expected format
     * will be ignored
     */
    fun inkCoverage(f: File): List<InkCoverage>?

    /**
     * Given a postscript file, output for the entire file:
     * - total page count
     * - colour page count (ignoring a '/ProcessColorModel /DeviceGray' declaration)
     * Returns null if the process fails to launch
     */
    fun psInfo(f: File): PsData?

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

    /*
    * The ink_cov device differs from the inkcov device
    * ink_cov tries to get a more accurate representation of the actual colours which will be used by the page.
    * it tries to deal with conversions from RGB space to CMYK space.
    * For example, it will try to crush all monochrome to K, rather than some CMY combination or a "rich black" MYK
    * It is also able to deal with pages with a small patch of colour.
    * For example, a page might have a small color logo which is too small to count for more than 1% of 1% of the page (a square roughly 7mm on a side). With inkcov, there are not enough decimals printed for this to show up. But ink_cov will make the difference greater, and so more color pages will be detected as color
    * This is undocumented in GhostScript, but they have basically the same inputs
    */
    fun gs(f: File): List<String>? {
        val gsProcess = run("-sOutputFile=%stdout%",
                "-dBATCH", "-dNOPAUSE", "-dQUIET", "-q",
                "-sDEVICE=ink_cov", f.absolutePath) ?: return null
        return gsProcess.inputStream.bufferedReader().useLines { it.toList() }
    }

    /**
     * Matcher for the snippet relevant to ink coverage
     * Note that this may not necessarily match a full line, as not all outputs are separated by new lines
     */
    private val cmykRegex: Regex by lazy { Regex("(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+CMYK OK") }

    /**
     * Expected input format:
     * 0.06841  0.41734  0.17687  0.04558 CMYK OK
     * See [cmykRegex] for matching regex
     *
     * The lines are joined and then all regex matches are extracted because of a bug in GhostScript
     * see for more details: https://bugs.ghostscript.com/show_bug.cgi?id=699342
     */
    fun inkCoverage(lines: List<String>): List<InkCoverage> = cmykRegex.findAll(lines.joinToString(" "))
            .map {
                val (_, c, y, m, k) = it.groupValues
                InkCoverage(c.toFloat(), y.toFloat(), m.toFloat(), k.toFloat())
            }.toList()

    override fun inkCoverage(f: File): List<InkCoverage>? {
        return inkCoverage(gs(f) ?: return null)
    }

    override fun psInfo(f: File): PsData? {
        val coverage = inkCoverage(f) ?: return null
        return coverageToInfo(coverage)
    }

    fun coverageToInfo(coverage: List<InkCoverage>): PsData {
        val pages = coverage.size
        val colour = coverage.filter { !it.monochrome }.size
        return PsData(pages, colour)
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

/**
 * Holds info for ps files
 */
data class PsData(val pages: Int, val colourPages: Int) {
    val isColour: Boolean
        get() = this.colourPages != 0
}