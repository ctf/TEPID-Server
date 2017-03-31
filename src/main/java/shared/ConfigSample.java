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
            case DB_PASSWORD:
                return "admin";
            case RESOURCE_CREDENTIALS:
                return "test";
            default:
                System.err.println(String.format("Config key %s not set", key));
                return null;
        }
    }
}
