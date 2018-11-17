package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.server.printing.GsDelegate
import ca.mcgill.science.tepid.server.printing.PsData
import org.junit.Ignore
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

    @Ignore("BROKEN TEST SMH: test resource not present")
    @Test
    fun fileTests() {
        val gsDir = File(this::class.java.classLoader.getResource("gs").file)
        if (!gsDir.isDirectory) {
            println("Skipping gs test; no files found")
            return
        }

        gsDir.listFiles { _, name -> name.endsWith(".ps") }.forEach {
            val lines = gs.gs(it)
            println("\nTested ${it.name}: lines\n${lines.joinToString("\n")}")
            val coverage = gs.inkCoverage(lines)
            val psInfo = gs.coverageToInfo(coverage)
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
        val inkCov = gs.inkCoverage(listOf("$c $m $y $k CMYK OK")).firstOrNull()
        assertNotNull(inkCov)
        assertEquals(c, inkCov!!.c)
        assertEquals(m, inkCov.m)
        assertEquals(y, inkCov.y)
        assertEquals(k, inkCov.k)
        assertFalse(inkCov.monochrome)
    }

}