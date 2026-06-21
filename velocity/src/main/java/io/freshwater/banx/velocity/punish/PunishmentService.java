package io.freshwater.banx.velocity.punish;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.api.PunishmentSource;
import io.freshwater.banx.api.PunishmentType;
import io.freshwater.banx.common.ViolationMessage;
import io.freshwater.banx.velocity.config.PluginConfig;
import io.freshwater.banx.velocity.config.RuleTier;
import io.freshwater.banx.velocity.storage.Database;
import io.freshwater.banx.velocity.storage.StorageException;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Central logic for applying and querying punishments. Methods that touch the database are blocking
 * and should be invoked off the main/connection thread by callers.
 */
public final class PunishmentService {

    public static final String PERMISSION_NOTIFY = "freshwaterbanx.notify";
    public static final String PERMISSION_BYPASS = "freshwaterbanx.bypass";

    private final ProxyServer proxy;
    private final Database database;
    private final MessageRenderer renderer;
    private final Logger logger;
    private volatile PluginConfig config;

    public PunishmentService(ProxyServer proxy, Database database, MessageRenderer renderer,
                             Logger logger, PluginConfig config) {
        this.proxy = proxy;
        this.database = database;
        this.renderer = renderer;
        this.logger = logger;
        this.config = config;
    }

    public void setConfig(PluginConfig config) {
        this.config = config;
    }

    public PluginConfig config() {
        return config;
    }

    public MessageRenderer renderer() {
        return renderer;
    }

    // ---- Applying punishments ----

    /**
     * Bans a player. A null {@code durationMillis} means a permanent ban; otherwise a temp-ban.
     * Any existing active ban for the player is replaced.
     */
    public BanEntry ban(UUID playerId, String playerName, String reason, PunishmentSource source,
                        String operator, String hackType, int vl, Long durationMillis) {
        Instant now = Instant.now();
        PunishmentType type = durationMillis == null ? PunishmentType.BAN : PunishmentType.TEMPBAN;
        Instant expires = durationMillis == null ? null : now.plusMillis(durationMillis);
        try {
            database.deactivateActiveBans(playerId);
            BanEntry entry = database.insert(playerId, playerName, type, source, reason, operator,
                    hackType, vl, now, expires);
            disconnect(playerId, renderer.renderScreen(config, entry));
            broadcast(entry);
            logger.info("[{}] {} ({}) banned by {} reason='{}' type={}",
                    source, playerName, playerId, operator, reason, type);
            return entry;
        } catch (SQLException e) {
            throw new StorageException("Failed to ban player " + playerId, e);
        }
    }

    /** Kicks an online player and records it. Returns empty if the player is offline. */
    public Optional<BanEntry> kick(UUID playerId, String playerName, String reason, PunishmentSource source,
                                   String operator, String hackType, int vl) {
        Optional<Player> online = proxy.getPlayer(playerId);
        if (online.isEmpty()) {
            return Optional.empty();
        }
        Player player = online.get();
        String name = (playerName == null || playerName.isBlank()) ? player.getUsername() : playerName;
        Instant now = Instant.now();
        try {
            BanEntry entry = database.insert(playerId, name, PunishmentType.KICK, source, reason, operator,
                    hackType, vl, now, null);
            player.disconnect(renderer.renderScreen(config, entry));
            broadcast(entry);
            logger.info("[{}] {} ({}) kicked by {} reason='{}'", source, name, playerId, operator, reason);
            return Optional.of(entry);
        } catch (SQLException e) {
            throw new StorageException("Failed to kick player " + playerId, e);
        }
    }

    /** Lifts any active ban for a player. Returns true if something was removed. */
    public boolean unban(UUID playerId) {
        try {
            return database.deactivateActiveBans(playerId) > 0;
        } catch (SQLException e) {
            throw new StorageException("Failed to unban player " + playerId, e);
        }
    }

    /** Applies the configured rule for a forwarded Matrix violation. */
    public void handleViolation(ViolationMessage vm) {
        // Players with the bypass permission are never auto-punished (applies to all transports).
        Optional<Player> online = proxy.getPlayer(vm.playerId());
        if (online.isPresent() && online.get().hasPermission(PERMISSION_BYPASS)) {
            return;
        }
        Optional<RuleTier> tierOpt = PunishmentDecider.decide(config, vm.hackType(), vm.violations());
        if (tierOpt.isEmpty()) {
            return;
        }
        RuleTier tier = tierOpt.get();
        String operator = "Matrix";
        switch (tier.action()) {
            case KICK -> kick(vm.playerId(), vm.playerName(), tier.reason(),
                    PunishmentSource.MATRIX, operator, vm.hackType(), vm.violations());
            case TEMPBAN -> ban(vm.playerId(), vm.playerName(), tier.reason(),
                    PunishmentSource.MATRIX, operator, vm.hackType(), vm.violations(), tier.durationMillis());
            case BAN -> ban(vm.playerId(), vm.playerName(), tier.reason(),
                    PunishmentSource.MATRIX, operator, vm.hackType(), vm.violations(), null);
        }
    }

    // ---- Queries ----

    public Optional<BanEntry> getActiveBan(UUID playerId) {
        try {
            return database.findActiveBan(playerId);
        } catch (SQLException e) {
            throw new StorageException("Failed to query ban for " + playerId, e);
        }
    }

    public List<BanEntry> listActiveBans() {
        try {
            return database.listActiveBans();
        } catch (SQLException e) {
            throw new StorageException("Failed to list active bans", e);
        }
    }

    public int countToday() {
        try {
            return database.countBansToday();
        } catch (SQLException e) {
            throw new StorageException("Failed to count today's bans", e);
        }
    }

    public Optional<BanEntry> findLatestByName(String name) {
        try {
            return database.findLatestByName(name);
        } catch (SQLException e) {
            throw new StorageException("Failed to look up name " + name, e);
        }
    }

    // ---- Helpers ----

    private void disconnect(UUID playerId, Component screen) {
        proxy.getPlayer(playerId).ifPresent(p -> p.disconnect(screen));
    }

    private void broadcast(BanEntry entry) {
        if (!config.broadcastEnabled()) {
            return;
        }
        String key = switch (entry.type()) {
            case BAN -> "broadcast-ban";
            case TEMPBAN -> "broadcast-tempban";
            case KICK -> "broadcast-kick";
        };
        Component message = renderer.render(config.message(key), entry);
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(PERMISSION_NOTIFY)) {
                player.sendMessage(message);
            }
        }
    }
}
