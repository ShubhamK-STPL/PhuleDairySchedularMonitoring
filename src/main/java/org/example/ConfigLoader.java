package org.example;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("❌ config.properties not found in resources folder!");
            }
            properties.load(input);
            System.out.println("✅ Loaded configuration successfully from resources.");
        } catch (Exception e) {
            System.err.println("❌ Failed to load configuration: " + e.getMessage());
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
}
