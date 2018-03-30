package ca.mcgill.science.tepid.server.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class GsTest {

    private val gs = GsDelegate()

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