package io.freshwater.banx.velocity.punish;

import io.freshwater.banx.velocity.config.PluginConfig;
import io.freshwater.banx.velocity.config.RuleTier;

import java.util.List;
import java.util.Optional;

/**
 * Selects the punishment to apply based on the configured VL-threshold rules.
 */
public final class PunishmentDecider {

    private PunishmentDecider() {
    }

    /**
     * Chooses the highest tier whose {@code minVl} is &le; the player's current violations.
     *
     * @return the matching tier, or empty if the VL is below every configured threshold
     */
    public static Optional<RuleTier> decide(PluginConfig config, String hackType, int violations) {
        List<RuleTier> tiers = config.rulesFor(hackType); // ascending by minVl
        RuleTier chosen = null;
        for (RuleTier tier : tiers) {
            if (violations >= tier.minVl()) {
                chosen = tier;
            } else {
                break;
            }
        }
        return Optional.ofNullable(chosen);
    }
}
