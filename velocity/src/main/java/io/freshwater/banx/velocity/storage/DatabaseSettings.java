package io.freshwater.banx.velocity.storage;

/** Immutable MySQL connection settings parsed from config.yml. */
public record DatabaseSettings(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize,
        String tablePrefix,
        boolean useSsl
) {
    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&autoReconnect=true";
    }

    public String table() {
        return tablePrefix + "punishments";
    }
}
