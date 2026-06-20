package io.freshwater.banx.velocity.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.api.PunishmentSource;
import io.freshwater.banx.api.PunishmentType;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MySQL-backed punishment storage using a HikariCP connection pool.
 */
public final class Database implements AutoCloseable {

    private final DatabaseSettings settings;
    private final Logger logger;
    private HikariDataSource dataSource;

    public Database(DatabaseSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    public void init() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("FreshwaterBanX-Pool");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        config.setMinimumIdle(Math.min(2, Math.max(1, settings.poolSize())));
        config.setConnectionTimeout(10_000L);
        config.setMaxLifetime(1_800_000L);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        createSchema();
        logger.info("Connected to MySQL database '{}' at {}:{}", settings.database(), settings.host(), settings.port());
    }

    private void createSchema() throws SQLException {
        String table = settings.table();
        String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                + "`uuid` CHAR(36) NOT NULL,"
                + "`name` VARCHAR(32) NOT NULL,"
                + "`type` VARCHAR(16) NOT NULL,"
                + "`source` VARCHAR(16) NOT NULL,"
                + "`reason` VARCHAR(512) NOT NULL,"
                + "`operator` VARCHAR(64) NOT NULL,"
                + "`hack_type` VARCHAR(32) NULL,"
                + "`vl` INT NOT NULL DEFAULT 0,"
                + "`created_at` BIGINT NOT NULL,"
                + "`expires_at` BIGINT NULL,"
                + "`active` TINYINT(1) NOT NULL DEFAULT 1,"
                + "PRIMARY KEY (`id`),"
                + "INDEX `idx_uuid` (`uuid`),"
                + "INDEX `idx_active` (`active`),"
                + "INDEX `idx_created_at` (`created_at`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Inserts a new punishment row and returns the persisted {@link BanEntry} (with generated id). */
    public BanEntry insert(UUID playerId,
                           String playerName,
                           PunishmentType type,
                           PunishmentSource source,
                           String reason,
                           String operator,
                           String hackType,
                           int vl,
                           Instant createdAt,
                           Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO `" + settings.table() + "` "
                + "(`uuid`,`name`,`type`,`source`,`reason`,`operator`,`hack_type`,`vl`,`created_at`,`expires_at`,`active`) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,1)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, playerName);
            ps.setString(3, type.name());
            ps.setString(4, source.name());
            ps.setString(5, reason);
            ps.setString(6, operator);
            if (hackType == null) {
                ps.setNull(7, java.sql.Types.VARCHAR);
            } else {
                ps.setString(7, hackType);
            }
            ps.setInt(8, vl);
            ps.setLong(9, createdAt.toEpochMilli());
            if (expiresAt == null) {
                ps.setNull(10, java.sql.Types.BIGINT);
            } else {
                ps.setLong(10, expiresAt.toEpochMilli());
            }
            ps.executeUpdate();
            long id = 0L;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            return new BanEntry(id, playerId, playerName, type, source, reason, operator,
                    hackType, vl, createdAt, expiresAt, true);
        }
    }

    /** Marks all active ban/temp-ban rows of the given player inactive. Returns rows affected. */
    public int deactivateActiveBans(UUID playerId) throws SQLException {
        String sql = "UPDATE `" + settings.table() + "` SET `active`=0 "
                + "WHERE `uuid`=? AND `active`=1 AND `type` IN ('BAN','TEMPBAN')";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            return ps.executeUpdate();
        }
    }

    private void deactivateById(long id) throws SQLException {
        String sql = "UPDATE `" + settings.table() + "` SET `active`=0 WHERE `id`=?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Finds the active ban for a player. Expired temp-bans are auto-deactivated and treated as none.
     */
    public Optional<BanEntry> findActiveBan(UUID playerId) throws SQLException {
        String sql = "SELECT * FROM `" + settings.table() + "` "
                + "WHERE `uuid`=? AND `active`=1 AND `type` IN ('BAN','TEMPBAN') "
                + "ORDER BY `id` DESC LIMIT 1";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                BanEntry entry = map(rs);
                if (entry.expired()) {
                    deactivateById(entry.id());
                    return Optional.empty();
                }
                return Optional.of(entry);
            }
        }
    }

    /** Returns all currently active (and non-expired) bans, deactivating any expired ones encountered. */
    public List<BanEntry> listActiveBans() throws SQLException {
        String sql = "SELECT * FROM `" + settings.table() + "` "
                + "WHERE `active`=1 AND `type` IN ('BAN','TEMPBAN') ORDER BY `created_at` DESC";
        List<BanEntry> result = new ArrayList<>();
        List<Long> expiredIds = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BanEntry entry = map(rs);
                if (entry.expired()) {
                    expiredIds.add(entry.id());
                } else {
                    result.add(entry);
                }
            }
        }
        for (long id : expiredIds) {
            deactivateById(id);
        }
        return result;
    }

    /** Most recent punishment row for a name, used to resolve offline targets to a UUID. */
    public Optional<BanEntry> findLatestByName(String name) throws SQLException {
        String sql = "SELECT * FROM `" + settings.table() + "` WHERE `name`=? ORDER BY `id` DESC LIMIT 1";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Counts distinct players banned (BAN/TEMPBAN) since local midnight today. */
    public int countBansToday() throws SQLException {
        long startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String sql = "SELECT COUNT(DISTINCT `uuid`) FROM `" + settings.table() + "` "
                + "WHERE `type` IN ('BAN','TEMPBAN') AND `created_at` >= ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, startOfDay);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private BanEntry map(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        PunishmentType type = PunishmentType.valueOf(rs.getString("type"));
        PunishmentSource source = parseSource(rs.getString("source"));
        String reason = rs.getString("reason");
        String operator = rs.getString("operator");
        String hackType = rs.getString("hack_type");
        int vl = rs.getInt("vl");
        Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
        long expiresMillis = rs.getLong("expires_at");
        Instant expiresAt = rs.wasNull() ? null : Instant.ofEpochMilli(expiresMillis);
        boolean active = rs.getInt("active") == 1;
        return new BanEntry(id, uuid, name, type, source, reason, operator, hackType, vl, createdAt, expiresAt, active);
    }

    private PunishmentSource parseSource(String raw) {
        try {
            return PunishmentSource.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return PunishmentSource.MANUAL;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
