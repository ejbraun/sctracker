package com.howl.uwtracker.leaderboards;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Cross-entity aggregate scalar queries that don't fit naturally as a Spring Data repository tied
 * to one entity — specs/backend/05-leaderboards.md's personal-best queries. Native SQL matching the
 * spec exactly, via JdbcTemplate directly.
 */
@Repository
public class LeaderboardQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public LeaderboardQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Full-run personal best, completed runs only, aggregated across every character the person has linked. */
    public Long findPersonalOverallBestMs(Long personId, Integer mapId) {
        return jdbcTemplate.query(
                "SELECT MIN(r.duration_ms) FROM runs r " +
                        "JOIN run_participants rp ON rp.run_id = r.id " +
                        "JOIN characters c ON c.id = rp.character_id " +
                        "WHERE c.person_id = ? AND r.map_id = ? AND r.completed = TRUE",
                LeaderboardQueryRepository::readNullableLong,
                personId, mapId);
    }

    /**
     * Section personal best, role-gated: a participant's objective time only counts if their role
     * in that run is mapped as involved in that objective, per {@code role_objectives}. See the
     * "role-gated" note in specs/backend/05-leaderboards.md — without this join, e.g. a spiker would
     * get credit for an Escort time they had no part in, just for having been in the party. Just the
     * run ref; the full gated participant list for that run (everyone gated in, not just the
     * person's own character — see {@link LeaderboardService#personalSectionBestMs}) is assembled
     * separately via JPA, since that touches lazy associations raw JDBC can't map onto directly.
     */
    public PersonalSectionBestRunRef findPersonalSectionBestRun(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        List<PersonalSectionBestRunRef> rows = jdbcTemplate.query(
                "SELECT ro.run_id, ro.duration_ms, ro.start_ms, ro.done_ms FROM run_objectives ro " +
                        "JOIN run_participants rp ON rp.run_id = ro.run_id " +
                        "JOIN characters c ON c.id = rp.character_id " +
                        "JOIN role_objectives rol ON rol.map_id = ? AND rol.objective_name = ro.name AND rol.role = rp.role " +
                        "WHERE c.person_id = ? AND ro.name = ? AND ro.duration_ms IS NOT NULL " +
                        "AND ro.run_id IN (SELECT id FROM runs WHERE map_id = ? " +
                        "AND (? IS NULL OR utc_start >= ?) AND (? IS NULL OR utc_start <= ?)) " +
                        "ORDER BY ro.duration_ms ASC LIMIT 1",
                (rs, rowNum) -> new PersonalSectionBestRunRef(rs.getLong("run_id"),
                        readNullableColumnLong(rs, "duration_ms"), readNullableColumnLong(rs, "start_ms"), readNullableColumnLong(rs, "done_ms")),
                mapId, personId, objectiveName, mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * The person's own top N completed runs on the map (across every character they've linked),
     * fastest first — backs the "Yours" top-10 table (specs/frontend/04). Just the run refs; the
     * full participant list (to match "Global"'s schema) is assembled in {@link LeaderboardService}
     * via JPA, since that touches lazy associations raw JDBC can't map onto directly. {@code from}/
     * {@code to} are optional (null means unbounded) — the time-window filter.
     */
    public List<PersonalBestRunRef> findPersonalOverallTop(Long personId, Integer mapId, int limit, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT DISTINCT r.id, r.duration_ms, r.utc_start FROM runs r " +
                        "JOIN run_participants rp ON rp.run_id = r.id " +
                        "JOIN characters c ON c.id = rp.character_id " +
                        "WHERE c.person_id = ? AND r.map_id = ? AND r.completed = TRUE " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "ORDER BY r.duration_ms ASC LIMIT ?",
                (rs, rowNum) -> new PersonalBestRunRef(rs.getLong("id"), rs.getLong("duration_ms"), rs.getTimestamp("utc_start").toInstant()),
                personId, mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to), limit);
    }

    /** {@code java.time.Instant} isn't one of JDBC 4.2's mandated {@code setObject} conversions; convert explicitly. */
    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /**
     * {@code duration_ms} is {@code BIGINT UNSIGNED} (specs/backend/01-schema-and-migrations.md) —
     * mysql-connector-j returns unsigned BIGINT values from {@code getObject()} as
     * {@link java.math.BigInteger}, not {@link Long} (its range can exceed {@code Long.MAX_VALUE}),
     * so a direct cast throws {@link ClassCastException} against a real MySQL. {@code getLong()}
     * converts correctly regardless of the underlying type; {@code wasNull()} distinguishes a real
     * zero from SQL NULL (no matching row), which {@code getLong()} alone can't (it returns 0 for
     * both). Never caught by mvn test — no unit test exercises this against a live driver.
     */
    private static Long readNullableLong(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
        long value = rs.getLong(1);
        return rs.wasNull() ? null : value;
    }

    /** Same {@code getLong()}/{@code wasNull()} caveat as above, for use inside a per-row {@link org.springframework.jdbc.core.RowMapper}. */
    private static Long readNullableColumnLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }
}
