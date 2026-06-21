package io.freshwater.banx.waterfall;

import io.freshwater.banx.common.Protocol;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.event.PluginMessageEvent;

/**
 * Relay plugin for a Waterfall/BungeeCord proxy sitting between Velocity and the backend servers.
 *
 * <p>In a {@code Velocity -> Waterfall -> Paper} topology, plugin messages on the FreshwaterBanX
 * channel only reach Waterfall. This plugin relays them one more hop in both directions:</p>
 * <ul>
 *   <li><b>Up</b> (Paper -> Velocity): violations and config requests are forwarded to the client
 *       connection (the upstream Velocity proxy).</li>
 *   <li><b>Down</b> (Velocity -> Paper): the synced config pushed by the proxy is forwarded to the
 *       player's current backend server.</li>
 * </ul>
 */
public final class FreshwaterBanXWaterfall extends Plugin implements Listener {

    @Override
    public void onEnable() {
        getProxy().registerChannel(Protocol.CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("FreshwaterBanX-Waterfall relay enabled on channel " + Protocol.CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(Protocol.CHANNEL)) {
            return;
        }
        boolean debug = Boolean.getBoolean("freshwaterbanx.debug");

        if (event.getSender() instanceof Server) {
            // Up: backend (Paper) -> client connection (Velocity).
            event.setCancelled(true);
            if (event.getReceiver() instanceof ProxiedPlayer) {
                ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
                player.sendData(Protocol.CHANNEL, event.getData());
                if (debug) {
                    ServerInfo current = player.getServer() == null ? null : player.getServer().getInfo();
                    String serverName = current == null ? "" : current.getName();
                    getLogger().info("Relayed message from " + serverName + " up to the upstream proxy.");
                }
            }
        } else if (event.getSender() instanceof ProxiedPlayer) {
            // Down: client connection (Velocity) -> backend (Paper).
            event.setCancelled(true);
            if (event.getReceiver() instanceof Server) {
                Server server = (Server) event.getReceiver();
                server.sendData(Protocol.CHANNEL, event.getData());
                if (debug) {
                    getLogger().info("Relayed config down to " + server.getInfo().getName() + ".");
                }
            }
        }
    }
}
