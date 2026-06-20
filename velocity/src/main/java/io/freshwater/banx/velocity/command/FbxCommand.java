package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** {@code /fbanx <reload|help>} - administrative command. */
public final class FbxCommand extends AbstractCommand {

    private final ConfigReloader reloader;

    public FbxCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger,
                      ConfigReloader reloader) {
        super(proxy, service, plugin, logger);
        this.reloader = reloader;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            send(src, "help");
            return;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!has(src, "freshwaterbanx.reload")) {
                send(src, "no-permission");
                return;
            }
            async(() -> {
                try {
                    reloader.reload();
                    send(src, "reload-success");
                } catch (Exception e) {
                    logger.error("Config reload failed", e);
                    send(src, "reload-failed");
                }
            });
            return;
        }
        send(src, "help");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return Stream.of("reload", "help")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
