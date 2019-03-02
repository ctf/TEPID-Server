package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.utils.Loggable

interface IHibernateCrud : Loggable {

    fun <T> create(obj:T)

    fun <T, P> read(classParameter: Class<T> ,id:P) : T

    fun <T> update(obj:T)

    fun <T> delete(obj:T)

    fun <T, P> deleteById(classParameter:Class<T>, id:P)

}
