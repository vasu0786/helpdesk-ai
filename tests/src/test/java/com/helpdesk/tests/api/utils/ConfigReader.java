package com.helpdesk.tests.api.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new RuntimeException("config.properties not found in src/test/resources");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config.properties", e);
        }
    }

    /** A -D system property wins over the file, e.g. mvn test -Dbase.url=http://other-host:8080 */
    public static String get(String key) {
        String value = System.getProperty(key, props.getProperty(key));
        if (value == null) {
            throw new RuntimeException("Missing key in config.properties: " + key);
        }
        return value;
    }
}
