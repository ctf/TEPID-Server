package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.MarqueeData


class HibernateMarqueeLayer(val hc : HibernateCrud) : DbMarqueeLayer {

    override fun getMarquees(): List<MarqueeData> {

        return hc.em.createQuery("SELECT c FROM MarqueeData  c", MarqueeData::class.java).resultList
    }
}
