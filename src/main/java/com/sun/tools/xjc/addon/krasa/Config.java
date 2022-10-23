package com.sun.tools.xjc.addon.krasa;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Config {

    private Properties properties;

    public Config(Properties properties) {
        this.properties = properties;
    }

    public long getLong(String key) {
        return Long.parseLong(String.valueOf(this.properties.get(key)).trim());
    }

    public int getInt(String key) {
        return Integer.parseInt(String.valueOf(this.properties.get(key)).trim());
    }

    public String getString(String key) {
        return String.valueOf(this.properties.getProperty(key)).trim();
    }

    public String getString(String key, String defaultValue) {
        return String.valueOf(this.properties.getProperty(key, defaultValue));
    }

    public boolean getBoolean(String key) {
        return Boolean.getBoolean(String.valueOf(this.properties.getProperty(key)));
    }

    public List<String> getStringArrayList(String key) {
        String tmp = String.valueOf(this.properties.getProperty(key));
        String[] arr = tmp.split(",");
        return Arrays.asList(arr);
    }
}
