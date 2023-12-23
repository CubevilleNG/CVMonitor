package org.cubeville.cvmonitor;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CVMonitor extends JavaPlugin {

    private Logger logger;
    private String serverName;

    private Map<String, Boolean> requiredPlugins;

    private Spark spark;
    private double tpsAlertThreshold;

    @Override
    public void onEnable() {
        this.logger = this.getLogger();
        this.requiredPlugins = initConfig();
        this.serverName = Bukkit.getServer().getWorldContainer().getName();
        if(this.serverName.equalsIgnoreCase("worlddata")) {
            this.serverName = getServer().getWorldContainer().getAbsoluteFile().getParentFile().getName();
        }
        checkRequiredPlugins();
        this.spark = SparkProvider.get();
        startTpsMonitor();
    }

    private void startTpsMonitor() {
        this.getServer().getScheduler().runTaskTimer(this, () -> {
            double tps = this.spark.tps().poll(StatisticWindow.TicksPerSecond.SECONDS_10);
            if(tps <= this.tpsAlertThreshold) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dsend send tps_alert " +
                        this.serverName + " " + tps);
            }
        }, 20, 6000); //6000 is 5min
    }

    private void checkRequiredPlugins() {
        boolean shutdownNeeded = false;
        StringBuilder failedPlugins = new StringBuilder();
        for(String plugin : this.requiredPlugins.keySet()) {
            if(this.requiredPlugins.get(plugin)) {
                if(Bukkit.getPluginManager().getPlugin(plugin) == null) {
                    shutdownNeeded = true;
                    failedPlugins.append(plugin).append(",");
                    this.logger.severe("Required plugin: " + plugin + " is not loaded on the server!");
                } else {
                    this.logger.info("Required plugin: " + plugin + " has successfully loaded on the server.");
                }
            }
        }
        if(shutdownNeeded) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dsend send server_start_failed " +
                    this.serverName + " " + failedPlugins.substring(0, failedPlugins.toString().lastIndexOf(",")));
            this.logger.severe("One or more required plugins have failed to load! Shutting down server now!");
            Bukkit.shutdown();
        }
    }

    private Map<String, Boolean> initConfig() {
        final File dataDir = getDataFolder();
        if(!dataDir.exists()) {
            dataDir.mkdirs();
        }
        File configFile = new File(dataDir, "config.yml");
        if(!configFile.exists()) {
            try {
                configFile.createNewFile();
                final InputStream inputStream = this.getResource(configFile.getName());
                final FileOutputStream fileOutputStream = new FileOutputStream(configFile);
                final byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = Objects.requireNonNull(inputStream).read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch(IOException e) {
                this.logger.log(Level.SEVERE, ChatColor.RED + "Unable to generate config file");
                throw new RuntimeException(ChatColor.LIGHT_PURPLE + "Unable to generate config file", e);
            }
        }
        Map<String, Boolean> requiredPlugins = new HashMap<>();
        YamlConfiguration mainConfig = new YamlConfiguration();
        try {
            mainConfig.load(configFile);
            this.tpsAlertThreshold = mainConfig.getDouble("tps-alert-threshold", 15);
            ConfigurationSection requiredPluginsConfig = mainConfig.getConfigurationSection("required-plugins");
            if(requiredPluginsConfig == null) {
                this.logger.severe("Unable to find path required-plugins in config file!");
            } else {
                for(String plugin : requiredPluginsConfig.getKeys(false)) {
                    requiredPlugins.put(plugin, requiredPluginsConfig.getBoolean(plugin, false));
                }
            }
        } catch(IOException | InvalidConfigurationException e) {
            this.logger.severe("Unable to load config file!");
            e.printStackTrace();
        }
        return requiredPlugins;
    }
}
