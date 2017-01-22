package com.stirante.updater.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * Created by stirante
 */
public class ConfigLoader {

    public static HashMap<String, String> load(InputStream s) {
        HashMap<String, String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s));
        try {
            while (reader.ready()) {
                String line = reader.readLine();
                String[] split = line.split("=");
                if (split.length == 2) {
                    result.put(split[0].replaceAll("%20", " "), split[1]);
                } else {
                    System.out.println("Invalid config!");
                    System.out.println(">" + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static HashMap<String, String> loadConfig() {
        return load(ConfigLoader.class.getResourceAsStream("/config.txt"));
    }

}
