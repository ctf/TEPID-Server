# Testing
Testing in Kotlin is as annoying as writing Kotlin is pleasant. This is partly by design: the variety of things you can do in Kotlin which would otherwise be impossible in Java lead to a number of bizarre implementations. For example, extension functions are impossible in Java, but common in Kotlin. However, extension functions actually compile to receiver functions (a function where the first parameter is a member of the extended class) in an ephemeral class only created during compilation. This means that trying to make a testing double for a class with an extension function can often require several different testing doubles of various sorts. 

Thankfully the [mockk library](mockk.io) makes a lot of this easier.

## Contents

1 [Testing Patterns](#Testing Patterns) : General ways to mock stuff

1 [Testing Cookbook](#Testing CookBook) : Ways to mock specific TEPID things

## Testing Patterns



## Testing Cookbook

### CouchDb Queries
You can make queries directly against a test database, but it is often easier to use a mock object. This is especially true when checking error-handling code (try getting CouchDb to throw bizarre errors or invalid data), and it is nice to be able to force certain error conditions in the response from the DB

A quirk of Kotlin requires a function similar to ours with a templated output to need the type specified as a "reified T", which can only be done with an inline function. It's basically impossible to mock inline function chains of the level required for what we have. So I've written functions which use the traditional type parameter. Essentially, I've just pulled up the standard type parameter from where it's too low to deal with (mocking the JSON itself) to high enough to mock a useful construct (mocking the returned object).
Any concerns about efficiency should be accompanied with profiler data, a loadtest, and a comparison between the loadtest and standard usage patterns. 

#### GET 

```kotlin
lateinit var wt: WebTarget
lateinit var testUser: FullUser
lateinit var testOtherUser: FullUser

@Before
fun initTest() {
// Standard mocking of Objects and extension function stuff
    objectMockk(Config).mock()
    every{Config.ACCOUNT_DOMAIN} returns "config.example.com"
    objectMockk(CouchDb).mock()
    staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").mock()

    wt = mockk<WebTarget>()
    //init test users
}
@After
fun tearTest() {
    objectMockk(CouchDb).unmock()
    staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").unmock()
    objectMockk(Config).unmock()
}

// helper function to make mocks
private fun makeMocks(userListReturned : List<FullUser>) {
    // Mock CouchDb to return mock WebTarget
    every {
        CouchDb.path(ofType(CouchDb.CouchDbView::class))
    } returns wt
    // Mock WebTarget gracefully accepts its parameters.
    // You could also use a spyk instead of a mockk if you wanted the wt to still function as a WebTarget. This would be useful if you wanted to verify that a path had been put together properly without using many complicated verify{} and ordered verify stuff
    every {
        wt.queryParam(ofType(String::class), ofType(String::class))
    } returns wt
    // Mock the wt to return what we want it to
    every {
        wt.getViewRows<FullUser>()
    } returns userListReturned
}

@Test
fun testQueryUserDbByEmail () {
    makeMocks(listOf<FullUser>(testUser, testOtherUser))

    val actual = SessionManager.queryUserDb(testUser.email)

    // Verifies that CouchDb is called to the right address
    verify { CouchDb.path(CouchDb.CouchDbView.ByLongUser)}
    // Verifies the queryParams
    // If I had more than a single set, I might make wt a spyk and then query its uri at the end with an assertEquals
    verify { wt.queryParam("key", match {it.toString() == "\"db.EM%40config.example.com\""})}
    // Assert we got what we expected
    assertEquals(testUser, actual, "User was not returned when searched by Email")
}

```

#### PUT
##### Without verifying contents of the request

If you don't need to verify what object was put or posted to CouchDb, you can use the following

```kotlin
@Before
fun initTest() {
    objectMockk(CouchDb).mock() //mocks the actual CouchDb object
    staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").mock() //mocks all the extension functions we use
}

@After
fun tearTest(){
    objectMockk(CouchDb).unmock()
    staticMockk("ca.mcgill.science.tepid.server.util.WebTargetsKt").unmock()
}

@Test
fun testThings(){
    // Mocks the response parameters
    val mockObjectNode = ObjectMapper().createObjectNode()
            .put("ok", true)
            .put("id", "utestSU")
            .put("_rev", "2222")
    // Mocks the readEntity of the response. We use a spy because it's an actual response and all the other functions should just work
    // I am aware of how jank this is, given that mockResponse is an outboundJaxrsResponse and readEntity doesn't work for that
    // But given that we need to mock its response, we might as well mock the whole function 
    val mockResponse = spyk(Response.ok().build())
    every {
       mockResponse.readEntity(ObjectNode::class.java)
    } returns mockObjectNode
    
    // Mocks the WebTarget to return the mockResponse

    // We must instead mock the body which is inlined
    every{
       wt.request(MediaType.APPLICATION_JSON).put(any())
    } returns mockResponse
    
    // Mocks the function invoked on CouchDb (in this case putJson)
    // In this example, I only make one query, so I mock all responses and verify it later.
    // If you need to make more queries, you can mock out specific paths so they return specific responses
    every{
        // Note that we cannot mock the actual function invoked, since that function is inline
        // We must instead mock the body which is inlined
        CouchDb.path(any()).request(MediaType.APPLICATION_JSON).put(any())
    } returns mockResponse
    
    // Actually runs the test
    SessionManager.updateDbWithUser(testUser)
    
    // Verifies that CouchDb was invoked with the right path
    verify { CouchDb.path("u" + testSU) }
    // Verifies that objectNode was read correctly and that the _rev was updated
    assert(testUser._rev == "2222")
}
```

##### With verifying contents of the request

If you need to verify what object was POSTed or PUT to CouchDb, you need to break the webtarget out as a separate thing, since the chained-call-mock-resolver seems to have difficulty verifying the call otherwise

```kotlin
@Test
fun testUpdateUser () {

    val mockObjectNode = ObjectMapper().createObjectNode()
            .put("ok", true)
            .put("id", "utestSU")
            .put("_rev", "2222")

    val mockResponse = spyk(Response.ok(testResponseUser).build())
    every {
        mockResponse.readEntity(ObjectNode::class.java)
    } returns mockObjectNode

    // Mocks the WebTarget
    val wt = mockk<WebTarget>()
    // Mocks the WebTarget to return the mockResponse
    every {
        wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))
    } returns mockResponse
    // Mocks CouchDb to return the WebTarget
    every {
        CouchDb.path(any())
    } returns wt
    // Run Test
    SessionManager.updateDbWithUser(testUser)
    // Verifies the path
    verify { CouchDb.path("u" + testSU) }
    // Verifies that the right object was put
    verify { wt.request(MediaType.APPLICATION_JSON).put(Entity.entity(testUser, MediaType.APPLICATION_JSON))}
}
```