package io.freshwater.banx.velocity.punish;

import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.velocity.config.PluginConfig;
import io.freshwater.banx.velocity.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Renders MiniMessage templates (disconnect screens and chat feedback) into Adventure components. */
public final class MessageRenderer {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Picks the right screen template for the punishment type and renders it. */
    public Component renderScreen(PluginConfig config, BanEntry entry) {
        String template = switch (entry.type()) {
            case BAN -> config.banScreen();
            case TEMPBAN -> config.tempbanScreen();
            case KICK -> config.kickScreen();
        };
        return miniMessage.deserialize(template, resolvers(entry));
    }

    /** Renders an arbitrary template with the given resolvers. */
    public Component render(String template, TagResolver... resolvers) {
        return miniMessage.deserialize(template, resolvers);
    }

    /** Renders a template using a ban entry's placeholders. */
    public Component render(String template, BanEntry entry) {
        return miniMessage.deserialize(template, resolvers(entry));
    }

    public TagResolver resolvers(BanEntry entry) {
        String expires = entry.expiresAt()
                .map(dateFormat::format)
                .orElse("never");
        String duration = entry.expiresAt()
                .map(exp -> Durations.format(Duration.between(entry.createdAt(), exp).toMillis()))
                .orElse("permanent");
        long remainingMillis = entry.remainingMillis();
        String remaining = remainingMillis == Long.MAX_VALUE ? "permanent" : Durations.format(remainingMillis);

        return TagResolver.resolver(
                Placeholder.unparsed("player", safe(entry.playerName())),
                Placeholder.unparsed("uuid", entry.playerId().toString()),
                Placeholder.unparsed("reason", safe(entry.reason())),
                Placeholder.unparsed("operator", safe(entry.operator())),
                Placeholder.unparsed("hacktype", entry.hackType().orElse("N/A")),
                Placeholder.unparsed("vl", String.valueOf(entry.violationLevel())),
                Placeholder.unparsed("id", String.valueOf(entry.id())),
                Placeholder.unparsed("type", entry.type().name()),
                Placeholder.unparsed("created", dateFormat.format(entry.createdAt())),
                Placeholder.unparsed("expires", expires),
                Placeholder.unparsed("duration", duration),
                Placeholder.unparsed("remaining", remaining)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public Instant now() {
        return Instant.now();
    }
}
