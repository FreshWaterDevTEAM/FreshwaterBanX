package io.freshwater.banx.velocityrelay;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.freshwater.banx.common.Protocol;
import org.slf4j.Logger;

/**
 * Relay plugin for an intermediate Velocity proxy in a {@code Velocity -> Velocity -> Paper}
 * (Velocity-nested-in-Velocity) topology.
 *
 * <p>The FreshwaterBanX plugin itself runs on the <b>top</b> Velocity (decisions, storage, API,
 * commands). On the <b>middle</b> Velocity, this relay forwards the {@code freshwaterbanx:matrix}
 * channel in both directions so messages can travel the extra hop:</p>
 * <ul>
 *   <li><b>Up</b> (Paper -> top): violations and config requests from a backend are forwarded to
 *       the upstream client connection (the top Velocity).</li>
 *   <li><b>Down</b> (top -> Paper): the synced bridge config pushed by the top proxy is forwarded
 *       to the player's current backend server.</li>
 * </ul>
 */
@Plugin(
        id = "freshwaterbanx-relay",
        name = "FreshwaterBanX-VelocityRelay",
        version = "1.2.0",
        description = "Relays FreshwaterBanX plugin messages through an intermediate Velocity proxy.",
        authors = {"FreshwaterIsland"}
)
public final class FreshwaterBanXVelocityRelay {

    private final ProxyServer proxy;
    private final Logger logger;
    private final MinecraftChannelIdentifier channel =
            MinecraftChannelIdentifier.create(Protocol.CHANNEL_NAMESPACE, Protocol.CHANNEL_NAME);

    @Inject
    public FreshwaterBanXVelocityRelay(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(channel);
        logger.info("FreshwaterBanX-VelocityRelay enabled on channel {}", Protocol.CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(Protocol.CHANNEL)) {
            return;
        }
        // We forward explicitly; never let the proxy also forward this control channel.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        boolean debug = Boolean.getBoolean("freshwaterbanx.debug");

        if (event.getSource() instanceof ServerConnection connection) {
            // Up: backend (Paper) -> upstream client (the top Velocity).
            Player player = connection.getPlayer();
            if (player != null) {
                player.sendPluginMessage(channel, event.getData());
                if (debug) {
                    logger.info("Relayed message from {} up to the upstream proxy.",
                            connection.getServerInfo().getName());
                }
            }
        } else if (event.getSource() instanceof Player player) {
            // Down: upstream client (top Velocity) -> backend (Paper).
            player.getCurrentServer().ifPresent(server -> {
                server.sendPluginMessage(channel, event.getData());
                if (debug) {
                    logger.info("Relayed config down to {}.", server.getServerInfo().getName());
                }
            });
        }
    }
}
