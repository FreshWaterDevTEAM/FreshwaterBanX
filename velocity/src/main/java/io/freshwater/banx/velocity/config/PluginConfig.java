package io.freshwater.banx.velocity.config;

import io.freshwater.banx.api.PunishmentType;
import io.freshwater.banx.velocity.storage.DatabaseSettings;
import io.freshwater.banx.velocity.util.Durations;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed view over {@code config.yml}. Reloadable: build a fresh instance via {@link #load}.
 */
public final class PluginConfig {

    public static final String DEFAULT_RULE_KEY = "DEFAULT";

    private final DatabaseSettings database;
    private final String banScreen;
    private final String tempbanScreen;
    private final String kickScreen;
    private final Map<String, String> messages;
    private final boolean httpEnabled;
    private final String httpBind;
    private final int httpPort;
    private final String httpToken;
    private final boolean broadcastEnabled;
    private final boolean bridgeSyncEnabled;
    private final boolean bridgeCancelMatrix;
    private final int bridgeMinViolations;
    private final boolean bridgeDebug;
    private final Map<String, List<RuleTier>> rules;

    private PluginConfig(DatabaseSettings database, String banScreen, String tempbanScreen, String kickScreen,
                         Map<String, String> messages, boolean httpEnabled, String httpBind, int httpPort,
                         String httpToken, boolean broadcastEnabled, boolean bridgeSyncEnabled,
                         boolean bridgeCancelMatrix, int bridgeMinViolations, boolean bridgeDebug,
                         Map<String, List<RuleTier>> rules) {
        this.database = database;
        this.banScreen = banScreen;
        this.tempbanScreen = tempbanScreen;
        this.kickScreen = kickScreen;
        this.messages = messages;
        this.httpEnabled = httpEnabled;
        this.httpBind = httpBind;
        this.httpPort = httpPort;
        this.httpToken = httpToken;
        this.broadcastEnabled = broadcastEnabled;
        this.bridgeSyncEnabled = bridgeSyncEnabled;
        this.bridgeCancelMatrix = bridgeCancelMatrix;
        this.bridgeMinViolations = bridgeMinViolations;
        this.bridgeDebug = bridgeDebug;
        this.rules = rules;
    }

    public DatabaseSettings database() {
        return database;
    }

    public String banScreen() {
        return banScreen;
    }

    public String tempbanScreen() {
        return tempbanScreen;
    }

    public String kickScreen() {
        return kickScreen;
    }

    public boolean httpEnabled() {
        return httpEnabled;
    }

    public String httpBind() {
        return httpBind;
    }

    public int httpPort() {
        return httpPort;
    }

    public String httpToken() {
        return httpToken;
    }

    public boolean broadcastEnabled() {
        return broadcastEnabled;
    }

    /** Whether the proxy is the source of truth for bridge config and should push it to backends. */
    public boolean bridgeSyncEnabled() {
        return bridgeSyncEnabled;
    }

    public boolean bridgeCancelMatrix() {
        return bridgeCancelMatrix;
    }

    public int bridgeMinViolations() {
        return bridgeMinViolations;
    }

    public boolean bridgeDebug() {
        return bridgeDebug;
    }

    /** Returns the configured message template for a key, or the key wrapped in brackets if missing. */
    public String message(String key) {
        return messages.getOrDefault(key, "<red>[missing message: " + key + "]");
    }

    /** Ordered (ascending VL) rule tiers for a hack type, falling back to the DEFAULT ruleset. */
    public List<RuleTier> rulesFor(String hackType) {
        String key = hackType == null ? DEFAULT_RULE_KEY : hackType.toUpperCase(Locale.ROOT);
        List<RuleTier> specific = rules.get(key);
        if (specific != null && !specific.isEmpty()) {
            return specific;
        }
        return rules.getOrDefault(DEFAULT_RULE_KEY, List.of());
    }

    /** Loads (and if necessary first writes the bundled default) the config from the data directory. */
    public static PluginConfig load(Path dataDirectory, Logger logger) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("config.yml");
        if (Files.notExists(file)) {
            try (InputStream in = PluginConfig.class.getResourceAsStream("/config.yml")) {
                if (in == null) {
                    throw new IOException("Bundled config.yml is missing from the jar");
                }
                Files.copy(in, file);
                logger.info("Wrote default config.yml to {}", file);
            }
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object parsed = yaml.load(reader);
            root = parsed instanceof Map ? castMap(parsed) : new LinkedHashMap<>();
        }

        Map<String, Object> db = section(root, "database");
        DatabaseSettings database = new DatabaseSettings(
                str(db, "host", "localhost"),
                integer(db, "port", 3306),
                str(db, "database", "freshwaterbanx"),
                str(db, "username", "root"),
                str(db, "password", ""),
                integer(db, "pool-size", 10),
                str(db, "table-prefix", "fbx_"),
                bool(db, "use-ssl", false)
        );

        Map<String, Object> msg = section(root, "messages");
        Map<String, Object> screens = section(msg, "screens");
        String banScreen = str(screens, "ban", "<red>You are permanently banned.");
        String tempScreen = str(screens, "tempban", "<red>You are temporarily banned.");
        String kickScreen = str(screens, "kick", "<red>You were kicked.");

        Map<String, String> messages = new LinkedHashMap<>();
        Map<String, Object> feedback = section(msg, "feedback");
        for (Map.Entry<String, Object> e : feedback.entrySet()) {
            messages.put(e.getKey(), String.valueOf(e.getValue()));
        }

        Map<String, Object> http = section(root, "http-api");
        boolean httpEnabled = bool(http, "enabled", false);
        String httpBind = str(http, "bind", "0.0.0.0");
        int httpPort = integer(http, "port", 8085);
        String httpToken = str(http, "token", "");

        boolean broadcast = bool(section(root, "notifications"), "broadcast", true);

        Map<String, Object> bridgeSync = section(root, "bridge-sync");
        boolean bridgeSyncEnabled = bool(bridgeSync, "enabled", true);
        boolean bridgeCancelMatrix = bool(bridgeSync, "cancel-matrix-commands", true);
        int bridgeMinViolations = integer(bridgeSync, "min-violations-to-forward", 1);
        boolean bridgeDebug = bool(bridgeSync, "debug", false);

        Map<String, List<RuleTier>> rules = parseRules(section(root, "rules"), logger);

        return new PluginConfig(database, banScreen, tempScreen, kickScreen, messages,
                httpEnabled, httpBind, httpPort, httpToken, broadcast, bridgeSyncEnabled,
                bridgeCancelMatrix, bridgeMinViolations, bridgeDebug, rules);
    }

    private static Map<String, List<RuleTier>> parseRules(Map<String, Object> rulesSection, Logger logger) {
        Map<String, List<RuleTier>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rulesSection.entrySet()) {
            String key = entry.getKey().toUpperCase(Locale.ROOT);
            if (!(entry.getValue() instanceof List<?> rawList)) {
                continue;
            }
            List<RuleTier> tiers = new ArrayList<>();
            for (Object rawTier : rawList) {
                if (!(rawTier instanceof Map<?, ?> tierMap)) {
                    continue;
                }
                Map<String, Object> tier = castMap(tierMap);
                int vl = integer(tier, "vl", 0);
                String actionRaw = str(tier, "action", "KICK").toUpperCase(Locale.ROOT);
                PunishmentType action;
                try {
                    action = PunishmentType.valueOf(actionRaw);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Unknown action '{}' in rule '{}', skipping tier", actionRaw, key);
                    continue;
                }
                Long durationMillis = null;
                if (action == PunishmentType.TEMPBAN) {
                    String durationStr = str(tier, "duration", "");
                    long parsed = Durations.parse(durationStr);
                    if (parsed <= 0L || parsed == Long.MAX_VALUE) {
                        logger.warn("Invalid TEMPBAN duration '{}' in rule '{}', defaulting to 1h", durationStr, key);
                        parsed = 3_600_000L;
                    }
                    durationMillis = parsed;
                }
                String reason = str(tier, "reason", "Cheating");
                tiers.add(new RuleTier(vl, action, durationMillis, reason));
            }
            tiers.sort(Comparator.comparingInt(RuleTier::minVl));
            result.put(key, tiers);
        }
        return result;
    }

    // ---- snakeyaml helpers ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map) {
            return castMap(value);
        }
        return new LinkedHashMap<>();
    }

    private static String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static int integer(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString().trim());
        }
        return def;
    }
}
