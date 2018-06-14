package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class UserTest : WithLogging() {

    val endpoints: Users by lazy {
        Users()
    }

    @Test
    fun configured() {
        assertTrue(endpoints.adminConfigured(), "Tepid is not configured")
    }

}

class TestUserGetQuota : WithLogging () {
    @Test
    fun testGetQuotaQueriedUserNull(){
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaQueriedUserNoRole(){
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaElder(){
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaCTFer(){
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserIgnoreSummerSemester(){
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSemesterPre2016 () {
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSemester2016F () {
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSemesterPost2016F () {
        fail("Test is not implemented")
    }

    @Test
    fun testGetQuotaUserSpanMultipleSemesters () {
        fail("Test is not implemented")
    }

}