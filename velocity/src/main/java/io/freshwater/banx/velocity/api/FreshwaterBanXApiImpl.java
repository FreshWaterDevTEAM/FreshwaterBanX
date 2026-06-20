package io.freshwater.banx.velocity.api;

import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.api.FreshwaterBanXAPI;
import io.freshwater.banx.api.PunishmentSource;
import io.freshwater.banx.velocity.punish.PunishmentService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Default implementation of the public API, backed by {@link PunishmentService}. */
public final class FreshwaterBanXApiImpl implements FreshwaterBanXAPI {

    private final PunishmentService service;

    public FreshwaterBanXApiImpl(PunishmentService service) {
        this.service = service;
    }

    @Override
    public List<BanEntry> getActiveBans() {
        return service.listActiveBans();
    }

    @Override
    public Optional<BanEntry> getActiveBan(UUID playerId) {
        return service.getActiveBan(playerId);
    }

    @Override
    public boolean isBanned(UUID playerId) {
        return service.getActiveBan(playerId).map(BanEntry::blocksLogin).orElse(false);
    }

    @Override
    public long getRemainingMillis(UUID playerId) {
        return service.getActiveBan(playerId).map(BanEntry::remainingMillis).orElse(0L);
    }

    @Override
    public int getTodayBanCount() {
        return service.countToday();
    }

    @Override
    public BanEntry ban(UUID playerId, String playerName, String reason, String operator) {
        return service.ban(playerId, playerName, reason, PunishmentSource.API, operator, null, 0, null);
    }

    @Override
    public BanEntry tempBan(UUID playerId, String playerName, long durationMillis, String reason, String operator) {
        return service.ban(playerId, playerName, reason, PunishmentSource.API, operator, null, 0, durationMillis);
    }

    @Override
    public Optional<BanEntry> kick(UUID playerId, String reason, String operator) {
        return service.kick(playerId, null, reason, PunishmentSource.API, operator, null, 0);
    }

    @Override
    public boolean unban(UUID playerId) {
        return service.unban(playerId);
    }
}
