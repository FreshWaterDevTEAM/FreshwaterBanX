package io.freshwater.banx.common;

/**
 * Shared constants for the plugin-message bridge between the Paper bridge and the Velocity plugin.
 */
public final class Protocol {

    private Protocol() {
    }

    /** Plugin message channel namespace. */
    public static final String CHANNEL_NAMESPACE = "freshwaterbanx";

    /** Plugin message channel name. */
    public static final String CHANNEL_NAME = "matrix";

    /** Full channel identifier string, e.g. {@code freshwaterbanx:matrix}. */
    public static final String CHANNEL = CHANNEL_NAMESPACE + ":" + CHANNEL_NAME;

    /** Protocol version, bumped if the wire format changes. */
    public static final int VERSION = 1;
}
