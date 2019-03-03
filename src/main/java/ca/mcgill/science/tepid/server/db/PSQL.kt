package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.server.util.mapper
import javax.ws.rs.core.Response


class HibernateDestinationLayer(val hc : HibernateCrud) : DbDestinationLayer{
    override fun getDestinations(): List<FullDestination> {
        return hc.readAll(FullDestination::class.java)
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
}

class HibernateMarqueeLayer(val hc : HibernateCrud) : DbMarqueeLayer {

    override fun getMarquees(): List<MarqueeData> {

        return hc.readAll(MarqueeData::class.java)
    }
}
