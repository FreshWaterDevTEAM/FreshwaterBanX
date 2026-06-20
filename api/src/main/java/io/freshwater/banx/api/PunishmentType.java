package io.freshwater.banx.api;

/** The kind of punishment applied to a player. */
public enum PunishmentType {
    /** Player was disconnected once, but may rejoin immediately. */
    KICK,
    /** Player is banned until a fixed expiry time. */
    TEMPBAN,
    /** Player is banned permanently. */
    BAN
}
