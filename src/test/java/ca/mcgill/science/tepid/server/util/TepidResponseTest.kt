package ca.mcgill.science.tepid.server.util

import org.junit.Test

/**
 * Created by Allan Wang on 2017-10-28.
 */
class TepidResponseTest {

    data class Data(val id: Int)

    @Test
    fun basic() {
        val response = tepidResponse("bad") { Data(1) }
//        println(response.readEntity(String::class.java))
    }
}