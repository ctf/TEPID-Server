package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.PrintQueue
import org.junit.Test

/**
 * Created by Allan Wang on 2017-11-18.
 */
class CouchTest {

    @Test
    fun test() {
        val rows = CouchDb.getViewRows<PrintQueue>("queues")
        println(rows)
    }

}