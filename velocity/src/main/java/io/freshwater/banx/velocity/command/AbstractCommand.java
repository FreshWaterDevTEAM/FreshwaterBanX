package io.freshwater.banx.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.velocity.punish.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Shared helpers for all FreshwaterBanX commands. */
public abstract class AbstractCommand implements SimpleCommand {

    public static final String PERMISSION_ADMIN = "freshwaterbanx.admin";

    protected final ProxyServer proxy;
    protected final PunishmentService service;
    protected final Object plugin;
    protected final Logger logger;

    protected AbstractCommand(ProxyServer proxy, PunishmentService service, Object plugin, Logger logger) {
        this.proxy = proxy;
        this.service = service;
        this.plugin = plugin;
        this.logger = logger;
    }

    protected boolean has(CommandSource source, String node) {
        return source.hasPermission(node) || source.hasPermission(PERMISSION_ADMIN);
    }

    protected void async(Runnable runnable) {
        proxy.getScheduler().buildTask(plugin, () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                logger.error("Command execution failed", e);
            }
        }).schedule();
    }

    protected void send(CommandSource source, String key, TagResolver... resolvers) {
        source.sendMessage(service.renderer().render(service.config().message(key), resolvers));
    }

    protected void sendEntry(CommandSource source, String key, BanEntry entry) {
        source.sendMessage(service.renderer().render(service.config().message(key), entry));
    }

    protected String operatorName(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername();
        }
        return "Console";
    }

    protected String reasonOrDefault(String[] args, int from) {
        if (args.length > from) {
            return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
        }
        return service.config().message("default-reason");
    }

    /** Resolves a name to a target: online player first, then last-known DB entry, then a raw UUID. */
    protected Optional<Target> resolve(String name) {
        Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            return Optional.of(new Target(online.get().getUniqueId(), online.get().getUsername()));
        }
        Optional<BanEntry> last = service.findLatestByName(name);
        if (last.isPresent()) {
            return Optional.of(new Target(last.get().playerId(), last.get().playerName()));
        }
        try {
            return Optional.of(new Target(UUID.fromString(name), name));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    protected TagResolver target(String name) {
        return Placeholder.unparsed("target", name);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
