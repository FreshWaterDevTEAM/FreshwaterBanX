package io.freshwater.banx.velocity.sync;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import io.freshwater.banx.common.BridgeConfigMessage;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

/**
 * Makes the Velocity proxy the single source of truth for the Paper bridge configuration.
 *
 * <p>Bridges request their config when a player joins; the proxy replies down the same connection.
 * On {@code /fbanx reload} the proxy proactively pushes the new config to every online backend.
 * For nested setups the Waterfall relay forwards these packets down to the Paper servers.</p>
 */
public final class BridgeSync {

    private final ProxyServer proxy;
    private final PunishmentService service;
    private final ChannelIdentifier channel;
    private final Logger logger;

    public BridgeSync(ProxyServer proxy, PunishmentService service, ChannelIdentifier channel, Logger logger) {
        this.proxy = proxy;
        this.service = service;
        this.channel = channel;
        this.logger = logger;
    }

    private byte[] payload() {
        return new BridgeConfigMessage(
                service.config().bridgeCancelMatrix(),
                service.config().bridgeMinViolations(),
                service.config().bridgeDebug()
        ).toBytes();
    }

    /** Replies to a single backend that asked for its config. */
    public void replyTo(ServerConnection connection) {
        if (!service.config().bridgeSyncEnabled()) {
            return;
        }
        try {
            connection.sendPluginMessage(channel, payload());
        } catch (Exception e) {
            logger.warn("Failed to push bridge config to {}", connection.getServerInfo().getName(), e);
        }
    }

    /** Pushes the current config to every online backend (used after a reload). */
    public void pushToAll() {
        if (!service.config().bridgeSyncEnabled()) {
            return;
        }
        byte[] data = payload();
        int sent = 0;
        for (Player player : proxy.getAllPlayers()) {
            if (player.getCurrentServer().isPresent()) {
                try {
                    player.getCurrentServer().get().sendPluginMessage(channel, data);
                    sent++;
                } catch (Exception ignored) {
                    // Connection may be in transition; the next player join re-syncs it.
                }
            }
        }
        logger.info("Pushed bridge config to {} backend connection(s).", sent);
    }
}
