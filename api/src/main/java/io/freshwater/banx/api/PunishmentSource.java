package io.freshwater.banx.api;

/** Where a punishment originated from. */
public enum PunishmentSource {
    /** Automatically issued from a Matrix anti-cheat violation. */
    MATRIX,
    /** Manually issued by a staff member via command. */
    MANUAL,
    /** Issued programmatically through the API. */
    API
}
