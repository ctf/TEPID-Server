package ca.mcgill.science.tepid.server.printing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull



class GsTest {

    private val gs = GsDelegate()

    /**
     * [GsInfo] is provided if a file's name is of the format:
     *
     * [...]_[color]_[pages].ps
     *
     * Pages is mandatory
     */
    private val File.gsInfo: PsData?
        get() {
            val parts = nameWithoutExtension.split("_")
            if (parts.size < 3) return null
            val pages = parts[parts.size - 1].toIntOrNull() ?: return null
            val color = parts[parts.size - 2].toIntOrNull() ?: 0
            return PsData(pages, color)
        }

    @Disabled("BROKEN TEST SMH: test resource not present")
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
    fun lineToInkCovColor() {
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

class PsInfoTest {

    private val gs = GsDelegate()

    @ParameterizedTest
    @CsvSource(
        "V3/ADOBE BW.ps, 1, 0",
        "V3/ADOBE COLORED IMAGE.prn, 1, 1",
        "V3/ADOBE COLORED.ps, 1, 1",
        "V3/ADOBE GRAYSCALE.ps, 1, 0",
        "V3/CHROME BW.prn, 2, 0",
        "V3/CHROME COLORED PRINTDIALOGED.prn, 1, 1",
        "V3/CHROME COLORED NOXEROX.prn, 1, 1",
        "V3/CHROME COLORED XEROX.prn, 1, 1",
        "V3/PINGU BW PHOTO.prn, 1, 0",
        "V3/PINGU COLORED PHOTO.prn, 1, 1",

        "V4/ADOBE BW.prn, 1, 0",
        "V4/ADOBE COLORED IMAGE.prn, 1, 1",
        "V4/ADOBE COLORED.prn, 1, 1",
        "V4/ADOBE GRAYSCALE.prn, 1, 0",
        //"V4/ADOBE GRAYSCALE BUT COLOR.prn, 1, 0",
        "V4/CHROME BW.prn, 1, 0",
        "V4/CHROME COLORED.prn, 1, 1",
        "V4/PDF FIREFOX BW.prn, 1, 0",
        "V4/PDF FIREFOX COLORED.prn, 1, 1",
        "V4/PINGU BW PHOTO.prn, 1, 0",
        "V4/PINGU COLORED PHOTO.prn, 1, 1"
    )
    fun psInfoTest(fileName:String, pages: Int, colorPages: Int){
        val tmp = File(this::class.java.classLoader.getResource("ps").file+"\\"+fileName)
        val r = gs.psInfo(tmp)
        assertEquals(pages, r.pages, "Total page count incorrect")
        assertEquals(colorPages, r.colorPages, "Color page count incorrect")
    }

}