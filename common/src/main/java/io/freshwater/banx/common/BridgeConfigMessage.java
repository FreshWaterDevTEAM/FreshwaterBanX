package io.freshwater.banx.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The bridge configuration pushed from the Velocity plugin (single source of truth) down to every
 * Paper bridge, so administrators only edit the proxy config.
 */
public final class BridgeConfigMessage {

    private final boolean cancelMatrixCommands;
    private final int minViolations;
    private final boolean debug;

    public BridgeConfigMessage(boolean cancelMatrixCommands, int minViolations, boolean debug) {
        this.cancelMatrixCommands = cancelMatrixCommands;
        this.minViolations = minViolations;
        this.debug = debug;
    }

    public boolean cancelMatrixCommands() {
        return cancelMatrixCommands;
    }

    public int minViolations() {
        return minViolations;
    }

    public boolean debug() {
        return debug;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(Protocol.TYPE_CONFIG_PUSH);
            out.writeBoolean(cancelMatrixCommands);
            out.writeInt(minViolations);
            out.writeBoolean(debug);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    public static BridgeConfigMessage fromBytes(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int type = in.readInt();
            if (type != Protocol.TYPE_CONFIG_PUSH) {
                return null;
            }
            boolean cancel = in.readBoolean();
            int minViolations = in.readInt();
            boolean debug = in.readBoolean();
            return new BridgeConfigMessage(cancel, minViolations, debug);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "BridgeConfigMessage{cancelMatrixCommands=" + cancelMatrixCommands
                + ", minViolations=" + minViolations + ", debug=" + debug + "}";
    }
}
