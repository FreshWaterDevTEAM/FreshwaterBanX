package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.util.Optional;

/** {@code /baninfo <player>} - shows the active ban for a player. Permission: freshwaterbanx.baninfo */
public final class BanInfoCommand extends AbstractCommand {

    public BanInfoCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        super(proxy, service, plugin, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();
        if (!has(src, "freshwaterbanx.baninfo")) {
            send(src, "no-permission");
            return;
        }
        if (args.length < 1) {
            send(src, "usage-baninfo");
            return;
        }
        String name = args[0];
        async(() -> {
            Optional<Target> target = resolve(name);
            if (target.isEmpty()) {
                send(src, "player-not-found", target(name));
                return;
            }
            Optional<BanEntry> ban = service.getActiveBan(target.get().id());
            if (ban.isEmpty()) {
                send(src, "baninfo-none", target(target.get().name()));
            } else {
                sendEntry(src, "baninfo-line", ban.get());
            }
        });
    }
}
