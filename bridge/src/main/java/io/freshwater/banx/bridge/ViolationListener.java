package io.freshwater.banx.bridge;

import io.freshwater.banx.common.BridgeConfigMessage;
import io.freshwater.banx.common.Protocol;
import io.freshwater.banx.common.ViolationMessage;
import me.rerere.matrix.api.events.PlayerViolationCommandEvent;
import me.rerere.matrix.api.events.PlayerViolationEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Bridges Matrix events to the Velocity proxy.
 *
 * <ul>
 *   <li>{@link PlayerViolationEvent} - forwarded to Velocity so it can apply VL-threshold rules.</li>
 *   <li>{@link PlayerViolationCommandEvent} - cancelled so Matrix does not run its own punishments.</li>
 * </ul>
 *
 * <p>Transport is configurable: {@code plugin-message} (default, works for Velocity -> Paper and,
 * with the Waterfall relay, for nested proxies), {@code http} (POST directly to the Velocity HTTP
 * API, bypassing proxy relaying), or {@code both}.</p>
 */
public final class ViolationListener implements Listener, PluginMessageListener {

    /** Don't ask the proxy for config more often than this (ms). */
    private static final long SYNC_REQUEST_COOLDOWN_MS = 30_000L;

    private final JavaPlugin plugin;
    private final String serverName;
    private final boolean syncFromProxy;

    // Synced from the proxy when sync-from-proxy is enabled; otherwise taken from the local config.
    private volatile boolean cancelMatrixCommands;
    private volatile int minViolations;
    private volatile boolean debug;
    private volatile long lastSyncRequest;

    private final boolean usePluginMessage;
    private final boolean useHttp;
    private final String httpEndpoint;
    private final String httpToken;
    private final HttpClient httpClient;

    public ViolationListener(JavaPlugin plugin, boolean cancelMatrixCommands, int minViolations,
                             String serverName, boolean debug, boolean syncFromProxy,
                             String transport, String httpUrl, String httpToken) {
        this.plugin = plugin;
        this.cancelMatrixCommands = cancelMatrixCommands;
        this.minViolations = minViolations;
        this.serverName = serverName == null ? "" : serverName;
        this.debug = debug;
        this.syncFromProxy = syncFromProxy;

        String mode = transport == null ? "plugin-message" : transport.trim().toLowerCase();
        this.usePluginMessage = mode.equals("plugin-message") || mode.equals("both");
        this.useHttp = mode.equals("http") || mode.equals("both");

        String url = httpUrl == null ? "" : httpUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.httpEndpoint = url.isEmpty() ? "" : url + "/api/violation";
        this.httpToken = httpToken == null ? "" : httpToken;
        this.httpClient = useHttp
                ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                : null;

        if (useHttp && httpEndpoint.isEmpty()) {
            plugin.getLogger().warning("transport includes 'http' but http.url is empty; HTTP forwarding disabled.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onViolation(PlayerViolationEvent event) {
        if (event.getViolations() < minViolations) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        String hackType = event.getHackType() == null ? "UNKNOWN" : event.getHackType().name();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        int violations = event.getViolations();
        String message = event.getMessage();

        if (usePluginMessage) {
            ViolationMessage vm = new ViolationMessage(uuid, name, hackType, violations, message, serverName);
            byte[] data = vm.toBytes();
            if (Bukkit.isPrimaryThread()) {
                sendPluginMessage(player, data, hackType, violations);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> sendPluginMessage(player, data, hackType, violations));
            }
        }

        if (useHttp && !httpEndpoint.isEmpty()) {
            sendHttp(uuid, name, hackType, violations, message);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onViolationCommand(PlayerViolationCommandEvent event) {
        if (cancelMatrixCommands) {
            event.setCancelled(true);
        }
    }

    /** When a player joins, ask the proxy for the synced config (only over plugin messaging). */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!syncFromProxy || !usePluginMessage) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSyncRequest < SYNC_REQUEST_COOLDOWN_MS) {
            return;
        }
        lastSyncRequest = now;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, Protocol.CHANNEL, Protocol.configRequest());
                if (debug) {
                    plugin.getLogger().info("Requested synced config from the proxy.");
                }
            }
        }, 20L);
    }

    /** Receives the synced config pushed down from the proxy. */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!syncFromProxy || !Protocol.CHANNEL.equals(channel)) {
            return;
        }
        if (Protocol.peekType(data) != Protocol.TYPE_CONFIG_PUSH) {
            return;
        }
        BridgeConfigMessage cfg = BridgeConfigMessage.fromBytes(data);
        if (cfg == null) {
            return;
        }
        this.cancelMatrixCommands = cfg.cancelMatrixCommands();
        this.minViolations = cfg.minViolations();
        this.debug = cfg.debug();
        plugin.getLogger().info("Applied synced config from proxy: cancel-matrix-commands="
                + cfg.cancelMatrixCommands() + ", min-violations-to-forward=" + cfg.minViolations()
                + ", debug=" + cfg.debug() + ".");
    }

    private void sendPluginMessage(Player player, byte[] data, String hackType, int violations) {
        if (!player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, Protocol.CHANNEL, data);
        if (debug) {
            plugin.getLogger().info("Forwarded (plugin-message) " + player.getName()
                    + " " + hackType + " vl=" + violations);
        }
    }

    private void sendHttp(UUID uuid, String name, String hackType, int violations, String message) {
        String json = "{"
                + "\"uuid\":\"" + uuid + "\","
                + "\"name\":" + jsonString(name) + ","
                + "\"hackType\":" + jsonString(hackType) + ","
                + "\"violations\":" + violations + ","
                + "\"message\":" + jsonString(message) + ","
                + "\"server\":" + jsonString(serverName)
                + "}";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(httpEndpoint))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (!httpToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + httpToken);
        }
        httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        plugin.getLogger().warning("HTTP violation forward failed: " + error.getMessage());
                    } else if (debug) {
                        plugin.getLogger().info("Forwarded (http) " + name + " " + hackType
                                + " vl=" + violations + " -> " + response.statusCode());
                    }
                });
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
