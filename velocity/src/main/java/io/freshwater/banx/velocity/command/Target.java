package io.freshwater.banx.velocity.command;

import java.util.UUID;

/** A resolved punishment target (online player, known offline player, or raw UUID). */
public record Target(UUID id, String name) {
}
