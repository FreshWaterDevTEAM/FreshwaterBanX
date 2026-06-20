package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.api.PunishmentSource;
import io.freshwater.banx.velocity.punish.PunishmentService;
import io.freshwater.banx.velocity.util.Durations;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.util.Optional;

/** {@code /tempban <player> <duration> [reason]} - temporary ban. Permission: freshwaterbanx.tempban */
public final class TempBanCommand extends AbstractCommand {

    public TempBanCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        super(proxy, service, plugin, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();
        if (!has(src, "freshwaterbanx.tempban")) {
            send(src, "no-permission");
            return;
        }
        if (args.length < 2) {
            send(src, "usage-tempban");
            return;
        }
        String name = args[0];
        String durationStr = args[1];
        long duration = Durations.parse(durationStr);
        if (duration <= 0L) {
            send(src, "invalid-duration", Placeholder.unparsed("input", durationStr));
            return;
        }
        String reason = reasonOrDefault(args, 2);
        String operator = operatorName(src);
        Long durationMillis = duration == Long.MAX_VALUE ? null : duration; // "perm" -> permanent
        async(() -> {
            Optional<Target> target = resolve(name);
            if (target.isEmpty()) {
                send(src, "player-not-found", target(name));
                return;
            }
            BanEntry entry = service.ban(target.get().id(), target.get().name(), reason,
                    PunishmentSource.MANUAL, operator, null, 0, durationMillis);
            sendEntry(src, durationMillis == null ? "ban-success" : "tempban-success", entry);
        });
    }
}
