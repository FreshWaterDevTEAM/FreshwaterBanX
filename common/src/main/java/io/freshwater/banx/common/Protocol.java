package io.freshwater.banx.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Shared constants for the plugin-message bridge between the Paper bridge and the Velocity plugin.
 *
 * <p>Every packet on the channel begins with an {@code int} message type (see the {@code TYPE_*}
 * constants) so multiple message kinds can share the single channel.</p>
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

    /** Backend -> proxy: a Matrix violation. */
    public static final int TYPE_VIOLATION = 1;
    /** Backend -> proxy: request the synced bridge configuration. */
    public static final int TYPE_CONFIG_REQUEST = 2;
    /** Proxy -> backend: the synced bridge configuration. */
    public static final int TYPE_CONFIG_PUSH = 3;

    /** Reads the leading message-type int from a payload, or -1 if it cannot be read. */
    public static int peekType(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return in.readInt();
        } catch (IOException e) {
            return -1;
        }
    }

    /** Builds a (bodyless) config-request packet. */
    public static byte[] configRequest() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(TYPE_CONFIG_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }
}
