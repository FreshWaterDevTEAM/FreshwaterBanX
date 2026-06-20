package io.freshwater.banx.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the FreshwaterBanX Velocity plugin.
 *
 * <p>Obtain an instance via {@link FreshwaterBanXProvider#get()} once the plugin has initialized.</p>
 */
public interface FreshwaterBanXAPI {

    /**
     * Returns all currently active bans (permanent and non-expired temp-bans).
     * Kicks are not included.
     */
    List<BanEntry> getActiveBans();

    /** Returns the active ban for a player, if any. */
    Optional<BanEntry> getActiveBan(UUID playerId);

    /** Whether the player currently has an active, non-expired ban. */
    boolean isBanned(UUID playerId);

    /**
     * Remaining ban time for the player in milliseconds.
     *
     * @return 0 if not banned, {@link Long#MAX_VALUE} if permanently banned
     */
    long getRemainingMillis(UUID playerId);

    /** Number of bans (permanent + temp, excluding kicks) created since local midnight today. */
    int getTodayBanCount();

    /**
     * Permanently bans a player.
     *
     * @return the created ban entry
     */
    BanEntry ban(UUID playerId, String playerName, String reason, String operator);

    /**
     * Temporarily bans a player.
     *
     * @param durationMillis ban duration from now, in milliseconds
     * @return the created ban entry
     */
    BanEntry tempBan(UUID playerId, String playerName, long durationMillis, String reason, String operator);

    /**
     * Kicks an online player. No-op (returns empty) if the player is not connected.
     *
     * @return the recorded kick entry, if the player was online
     */
    Optional<BanEntry> kick(UUID playerId, String reason, String operator);

    /**
     * Lifts any active ban for the player.
     *
     * @return true if an active ban was removed
     */
    boolean unban(UUID playerId);
}
