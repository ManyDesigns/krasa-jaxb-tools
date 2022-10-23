package com.sun.tools.xjc.addon.krasa;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {


    private static final String CONFIG_CONFIG = "config.properties";

    public static Config getConfig() {
        return new Config(getProperties(CONFIG_CONFIG));
    }

    private static Properties getProperties(String config) {

        InputStream inputStream = null;
        try {

            ClassLoader classLoader = ConfigReader.class.getClassLoader();
            File file = new File(classLoader.getResource(CONFIG_CONFIG).getFile());

            inputStream = new FileInputStream(file);

            Properties properties = new Properties();
            properties.load(inputStream);

            return properties;

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

}
