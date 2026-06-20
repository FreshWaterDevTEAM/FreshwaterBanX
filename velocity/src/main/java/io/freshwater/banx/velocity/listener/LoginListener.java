package io.freshwater.banx.velocity.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.velocity.punish.PunishmentService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Optional;

/** Blocks banned players from connecting, showing the configured ban screen. */
public final class LoginListener {

    private final PunishmentService service;
    private final Logger logger;

    public LoginListener(PunishmentService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.async(() -> {
            try {
                Optional<BanEntry> ban = service.getActiveBan(player.getUniqueId());
                if (ban.isPresent() && ban.get().blocksLogin()) {
                    Component screen = service.renderer().renderScreen(service.config(), ban.get());
                    event.setResult(ResultedEvent.ComponentResult.denied(screen));
                }
            } catch (Exception e) {
                // On storage failure, fail open (allow login) but log it loudly.
                logger.error("Failed to check ban status for {} ({})",
                        player.getUsername(), player.getUniqueId(), e);
            }
        });
    }
}
