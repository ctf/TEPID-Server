# Testing
Testing in Kotlin is as annoying as writing Kotlin is pleasant. This is partly by design: the variety of things you can do in Kotlin which would otherwise be impossible in Java lead to a number of bizarre implementations. For example, extension functions are impossible in Java, but common in Kotlin. However, extension functions actually compile to receiver functions (a function where the first parameter is a member of the extended class) in an ephemeral class only created during compilation. This means that trying to make a testing double for a class with an extension function can often require several different testing doubles of various sorts. 

Thankfully the [mockk library](mockk.io) makes a lot of this easier.

## Contents

1 [Testing Patterns](#Testing Patterns) : General ways to mock stuff

1 [Testing Cookbook](#Testing CookBook) : Ways to mock specific TEPID things

## Testing Patterns

## Testing Cookbook

### Mock Config Values

```kotlin
class test {

    @Test
    fun test(){
        every { Config.QUOTA_GROUP } returns listOf(AdGroup("user_group"))
    }
 
    companion object : Logging {
        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockkObject(Config)
        }

        @JvmStatic
        @AfterAll
        fun tearTest(){
            unmockkAll()
        }
    }
}
```

### Mock the DB

It would be nice if we had dependency injection but this is the world we live in.

```kotlin
class test {

    @Test
    fun test(){
        every { Config.QUOTA_GROUP } returns listOf(AdGroup("user_group"))
    }
 
    companion object : Logging {
        lateinit var mockDb: DbLayer

        @JvmStatic
        @BeforeAll
        fun initTest() {
            mockDb = spyk<DbLayer>(Config.getDb())
            DB = mockDb
        }

        @JvmStatic
        @AfterAll
        fun tearTest(){
            DB = Config.getDb()
        }
    }
}
```