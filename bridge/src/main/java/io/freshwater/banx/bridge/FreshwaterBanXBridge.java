package io.freshwater.banx.bridge;

import io.freshwater.banx.common.Protocol;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper/Spigot companion plugin. Listens to Matrix violation events and forwards them to the
 * FreshwaterBanX Velocity plugin over a plugin-message channel, while cancelling Matrix's own
 * punishment commands so that all punishment decisions are made on the proxy.
 */
public final class FreshwaterBanXBridge extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("Bridge disabled via config.yml; not registering listeners.");
            return;
        }

        boolean cancelMatrix = getConfig().getBoolean("cancel-matrix-commands", true);
        int minViolations = getConfig().getInt("min-violations-to-forward", 1);
        String serverName = getConfig().getString("server-name", "");
        boolean debug = getConfig().getBoolean("debug", false);
        boolean syncFromProxy = getConfig().getBoolean("sync-from-proxy", true);
        String transport = getConfig().getString("transport", "plugin-message").toLowerCase();
        String httpUrl = getConfig().getString("http.url", "");
        String httpToken = getConfig().getString("http.token", "");

        if (getServer().getPluginManager().getPlugin("Matrix") == null) {
            getLogger().warning("Matrix plugin not found. The bridge will only work on servers running Matrix.");
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);

        ViolationListener listener = new ViolationListener(this, cancelMatrix, minViolations, serverName,
                debug, syncFromProxy, transport, httpUrl, httpToken);
        getServer().getPluginManager().registerEvents(listener, this);

        // Receive the synced config pushed down by the proxy.
        if (syncFromProxy) {
            getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, listener);
        }

        getLogger().info("FreshwaterBanX-Bridge enabled (transport=" + transport
                + ", sync-from-proxy=" + syncFromProxy
                + ", cancel-matrix-commands=" + cancelMatrix
                + ", min-violations-to-forward=" + minViolations + ").");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, Protocol.CHANNEL);
    }
}
