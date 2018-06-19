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

