package shared;

/**
 * Created by Allan Wang on 27/01/2017.
 *
 * The following are default keys used for testing
 * Please copy this class to a new Config class and modify your return values if necessary.
 * Config is gitignored for your security
 */
public class ConfigSample {

    public static String getSetting(ConfigKeys key) {
        switch (key) {
	    case COUCHDB_URL:
		return "";
     	    case COUCHDB_USERNAME:
		return "";
	    case COUCHDB_PASSWORD:
                return "";
            case BARCODES_USERNAME:
		return "";
            case BARCODES_PASSWORD:
		return "";
            case BARCODES_URL:
		return "";
            case TEM_URL:
		return "";
            case RESOURCE_CREDENTIALS:
                return "";
            case LDAP_ENABLED:
                return "";
            default:
                System.err.println(String.format("Config key %s not set", key));
                return null;
        }
    }
}
