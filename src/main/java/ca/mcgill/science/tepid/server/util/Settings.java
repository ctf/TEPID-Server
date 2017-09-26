package ca.mcgill.science.tepid.server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Settings {

    private static final Properties settings = new Properties();

    static {
        String configFile = System.getProperty("catalina.base") + File.separator + "conf" + File.separator + "tepid.conf";
        try (FileInputStream in = new FileInputStream(configFile)) {
            settings.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getString(String key) {
        return settings.getProperty(key);
    }

    public static boolean getBoolean(String key) {
        return Boolean.valueOf(settings.getProperty(key));
    }

}
