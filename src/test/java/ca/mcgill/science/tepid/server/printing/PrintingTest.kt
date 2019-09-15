package ca.mcgill.science.tepid.server.printing

import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.enums.PrintError
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.utils.WithLogging
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PrintingTest : WithLogging() {

    @Test
    fun tooManyPagesTestTooManyEnabled() {
        every { Config.MAX_PAGES_PER_JOB } returns 10

        val p = PrintJob(pages = 1000)
        val e = Assertions.assertThrows(Printer.PrintException::class.java) { Printer.validateJobSize(p) }

        Assertions.assertEquals(PrintError.TOO_MANY_PAGES.display, e.message)
    }

    @Test
    fun tooManyPagesTestTooManyDisabled() {
        every { Config.MAX_PAGES_PER_JOB } returns -1

        val p = PrintJob(pages = 1000)
        Printer.validateJobSize(p)
    }

    @Test
    fun tooManyPagesTestEnoughEnabled() {

        every { Config.MAX_PAGES_PER_JOB } returns 50

        val p = PrintJob(pages = 10)
        Printer.validateJobSize(p)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest() {
            unmockkAll()
        }
    }
}