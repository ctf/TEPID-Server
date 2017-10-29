package shared

/**
 * Created by Allan Wang on 27/01/2017.
 *
 * The following are default keys used for testing
 * Please copy this class to a new Config class and modify your return values if necessary.
 * Config is gitignored for your security
 */
object ConfigSample {

    private const val COUCHDB_URL_MAIN = "***REMOVED***"
    private const val COUCHDB_URL_TEST = "***REMOVED***"

    const val COUCHDB_URL = COUCHDB_URL_TEST
    const val COUCHDB_USERNAME = ""
    const val COUCHDB_PASSWORD = ""
    const val BARCODES_USERNAME = ""
    const val BARCODES_PASSWORD = ""
    const val BARCODES_URL = "http://tepid.science.mcgill.ca:5984/barcodes"
    const val TEM_URL = ""
    const val RESOURCE_CREDENTIALS = ""
    const val LDAP_ENABLED = false //todo make dependent on couchdb url
}
