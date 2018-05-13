package ca.mcgill.science.tepid.server.util

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail

class GsTest {

    private val gs = GsDelegate()

    /**
     * [GsInfo] is provided if a file's name is of the format:
     *
     * [...]_[colour]_[pages].ps
     *
     * Pages is mandatory
     */
    private val File.gsInfo: PsData?
        get() {
            val parts = nameWithoutExtension.split("_")
            if (parts.size < 3) return null
            val pages = parts[parts.size - 1].toIntOrNull() ?: return null
            val colour = parts[parts.size - 2].toIntOrNull() ?: 0
            return PsData(pages, colour)
        }

    @Test
    fun fileTests() {
        val gsDir = File(this::class.java.classLoader.getResource("gs").file)
        if (!gsDir.isDirectory) {
            println("Skipping gs test; no files found")
            return
        }

        gsDir.listFiles { _, name -> name.endsWith(".ps") }.forEach {
            val coverage = Gs.inkCoverage(it) ?: fail("Failed to get gs info for ${it.absolutePath}")
            println("\nTested ${it.name}: coverage\n$coverage")
            val psInfo = Gs.coverageToInfo(coverage)
            val fileInfo = it.gsInfo ?: return@forEach println("Resulting info: $psInfo")
            assertEquals(fileInfo, psInfo, "GS info mismatch for ${it.absolutePath}")
            println("Matches supplied info: $fileInfo")
        }
    }

    @Test
    fun lineToInkCovColour() {
        val c = 0.06841f
        val m = 0.41734f
        val y = 0.17687f
        val k = 0.04558f
        val inkCov = gs.lineToInkCov("$c $m $y $k CMYK OK")
        assertNotNull(inkCov)
        assertEquals(c, inkCov!!.c)
        assertEquals(m, inkCov.m)
        assertEquals(y, inkCov.y)
        assertEquals(k, inkCov.k)
        assertFalse(inkCov.monochrome)
    }

}