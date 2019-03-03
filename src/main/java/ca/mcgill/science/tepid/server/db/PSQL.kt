package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.server.util.mapper
import javax.ws.rs.core.Response


class HibernateDestinationLayer(val hc : HibernateCrud) : DbDestinationLayer{
    override fun getDestinations(): List<FullDestination> {
        return hc.readAll(FullDestination::class.java)
    }
}

class HibernateMarqueeLayer(val hc : HibernateCrud) : DbMarqueeLayer {

    override fun getMarquees(): List<MarqueeData> {

        return hc.readAll(MarqueeData::class.java)
    }
}
