package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.velocity.punish.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.util.List;

/** {@code /banlist} - lists active bans. Permission: freshwaterbanx.banlist */
public final class BanListCommand extends AbstractCommand {

    private static final int MAX_LINES = 50;

    public BanListCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        super(proxy, service, plugin, logger);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        if (!has(src, "freshwaterbanx.banlist")) {
            send(src, "no-permission");
            return;
        }
        async(() -> {
            List<BanEntry> bans = service.listActiveBans();
            if (bans.isEmpty()) {
                send(src, "banlist-empty");
                return;
            }
            send(src, "banlist-header", Placeholder.unparsed("count", String.valueOf(bans.size())));
            int shown = 0;
            for (BanEntry entry : bans) {
                if (shown++ >= MAX_LINES) {
                    send(src, "banlist-truncated",
                            Placeholder.unparsed("remaining", String.valueOf(bans.size() - MAX_LINES)));
                    break;
                }
                sendEntry(src, "banlist-line", entry);
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
