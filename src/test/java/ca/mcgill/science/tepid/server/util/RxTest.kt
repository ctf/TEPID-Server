package ca.mcgill.science.tepid.server.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RxTest {

    @Test
    fun threadCheck() {
        val threadName = Thread.currentThread().name
        val result = Rx.zipMaybe({
            Thread.currentThread().name
        }, {
            Thread.currentThread().name
        }, { out1, out2 ->
            assertNotNull(out1)
            assertNotNull(out2)
            assertNotEquals(threadName, out1)
            assertNotEquals(threadName, out2)
            Thread.currentThread().name
        }).blockingGet()
        assertNotNull(result)
    }

    @Test
    fun nullAttrs() {
        val result = Rx.zipMaybe<String, Int, String>({ null }, { 20 }, { out1, out2 ->
            assertNull(out1)
            assertEquals(out2, 20)
            null
        }).blockingGet()
        assertNull(result)
    }

}