package fr.bosketball.storage;

import fr.bosketball.model.PlayerProfile;
import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JDBC mirror backend for SQLite and MySQL/MariaDB.
 *
 * <p><b>Zero hard dependency:</b> no JDBC driver is bundled with BosketBall. The backend
 * discovers {@code org.sqlite.JDBC} / {@code com.mysql.cj.jdbc.Driver} reflectively at
 * runtime, so it activates automatically as soon as the hosting server provides a driver
 * (most Minecraft hosts do, and dropping {@code sqlite-jdbc.jar} next to the plugin is
 * enough on a standalone box). When no driver is found, BosketBall logs one clear warning
 * and keeps running on the YAML backend — the server never fails to boot because of it.</p>
 */
public final class SqlProfileBackend implements ProfileBackend {

    private static final String[] SQLITE_DRIVERS = {"org.sqlite.JDBC"};
    private static final String[] MYSQL_DRIVERS = {"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "org.mariadb.jdbc.Driver"};

    private final Logger logger;
    private final boolean mysql;
    private final String sqliteFile;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    private volatile boolean driverPresent = false;
    private volatile boolean available = false;
    private Connection connection;

    public SqlProfileBackend(
        Logger logger,
        boolean mysql,
        String sqliteFile,
        String host,
        int port,
        String database,
        String username,
        String password
    ) {
        this.logger = logger;
        this.mysql = mysql;
        this.sqliteFile = sqliteFile;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public String name() {
        return mysql ? "MySQL" : "SQLite";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize() {
        String[] candidates = mysql ? MYSQL_DRIVERS : SQLITE_DRIVERS;
        for (String driver : candidates) {
            try {
                Class<?> clazz = Class.forName(driver);
                Method register = clazz.getMethod("register");
                register.invoke(null);
                driverPresent = true;
                break;
            } catch (Throwable ignored) {
            }
        }
        if (!driverPresent) {
            if (logger != null) {
                logger.warning("[storage] " + name() + " backend requested but no JDBC driver was found on the "
                    + "server classpath. BosketBall keeps running on the YAML backend — drop "
                    + (mysql ? "mysql-connector-j" : "sqlite-jdbc") + ".jar into the server lib/ folder to enable it.");
            }
            available = false;
            return;
        }
        try {
            Class.forName(mysql ? MYSQL_DRIVERS[0] : SQLITE_DRIVERS[0]);
            this.connection = openConnection();
            createSchema();
            available = true;
            if (logger != null) {
                logger.info("[storage] " + name() + " mirror backend connected (" + describeTarget() + ").");
            }
        } catch (Exception ex) {
            available = false;
            if (logger != null) {
                logger.warning("[storage] " + name() + " backend unavailable (" + ex.getMessage()
                    + ") — falling back to YAML only.");
            }
        }
    }

    private String describeTarget() {
        return mysql ? host + ":" + port + "/" + database : sqliteFile;
    }

    private Connection openConnection() throws SQLException {
        if (mysql) {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC";
            return DriverManager.getConnection(url, username, password);
        }
        String url = "jdbc:sqlite:" + new File(sqliteFile).getAbsolutePath();
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        } catch (SQLException ignored) {
        }
        return c;
    }

    private void createSchema() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS bosketball_profiles ("
            + "uuid VARCHAR(36) PRIMARY KEY,"
            + "username VARCHAR(32) NOT NULL,"
            + "elo INT NOT NULL DEFAULT 1000,"
            + "coins INT NOT NULL DEFAULT 0,"
            + "wins INT NOT NULL DEFAULT 0,"
            + "losses INT NOT NULL DEFAULT 0,"
            + "points INT NOT NULL DEFAULT 0,"
            + "dunks INT NOT NULL DEFAULT 0,"
            + "assists INT NOT NULL DEFAULT 0,"
            + "steals INT NOT NULL DEFAULT 0,"
            + "blocks INT NOT NULL DEFAULT 0,"
            + "mvps INT NOT NULL DEFAULT 0,"
            + "xp INT NOT NULL DEFAULT 0,"
            + "playtime_seconds BIGINT NOT NULL DEFAULT 0,"
            + "updated_at BIGINT NOT NULL DEFAULT 0)";
        try (Statement st = connection.createStatement()) {
            st.execute(ddl);
        }
    }

    private Connection liveConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = openConnection();
        }
        return connection;
    }

    @Override
    public void upsertAll(List<PlayerProfile> profiles) {
        if (!available || profiles == null || profiles.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO bosketball_profiles "
            + "(uuid, username, elo, coins, wins, losses, points, dunks, assists, steals, blocks, mvps, xp, playtime_seconds, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + (mysql
            ? "ON DUPLICATE KEY UPDATE username=VALUES(username), elo=VALUES(elo), coins=VALUES(coins), wins=VALUES(wins), "
              + "losses=VALUES(losses), points=VALUES(points), dunks=VALUES(dunks), assists=VALUES(assists), "
              + "steals=VALUES(steals), blocks=VALUES(blocks), mvps=VALUES(mvps), xp=VALUES(xp), "
              + "playtime_seconds=VALUES(playtime_seconds), updated_at=VALUES(updated_at)"
            : "ON CONFLICT(uuid) DO UPDATE SET username=excluded.username, elo=excluded.elo, coins=excluded.coins, "
              + "wins=excluded.wins, losses=excluded.losses, points=excluded.points, dunks=excluded.dunks, "
              + "assists=excluded.assists, steals=excluded.steals, blocks=excluded.blocks, mvps=excluded.mvps, "
              + "xp=excluded.xp, playtime_seconds=excluded.playtime_seconds, updated_at=excluded.updated_at");
        try {
            Connection c = liveConnection();
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (PlayerProfile p : profiles) {
                    ps.setString(1, p.uuid().toString());
                    ps.setString(2, p.lastKnownName());
                    ps.setInt(3, p.elo());
                    ps.setInt(4, p.coins());
                    ps.setInt(5, p.wins());
                    ps.setInt(6, p.losses());
                    ps.setInt(7, p.points());
                    ps.setInt(8, p.dunks());
                    ps.setInt(9, p.assists());
                    ps.setInt(10, p.steals());
                    ps.setInt(11, p.blocks());
                    ps.setInt(12, p.mvps());
                    ps.setInt(13, p.xp());
                    ps.setLong(14, p.playTimeSeconds());
                    ps.setLong(15, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            c.setAutoCommit(auto);
        } catch (SQLException ex) {
            available = false;
            if (logger != null) {
                logger.warning("[storage] " + name() + " mirror write failed: " + ex.getMessage());
            }
            closeQuietly();
        }
    }

    @Override
    public void flush() {
        // Batched writes are committed per call; nothing buffered.
    }

    @Override
    public List<Row> topBy(String column, int limit) {
        if (!available || limit <= 0) {
            return List.of();
        }
        String safeColumn = switch (column) {
            case "elo", "coins", "wins", "points", "dunks", "mvps", "assists" -> column;
            default -> "elo";
        };
        String sql = "SELECT uuid, username, elo, coins, wins, points, dunks, mvps, assists FROM bosketball_profiles "
            + "ORDER BY " + safeColumn + " DESC, wins DESC LIMIT ?";
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = liveConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("username"),
                        rs.getInt("elo"),
                        rs.getInt("coins"),
                        rs.getInt("wins"),
                        rs.getInt("points"),
                        rs.getInt("dunks"),
                        rs.getInt("mvps"),
                        rs.getInt("assists")
                    ));
                }
            }
        } catch (SQLException ex) {
            available = false;
            closeQuietly();
        }
        return rows;
    }

    @Override
    public void shutdown() {
        flush();
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}
