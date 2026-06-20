package io.freshwater.banx.bridge;

import io.freshwater.banx.common.Protocol;
import io.freshwater.banx.common.ViolationMessage;
import me.rerere.matrix.api.events.PlayerViolationCommandEvent;
import me.rerere.matrix.api.events.PlayerViolationEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bridges Matrix events to the Velocity proxy.
 *
 * <ul>
 *   <li>{@link PlayerViolationEvent} - forwarded to Velocity so it can apply VL-threshold rules.</li>
 *   <li>{@link PlayerViolationCommandEvent} - cancelled so Matrix does not run its own punishments.</li>
 * </ul>
 */
public final class ViolationListener implements Listener {

    private final JavaPlugin plugin;
    private final boolean cancelMatrixCommands;
    private final int minViolations;
    private final String serverName;
    private final boolean debug;

    public ViolationListener(JavaPlugin plugin, boolean cancelMatrixCommands, int minViolations,
                             String serverName, boolean debug) {
        this.plugin = plugin;
        this.cancelMatrixCommands = cancelMatrixCommands;
        this.minViolations = minViolations;
        this.serverName = serverName == null ? "" : serverName;
        this.debug = debug;
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
        ViolationMessage message = new ViolationMessage(
                player.getUniqueId(),
                player.getName(),
                hackType,
                event.getViolations(),
                event.getMessage(),
                serverName);
        byte[] data = message.toBytes();

        if (Bukkit.isPrimaryThread()) {
            send(player, data, hackType, event.getViolations());
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> send(player, data, hackType, event.getViolations()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onViolationCommand(PlayerViolationCommandEvent event) {
        if (cancelMatrixCommands) {
            event.setCancelled(true);
        }
    }

    private void send(Player player, byte[] data, String hackType, int violations) {
        if (!player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, Protocol.CHANNEL, data);
        if (debug) {
            plugin.getLogger().info("Forwarded violation " + player.getName()
                    + " " + hackType + " vl=" + violations);
        }
    }
}
