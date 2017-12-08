//package ca.mcgill.science.tepid.server.util
//
//import org.junit.Before
//import org.junit.Test
//import java.io.ByteArrayOutputStream
//import java.io.PrintStream
//import java.nio.charset.StandardCharsets
//import kotlin.test.assertTrue
//
///**
// * Created by Allan Wang on 2017-10-28.
// *
// * Some validation to ensure that our logger contains the information we need
// */
//class LoggingTest {
//
//    companion object : WithLogging()
//
//    private val stream = ByteArrayOutputStream()
//    private val origOut = System.out
//
//    @Before
//    fun init() {
//        System.setOut(PrintStream(stream))
//        stream.reset()
//    }
//
//    fun withOutput(action: (String) -> Unit) {
//        val output = stream.toString(StandardCharsets.UTF_8.name())
//        System.setOut(origOut)
//        println(output)
//        action(output)
//    }
//
//    @Test
//    fun test() {
//        val text = "This is just a showcase of the logging companion"
//        log.debug(text)
//        withOutput {
//            assertTrue(it.contains(javaClass.simpleName), "Logger should contain class name of explicit caller")
//            assertTrue(it.contains(text))
//        }
//    }
//
//}