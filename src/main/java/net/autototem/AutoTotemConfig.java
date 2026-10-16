package net.autototem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod settings, stored in config/autototem.json.
 * Deliberately free of Minecraft classes so it works on any game version.
 */
public class AutoTotemConfig {

    private static final int CURRENT_VERSION = 2;

    public static final int MAX_DELAY_TICKS = 40;
    public static final float MIN_HEARTS = 0.5f;
    public static final float MAX_HEARTS = 10.0f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("autototem.json");
    private static final Path SOUND_DIR = CONFIG_DIR.resolve("autototem");

    /** 0 means "file written before versions were tracked". */
    public int configVersion = 0;

    public boolean enabled = true;

    /** Ticks to wait before the totem is moved to the offhand. 0 = as soon as possible. */
    public int equipDelayTicks = 0;

    public HealthThresholdConfig healthThreshold = new HealthThresholdConfig();
    public StatsHudConfig statsHud = new StatsHudConfig();
    public NotificationHudConfig notificationHud = new NotificationHudConfig();

    public static class HealthThresholdConfig {
        /** Off by default: the totem is equipped right away. */
        public boolean enabled = false;
        /** Equip when health is at or below this many hearts. */
        public float hearts = 2.0f;
        /** Also equip as soon as the player takes any damage. */
        public boolean afterFirstHit = false;
    }

    public static class StatsHudConfig {
        public boolean enabled = false;
        public int x = -1;
        public int y = -1;
        public float scale = 1.0f;
        public float opacity = 0.8f;
    }

    public static class NotificationHudConfig {
        public boolean enabled = false;
        public int x = -1;
        public int y = -1;
        public float scale = 1.0f;
        public float opacity = 0.9f;
        public float volume = 0.6f;
        /** File name inside config/autototem/, empty = vanilla totem sound. */
        public String customSound = "";
    }

    public static Path soundDir() {
        return SOUND_DIR;
    }

    public static AutoTotemConfig defaults() {
        AutoTotemConfig config = new AutoTotemConfig();
        config.configVersion = CURRENT_VERSION;
        return config;
    }

    /** Restores every setting to its default value, keeping this object (and the nested ones) in place. */
    public void resetToDefaults() {
        AutoTotemConfig fresh = defaults();
        this.configVersion = fresh.configVersion;
        this.enabled = fresh.enabled;
        this.equipDelayTicks = fresh.equipDelayTicks;

        this.healthThreshold.enabled = fresh.healthThreshold.enabled;
        this.healthThreshold.hearts = fresh.healthThreshold.hearts;
        this.healthThreshold.afterFirstHit = fresh.healthThreshold.afterFirstHit;

        this.statsHud.enabled = fresh.statsHud.enabled;
        this.statsHud.x = fresh.statsHud.x;
        this.statsHud.y = fresh.statsHud.y;
        this.statsHud.scale = fresh.statsHud.scale;
        this.statsHud.opacity = fresh.statsHud.opacity;

        this.notificationHud.enabled = fresh.notificationHud.enabled;
        this.notificationHud.x = fresh.notificationHud.x;
        this.notificationHud.y = fresh.notificationHud.y;
        this.notificationHud.scale = fresh.notificationHud.scale;
        this.notificationHud.opacity = fresh.notificationHud.opacity;
        this.notificationHud.volume = fresh.notificationHud.volume;
        this.notificationHud.customSound = fresh.notificationHud.customSound;
    }

    public static AutoTotemConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            AutoTotemConfig defaultConfig = defaults();
            save(defaultConfig);
            return defaultConfig;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            AutoTotemConfig config = GSON.fromJson(reader, AutoTotemConfig.class);
            if (config == null) {
                return defaults();
            }
            if (config.healthThreshold == null) {
                config.healthThreshold = new HealthThresholdConfig();
            }
            if (config.statsHud == null) {
                config.statsHud = new StatsHudConfig();
            }
            if (config.notificationHud == null) {
                config.notificationHud = new NotificationHudConfig();
            }
            if (config.notificationHud.customSound == null) {
                config.notificationHud.customSound = "";
            }

            // Version 2: the stats HUD is no longer on by default.
            if (config.configVersion < 2) {
                config.statsHud.enabled = false;
            }
            config.configVersion = CURRENT_VERSION;

            config.sanitize();
            save(config);
            return config;
        } catch (IOException | RuntimeException e) {
            // Unreadable or hand-edited into nonsense: start over rather than crash the game.
            return defaults();
        }
    }

    public static void save(AutoTotemConfig config) {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            // A failed save should never take the game down.
        }
    }

    /** Pulls hand-edited values back into their valid ranges. */
    private void sanitize() {
        equipDelayTicks = Math.max(0, Math.min(MAX_DELAY_TICKS, equipDelayTicks));
        healthThreshold.hearts = clamp(healthThreshold.hearts, MIN_HEARTS, MAX_HEARTS);
        statsHud.scale = clamp(statsHud.scale, 0.5f, 2.0f);
        statsHud.opacity = clamp(statsHud.opacity, 0.0f, 1.0f);
        notificationHud.scale = clamp(notificationHud.scale, 0.5f, 2.0f);
        notificationHud.opacity = clamp(notificationHud.opacity, 0.0f, 1.0f);
        notificationHud.volume = clamp(notificationHud.volume, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
