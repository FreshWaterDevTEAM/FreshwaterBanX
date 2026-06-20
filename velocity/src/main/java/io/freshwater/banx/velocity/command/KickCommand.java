package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.PunishmentSource;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/** {@code /kick <player> [reason]} - kicks an online player. Permission: freshwaterbanx.kick */
public final class KickCommand extends AbstractCommand {

    public KickCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        super(proxy, service, plugin, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();
        if (!has(src, "freshwaterbanx.kick")) {
            send(src, "no-permission");
            return;
        }
        if (args.length < 1) {
            send(src, "usage-kick");
            return;
        }
        String name = args[0];
        Optional<Player> player = proxy.getPlayer(name);
        if (player.isEmpty()) {
            send(src, "kick-offline", target(name));
            return;
        }
        UUID id = player.get().getUniqueId();
        String username = player.get().getUsername();
        String reason = reasonOrDefault(args, 1);
        String operator = operatorName(src);
        async(() -> service.kick(id, username, reason, PunishmentSource.MANUAL, operator, null, 0)
                .ifPresentOrElse(
                        entry -> sendEntry(src, "kick-success", entry),
                        () -> send(src, "kick-offline", target(name))));
    }
}
