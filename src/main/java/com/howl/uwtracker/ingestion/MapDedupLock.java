package com.howl.uwtracker.ingestion;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * MySQL named lock per map_id, guarding the find-or-create-run step in UploadRunService against
 * concurrent uploads from different party members' clients for the same run — specs/backend/02.
 * Coarser than a time-bucketed lock, but avoids the bucket-boundary race a time-bucketed key would
 * have, and is fine at this traffic volume (a small guild, a handful of concurrent uploads at most).
 *
 * <p>Deliberately uses its own {@link Connection} pinned for the duration of {@code action}, rather
 * than routing GET_LOCK/RELEASE_LOCK through the same connection pool the {@code @Transactional}
 * work uses via JdbcTemplate. MySQL named locks are session-scoped: if RELEASE_LOCK ran inside the
 * same transaction as the write, it would fire before that transaction commits, leaving a window
 * where a second thread acquires the lock and runs its own dedup lookup against not-yet-visible
 * data — exactly the duplicate-run race this lock exists to prevent. Holding a separate connection
 * open across the whole call (including the transactional work's own commit) and releasing only
 * after {@code action} returns closes that window.
 */
@Component
public class MapDedupLock {

    private static final int TIMEOUT_SECONDS = 10;

    private final DataSource dataSource;

    public MapDedupLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T withLock(Integer mapId, Supplier<T> action) {
        String lockName = lockName(mapId);
        try (Connection connection = dataSource.getConnection()) {
            acquire(connection, lockName);
            try {
                return action.get();
            } finally {
                release(connection, lockName);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("dedup lock JDBC failure for map " + mapId, e);
        }
    }

    private void acquire(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            stmt.setString(1, lockName);
            stmt.setInt(2, TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                int acquired = rs.getInt(1);
                if (rs.wasNull() || acquired != 1) {
                    throw new IllegalStateException("could not acquire dedup lock '" + lockName + "' within " + TIMEOUT_SECONDS + "s");
                }
            }
        }
    }

    private void release(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            stmt.setString(1, lockName);
            stmt.executeQuery();
        }
    }

    private String lockName(Integer mapId) {
        return "run-dedup:map:" + mapId;
    }
}
