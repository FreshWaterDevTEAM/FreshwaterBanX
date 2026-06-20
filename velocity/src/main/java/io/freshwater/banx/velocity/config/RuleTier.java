package io.freshwater.banx.velocity.config;

import io.freshwater.banx.api.PunishmentType;

/**
 * A single VL-threshold rule: once a player's violation level reaches {@code minVl},
 * the {@code action} is applied.
 *
 * @param minVl          minimum violation level that triggers this tier
 * @param action         punishment to apply (KICK / TEMPBAN / BAN)
 * @param durationMillis duration for TEMPBAN; ignored for KICK/BAN, may be null
 * @param reason         reason string shown to the player / stored
 */
public record RuleTier(int minVl, PunishmentType action, Long durationMillis, String reason) {
}
