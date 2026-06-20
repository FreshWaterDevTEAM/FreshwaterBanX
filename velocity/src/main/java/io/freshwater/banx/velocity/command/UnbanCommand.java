package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.util.Optional;

/** {@code /unban <player>} - lifts an active ban. Permission: freshwaterbanx.unban */
public final class UnbanCommand extends AbstractCommand {

    public UnbanCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        super(proxy, service, plugin, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();
        if (!has(src, "freshwaterbanx.unban")) {
            send(src, "no-permission");
            return;
        }
        if (args.length < 1) {
            send(src, "usage-unban");
            return;
        }
        String name = args[0];
        async(() -> {
            Optional<Target> target = resolve(name);
            if (target.isEmpty()) {
                send(src, "player-not-found", target(name));
                return;
            }
            boolean removed = service.unban(target.get().id());
            send(src, removed ? "unban-success" : "unban-none", target(target.get().name()));
        });
    }
}
