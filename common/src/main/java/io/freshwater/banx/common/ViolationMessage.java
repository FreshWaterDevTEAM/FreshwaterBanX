package io.freshwater.banx.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * A cheat-violation notification forwarded from the Paper bridge (Matrix) to the Velocity plugin.
 *
 * <p>The wire format is a simple, dependency-free binary blob written with {@link DataOutputStream}
 * so it can be carried inside a Minecraft plugin message without pulling in any serialization
 * library on either side.</p>
 */
public final class ViolationMessage {

    private final UUID playerId;
    private final String playerName;
    private final String hackType;
    private final int violations;
    private final String message;
    private final String serverName;

    public ViolationMessage(UUID playerId,
                            String playerName,
                            String hackType,
                            int violations,
                            String message,
                            String serverName) {
        this.playerId = playerId;
        this.playerName = playerName == null ? "" : playerName;
        this.hackType = hackType == null ? "UNKNOWN" : hackType;
        this.violations = violations;
        this.message = message == null ? "" : message;
        this.serverName = serverName == null ? "" : serverName;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public String hackType() {
        return hackType;
    }

    public int violations() {
        return violations;
    }

    public String message() {
        return message;
    }

    public String serverName() {
        return serverName;
    }

    /** Serializes this message to a byte array suitable for a plugin message payload. */
    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(Protocol.VERSION);
            out.writeLong(playerId.getMostSignificantBits());
            out.writeLong(playerId.getLeastSignificantBits());
            out.writeUTF(playerName);
            out.writeUTF(hackType);
            out.writeInt(violations);
            out.writeUTF(message);
            out.writeUTF(serverName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    /** Parses a {@link ViolationMessage} from a plugin message payload, or returns {@code null} on mismatch. */
    public static ViolationMessage fromBytes(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != Protocol.VERSION) {
                return null;
            }
            long msb = in.readLong();
            long lsb = in.readLong();
            UUID id = new UUID(msb, lsb);
            String name = in.readUTF();
            String hackType = in.readUTF();
            int violations = in.readInt();
            String message = in.readUTF();
            String server = in.readUTF();
            return new ViolationMessage(id, name, hackType, violations, message, server);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ViolationMessage{" + playerName + "/" + playerId + ", " + hackType + ", vl=" + violations + "}";
    }
}
