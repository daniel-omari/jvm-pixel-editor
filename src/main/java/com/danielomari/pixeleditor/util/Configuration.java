package com.danielomari.pixeleditor.util;

import java.io.*;
import java.util.Properties;


// Loads and saves persistent settings (editor.properties), e.g. panel sizes and first-run flags.
public final class Configuration {
    private static Configuration instance;
    public Properties properties = new Properties();


    private Configuration() {
        try {
            File filePath = new File(getConfiguration(), "editor.properties"); // If the file is missing its created.
            if(filePath.exists() && filePath.isFile()) {
                InputStream stream = new FileInputStream(filePath);
                properties.load(stream);
                stream.close();}
        } catch (Exception e) {
            // Log the error
        }
    }

    // Per-user settings folder (~/.pixeleditor). Independent of the working
    // directory, so it works the same from Gradle, a bare JAR, or a packaged
    // install - and keeps runtime settings out of the source tree.
    private File getConfiguration() {
        File dir = new File(System.getProperty("user.home"), ".pixeleditor");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void updateConfiguration() {
        // Using getConfiguration() to get the configuration folder path and then adding the properties file name.
        File filePath = new File(getConfiguration(), "editor.properties");
        try {
            properties.store(new FileOutputStream(filePath), "Updated successfully!");
        } catch (IOException e) {
            throw new RuntimeException("Error occurred when updating the configuration:", e);
        }
    }


    public Boolean is(String key, Boolean defaultValue) {
        return getValue(key, defaultValue);
    } // Getter of GetValue - there may be a better way to handle this..

    private Boolean getValue(String key, Boolean defaultValue) {
        // Get the value of the boolean, it may be beneficial to change this to handle more than just booleans...
        return Boolean.parseBoolean(properties.getProperty(key, defaultValue.toString()));
    }

    public void getUpdatedConfiguration() { // Getter
        updateConfiguration();
    }

    // String settings (e.g. the selected UI theme).
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void putString(String key, String value) {
        properties.setProperty(key, value);
    }

    // Integer settings (e.g. saved panel divider positions).
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void putInt(String key, int value) {
        properties.setProperty(key, String.valueOf(value));
    }

    // Persist the current properties to disk (non-fatal if it can't be written).
    public void save() {
        try {
            updateConfiguration();
        } catch (RuntimeException e) {
            // ignore: settings just won't persist this time
        }
    }


    public static Configuration getInstance() {
        if (instance == null) {
            instance = new Configuration();
        }
        return instance;
    }

    public void updateUI() {
        // Update the UI
        javax.swing.SwingUtilities.invokeLater(() -> {
            // Changes each window to the updated UI
            for (java.awt.Window window : java.awt.Window.getWindows()) {
                javax.swing.SwingUtilities.updateComponentTreeUI(window);
                window.revalidate();
                window.repaint();
            }
        });
    }
}
