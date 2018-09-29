package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.server.util.mapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@Ignore("Just some demos of deserialisation from couchdb ")
class QueryWithoutDocsTest {

    data class queryResult(val id: String, val key: String, val value: String)

    @Before
    fun initTest() {
    }

    @After
    fun tearTest() {
    }


    /**
     * Test showing that it is possible to deserialise a row into a class which stores the ID, the key, and the value
     */
    @Test
    fun testDeserialiseSingleRow() {

        val queryId = "queryId"
        val queryKey = "queryKey"
        val queryValue = "queryValue"

        val result = mapper.readValue<queryResult>("{\"id\":\"$queryId\",\"key\":\"$queryKey\",\"value\":\"$queryValue\"}")
        assertEquals(queryResult::class, result::class)
        assertEquals(queryId, result.id)
        assertEquals(queryKey, result.key)
        assertEquals(queryValue, result.value)
    }

    /**
     * Test to show what to expect from [CouchDb.getViewRows]
     * Will only serialise the "value"
     */
    @Test
    fun testDeserialiseGetViewRows() {
        val queryKey = "queryKey"

        val input = mapper.readValue<ObjectNode>(
                "{\"total_rows\":14,\"offset\":9,\"rows\":[\n" +
                        "{\"id\":\"23ujckq28cp7evvp98kfkn561u\",\"key\":\"$queryKey\",\"value\":\"23ujckq28cp7evvp98kfkn561u\"},\n" +
                        "{\"id\":\"2j65dm1g14vb5bc9amv61jdhmv\",\"key\":\"$queryKey\",\"value\":\"2j65dm1g14vb5bc9amv61jdhmv\"},\n" +
                        "{\"id\":\"4cn9ebghqem2spa4ds3t68202k\",\"key\":\"$queryKey\",\"value\":\"4cn9ebghqem2spa4ds3t68202k\"},\n" +
                        "{\"id\":\"548av6pu4vban7oipvs0uc9avq\",\"key\":\"$queryKey\",\"value\":\"548av6pu4vban7oipvs0uc9avq\"}\n" +
                        "]}"
        )
//        // Will throw an error
//        val rows = input.get("rows") ?: fail ("null returned from parsing")
//        val result =  rows.mapNotNull { it?.get("value") }.map { mapper.treeToValue(it, queryResult::class.java)}

        val rows = input.get("rows") ?: fail("null returned from parsing")
        val result = rows.mapNotNull { it?.get("value") }.map { mapper.treeToValue(it, String::class.java) }

        assertEquals(String::class, result[0]::class)
        assertEquals(4, result.size)

    }
}