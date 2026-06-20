package io.freshwater.banx.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An immutable record of a single punishment (kick, temp-ban or permanent ban).
 */
public final class BanEntry {

    private final long id;
    private final UUID playerId;
    private final String playerName;
    private final PunishmentType type;
    private final PunishmentSource source;
    private final String reason;
    private final String operator;
    private final String hackType;
    private final int violationLevel;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final boolean active;

    public BanEntry(long id,
                    UUID playerId,
                    String playerName,
                    PunishmentType type,
                    PunishmentSource source,
                    String reason,
                    String operator,
                    String hackType,
                    int violationLevel,
                    Instant createdAt,
                    Instant expiresAt,
                    boolean active) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.type = type;
        this.source = source;
        this.reason = reason;
        this.operator = operator;
        this.hackType = hackType;
        this.violationLevel = violationLevel;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public long id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public PunishmentType type() {
        return type;
    }

    public PunishmentSource source() {
        return source;
    }

    public String reason() {
        return reason;
    }

    public String operator() {
        return operator;
    }

    /** The Matrix hack type that triggered this punishment, if any. */
    public Optional<String> hackType() {
        return Optional.ofNullable(hackType);
    }

    public int violationLevel() {
        return violationLevel;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** The expiry instant, or empty for a permanent ban / kick. */
    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    /** Whether the punishment is currently flagged active in storage. */
    public boolean active() {
        return active;
    }

    public boolean permanent() {
        return type == PunishmentType.BAN || expiresAt == null;
    }

    /** Whether this is an expired temp-ban as of now. */
    public boolean expired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** Whether this punishment should currently prevent the player from joining. */
    public boolean blocksLogin() {
        if (!active || type == PunishmentType.KICK) {
            return false;
        }
        return !expired();
    }

    /** Remaining ban time in milliseconds; 0 if expired/kick, {@link Long#MAX_VALUE} if permanent. */
    public long remainingMillis() {
        if (type == PunishmentType.KICK) {
            return 0L;
        }
        if (expiresAt == null) {
            return Long.MAX_VALUE;
        }
        long remaining = expiresAt.toEpochMilli() - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }
}
