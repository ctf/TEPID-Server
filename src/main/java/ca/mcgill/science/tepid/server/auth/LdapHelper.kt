package ca.mcgill.science.tepid.server.auth

import javax.naming.directory.Attribute
import javax.naming.directory.Attributes

/**
 * Some helper functions for ldap
 */
interface LdapHelperContract {
    fun Attribute.toList(): List<String>
}

class LdapHelperDelegate : LdapHelperContract {

    /**
     * Convert attribute to string list
     */
    override fun Attribute.toList() = (0 until size()).map { get(it).toString() }
}