package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.server.util.mapper
import ca.mcgill.science.tepid.server.util.text
import javax.ws.rs.core.Response


class HibernateDestinationLayer(val hc : HibernateCrud<FullDestination, String?>) : DbDestinationLayer{
    override fun getDestinations(): List<FullDestination> {
        return hc.readAll()
    }

    override fun putDestinations(destinations: Map<Id, FullDestination>): String {
        val failures = mutableListOf<String>()
        destinations.map {
            try{
                it.value._id = it.key
                hc.updateOrCreateIfNotExist(it.value)
            }catch (e:Exception){
                failures.add(e.message ?: "Generic Failure :(")
            }
        }

        return mapper.writeValueAsString(failures)
    }
    override fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response {
        return Response.ok().build() //TODO("Implement ${FUNTION_NAME}")
}

    override fun deleteDestination(id: Id): String {
        return String() //TODO("Implement ${FUNTION_NAME}")
    }

}

class HibernateMarqueeLayer(val hc : HibernateCrud<MarqueeData, String?>) : DbMarqueeLayer {

    override fun getMarquees(): List<MarqueeData> {

        return hc.readAll()
    }
}
