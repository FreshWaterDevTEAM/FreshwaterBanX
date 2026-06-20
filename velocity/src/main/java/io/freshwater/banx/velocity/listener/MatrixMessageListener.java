package io.freshwater.banx.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import io.freshwater.banx.common.Protocol;
import io.freshwater.banx.common.ViolationMessage;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Receives Matrix violation notifications forwarded by the Paper bridge over the
 * {@code freshwaterbanx:matrix} plugin-message channel and applies the configured punishment.
 */
public final class MatrixMessageListener {

    public static final String PERMISSION_BYPASS = "freshwaterbanx.bypass";

    private final Object plugin;
    private final ProxyServer proxy;
    private final PunishmentService service;
    private final Logger logger;

    public MatrixMessageListener(Object plugin, ProxyServer proxy, PunishmentService service, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.service = service;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(Protocol.CHANNEL)) {
            return;
        }
        // Our control channel: never forward to the client.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Only trust messages coming from a backend server connection.
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        ViolationMessage vm = ViolationMessage.fromBytes(event.getData());
        if (vm == null) {
            return;
        }

        Optional<Player> player = proxy.getPlayer(vm.playerId());
        if (player.isPresent() && player.get().hasPermission(PERMISSION_BYPASS)) {
            return;
        }

        proxy.getScheduler().buildTask(plugin, () -> {
            try {
                service.handleViolation(vm);
            } catch (Exception e) {
                logger.error("Failed to handle Matrix violation {}", vm, e);
            }
        }).schedule();
    }
}
