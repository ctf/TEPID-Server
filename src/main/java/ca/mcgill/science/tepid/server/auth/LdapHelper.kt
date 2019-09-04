package ca.mcgill.science.tepid.server.auth

import javax.naming.directory.Attribute
import javax.naming.directory.Attributes

class LdapHelper {
    companion object{
        /**
         * Convert attribute to string list
         */
        fun AttributeToList(attribute: Attribute) = (0 until attribute.size()).map { attribute.get(it).toString() }

    }
}