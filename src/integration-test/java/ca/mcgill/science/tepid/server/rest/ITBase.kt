package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.test.TestUtilsContract
import ca.mcgill.science.tepid.test.TestUtilsDelegate


open class ITBase { // so open class ITBase : TestUtilsContract by TestUtilsDelegate() gives a thing where it prepends https:// to the URL, which already has that
    object server : TestUtilsContract by TestUtilsDelegate(){ // this works though
    }
}