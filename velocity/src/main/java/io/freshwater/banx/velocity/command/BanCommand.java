package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.api.PunishmentSource;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.util.Optional;

/** {@code /ban <player> [reason]} - permanent ban. Permission: freshwaterbanx.ban */
public final class BanCommand extends AbstractCommand {

    public BanCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        super(proxy, service, plugin, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();
        if (!has(src, "freshwaterbanx.ban")) {
            send(src, "no-permission");
            return;
        }
        if (args.length < 1) {
            send(src, "usage-ban");
            return;
        }
        String name = args[0];
        String reason = reasonOrDefault(args, 1);
        String operator = operatorName(src);
        async(() -> {
            Optional<Target> target = resolve(name);
            if (target.isEmpty()) {
                send(src, "player-not-found", target(name));
                return;
            }
            BanEntry entry = service.ban(target.get().id(), target.get().name(), reason,
                    PunishmentSource.MANUAL, operator, null, 0, null);
            sendEntry(src, "ban-success", entry);
        });
    }
}
