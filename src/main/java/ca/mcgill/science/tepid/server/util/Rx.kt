package ca.mcgill.science.tepid.server.util

import io.reactivex.Maybe
import io.reactivex.schedulers.Schedulers

object Rx {

    /**
     * Given two callables, execute them concurrently and zip the result
     * Results in a maybe, where the output can be retrieved from [Maybe.blockingGet]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, U : Any, R : Any> zipMaybe(callable1: () -> T?, callable2: () -> U?, zipper: (T?, U?) -> R?): Maybe<R> {
        val maybe1 = Maybe.fromCallable(callable1).subscribeOn(Schedulers.io())
        val maybe2 = Maybe.fromCallable(callable2).subscribeOn(Schedulers.io())
        return Maybe.zip(listOf(maybe1, maybe2), {
            data ->
            val out1 = data[0] as? T
            val out2 = data[1] as? U
            zipper(out1, out2)
        })
    }


}