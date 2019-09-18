package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.server.ITBase
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrintIT : ITBase(), Loggable by WithLogging() {

    @Test
    fun testDeleteFileOnFailedUpload() {
        val testId = "testId0"
        // Produces an error in the pre-submit thread. It's not the best, but apparently mockking a file is deep in the JVM and bascially not possible.
        every { Printer.validateAndSend(any(), any(), any()) } throws IOException("TESTING PLZ IGNORE")
        Printer.print(testId, ByteArrayInputStream("".toByteArray()), true)
        assertFalse { File("/tmp/tepid/$testId.ps.xz").exists() }
    }

    @Test
    fun testFileStillExistsIfPrintingSuccessful() {
        val testId = "testId1"
        every { Printer.validateAndSend(any(), any(), any()) } returns { Unit }
        Printer.print(testId, ByteArrayInputStream("".toByteArray()), true)
        assertTrue { File("/tmp/tepid/$testId.ps.xz").exists() }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Printer)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}