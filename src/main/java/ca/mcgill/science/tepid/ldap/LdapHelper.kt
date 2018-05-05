package ca.mcgill.science.tepid.ldap

import javax.naming.directory.Attribute
import javax.naming.directory.Attributes

/**
 * Some helper functions for ldap
 */
interface LdapHelperContract {
    fun Attributes.toList(): List<Attribute>
    fun Attribute.toList(): List<String>
}

class LdapHelperDelegate : LdapHelperContract {
    /**
     * Convert attributes to attribute list
     */
    override fun Attributes.toList(): List<Attribute> {
        val ids = iDs
        val data = mutableListOf<Attribute>()
        while (ids.hasMore()) {
            val id = ids.next()
            data.add(get(id))
        }
        ids.close()
        return (data)
    }

    /**
     * Convert attribute to string list
     */
    override fun Attribute.toList() = (0 until size()).map { get(it).toString() }
}