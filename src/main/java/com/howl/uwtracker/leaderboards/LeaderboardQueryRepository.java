package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.leaderboards.dto.ItemDropLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.UserStreakResponse;
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
     * {@code status = 2} (Completed) excludes Failed objectives — GWToolboxdll still fills in a real
     * {@code duration_ms} for those, so without this filter a quick death can out-rank a real clear.
     */
    public PersonalSectionBestRunRef findPersonalSectionBestRun(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        List<PersonalSectionBestRunRef> rows = jdbcTemplate.query(
                "SELECT ro.run_id, ro.duration_ms, ro.start_ms, ro.done_ms FROM run_objectives ro " +
                        "JOIN run_participants rp ON rp.run_id = ro.run_id " +
                        "JOIN characters c ON c.id = rp.character_id " +
                        "JOIN role_objectives rol ON rol.map_id = ? AND rol.objective_name = ro.name AND rol.role = rp.role " +
                        "WHERE c.person_id = ? AND ro.name = ? AND ro.status = 2 AND ro.duration_ms IS NOT NULL " +
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

    /**
     * Personal fastest-to-finish-objective, across every character the person has linked, role-gated
     * the same way as {@link #findPersonalSectionBestRun} — both are about who earns credit for the
     * objective, just ordered by a different column ({@code done_ms} instead of {@code duration_ms}).
     * Same {@code status = 2} filter and reasoning as that method.
     */
    public PersonalSectionBestRunRef findPersonalSectionFinishRun(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        List<PersonalSectionBestRunRef> rows = jdbcTemplate.query(
                "SELECT ro.run_id, ro.duration_ms, ro.start_ms, ro.done_ms FROM run_objectives ro " +
                        "JOIN run_participants rp ON rp.run_id = ro.run_id " +
                        "JOIN characters c ON c.id = rp.character_id " +
                        "JOIN role_objectives rol ON rol.map_id = ? AND rol.objective_name = ro.name AND rol.role = rp.role " +
                        "WHERE c.person_id = ? AND ro.name = ? AND ro.status = 2 AND ro.done_ms IS NOT NULL " +
                        "AND ro.run_id IN (SELECT id FROM runs WHERE map_id = ? " +
                        "AND (? IS NULL OR utc_start >= ?) AND (? IS NULL OR utc_start <= ?)) " +
                        "ORDER BY ro.done_ms ASC LIMIT 1",
                (rs, rowNum) -> new PersonalSectionBestRunRef(rs.getLong("run_id"),
                        readNullableColumnLong(rs, "duration_ms"), readNullableColumnLong(rs, "start_ms"), readNullableColumnLong(rs, "done_ms")),
                mapId, personId, objectiveName, mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Personal fastest-to-reach-objective, across every character the person has linked —
     * role-gated the same way as {@link #findPersonalSectionBestRun}: arrival time only counts for
     * a role that's actually mapped as involved in this objective, per {@code role_objectives}. Just
     * the run ref; the full gated party for that run is assembled separately via JPA, same as
     * {@link LeaderboardService#sectionStart}.
     */
    public PersonalSectionBestRunRef findPersonalSectionFastestStartRun(Long personId, Integer mapId, String objectiveName, Instant from, Instant to) {
        List<PersonalSectionBestRunRef> rows = jdbcTemplate.query(
                "SELECT ro.run_id, ro.duration_ms, ro.start_ms, ro.done_ms FROM run_objectives ro " +
                        "JOIN run_participants rp ON rp.run_id = ro.run_id " +
                        "JOIN characters c ON c.id = rp.character_id " +
                        "JOIN role_objectives rol ON rol.map_id = ? AND rol.objective_name = ro.name AND rol.role = rp.role " +
                        "WHERE c.person_id = ? AND ro.name = ? AND ro.start_ms IS NOT NULL " +
                        "AND ro.run_id IN (SELECT id FROM runs WHERE map_id = ? " +
                        "AND (? IS NULL OR utc_start >= ?) AND (? IS NULL OR utc_start <= ?)) " +
                        "ORDER BY ro.start_ms ASC LIMIT 1",
                (rs, rowNum) -> new PersonalSectionBestRunRef(rs.getLong("run_id"),
                        readNullableColumnLong(rs, "duration_ms"), readNullableColumnLong(rs, "start_ms"), readNullableColumnLong(rs, "done_ms")),
                mapId, personId, objectiveName, mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Longest run of consecutive completed runs per user, best streak only, ranked — a "gaps and
     * islands" query: {@code rn - rn_hit} is constant across a run of consecutive rows sharing the
     * same {@code is_hit} value (within a user's own utc_start-ordered timeline), so grouping on it
     * isolates each unbroken streak; {@code best_per_user} then keeps only each user's longest one
     * before the final ranking. Same {@code COALESCE(p.alias, rp.raw_name)} identity as
     * {@code LoserboardQueryRepository.findGlobalFails} — unlinked participants still count, unlike
     * the person_id-only joins above. A user with no completed runs has no {@code is_hit = 1} island
     * and is simply absent from the result, not a zero row.
     */
    public List<UserStreakResponse> findLongestCompletedStreak(Integer mapId, int limit, Instant from, Instant to) {
        return jdbcTemplate.query(
                "WITH person_runs AS (" +
                        "    SELECT DISTINCT COALESCE(p.alias, rp.raw_name) AS user, r.id AS run_id, r.utc_start, " +
                        "           CASE WHEN r.completed = TRUE THEN 1 ELSE 0 END AS is_hit " +
                        "    FROM run_participants rp " +
                        "    JOIN runs r ON r.id = rp.run_id " +
                        "    LEFT JOIN characters c ON c.id = rp.character_id " +
                        "    LEFT JOIN people p ON p.id = c.person_id " +
                        "    WHERE r.map_id = ? " +
                        "      AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?)" +
                        "), numbered AS (" +
                        "    SELECT user, run_id, utc_start, is_hit, " +
                        "           ROW_NUMBER() OVER (PARTITION BY user ORDER BY utc_start) AS rn, " +
                        "           ROW_NUMBER() OVER (PARTITION BY user, is_hit ORDER BY utc_start) AS rn_hit " +
                        "    FROM person_runs" +
                        "), islands AS (" +
                        "    SELECT user, is_hit, (rn - rn_hit) AS island_id, " +
                        "           COUNT(*) AS streak_len, MIN(utc_start) AS streak_start, MAX(utc_start) AS streak_end " +
                        "    FROM numbered " +
                        "    GROUP BY user, is_hit, island_id" +
                        "), best_per_user AS (" +
                        "    SELECT user, streak_len, streak_start, streak_end, " +
                        "           ROW_NUMBER() OVER (PARTITION BY user ORDER BY streak_len DESC, streak_end DESC) AS rn_best " +
                        "    FROM islands " +
                        "    WHERE is_hit = 1" +
                        ") " +
                        "SELECT user, streak_len, streak_start, streak_end FROM best_per_user WHERE rn_best = 1 " +
                        "ORDER BY streak_len DESC, streak_end DESC LIMIT ?",
                (rs, rowNum) -> new UserStreakResponse(rs.getString("user"), rs.getLong("streak_len"),
                        rs.getTimestamp("streak_start").toInstant(), rs.getTimestamp("streak_end").toInstant()),
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to), limit);
    }

    /**
     * One row per (tracked item, user) that has ever had that item drop for them on this map, total
     * reserved count summed across every run plus that user's average per run (total_count divided
     * by how many runs on this map they participated in at all, not just runs where this item
     * dropped — so a user who's only ever gotten the item once, in their one run, ranks above
     * someone who's gotten it five times but over fifty runs), luckiest (by average) first within
     * each item — "Luckiest Players." {@code user} is {@code COALESCE(alias, raw_name)}, same
     * unlinked-participant fallback as {@code LoserboardQueryRepository.findRoleDeaths}. Ordered by
     * {@code item_id} first so rows for the same item stay contiguous — the frontend derives its
     * per-item sub-sections directly from that grouping rather than needing a separately-maintained
     * list of tracked items.
     */
    public List<ItemDropLeaderResponse> findLuckiestPlayers(Integer mapId, Instant from, Instant to) {
        return jdbcTemplate.query(
                "WITH user_runs AS (" +
                        "    SELECT DISTINCT COALESCE(p.alias, rp.raw_name) AS user, rp.run_id " +
                        "    FROM run_participants rp " +
                        "    JOIN runs r ON r.id = rp.run_id " +
                        "    LEFT JOIN characters c ON c.id = rp.character_id " +
                        "    LEFT JOIN people p ON p.id = c.person_id " +
                        "    WHERE r.map_id = ? " +
                        "    AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?)" +
                        "), run_counts AS (" +
                        "    SELECT user, COUNT(*) AS run_count FROM user_runs GROUP BY user" +
                        "), drops AS (" +
                        "    SELECT ti.id AS item_id, ti.name AS item_name, COALESCE(p.alias, rp.raw_name) AS user, " +
                        "           SUM(rpid.drop_count) AS total_count " +
                        "    FROM run_participant_item_drops rpid " +
                        "    JOIN tracked_items ti ON ti.id = rpid.item_id " +
                        "    JOIN run_participants rp ON rp.id = rpid.run_participant_id " +
                        "    JOIN runs r ON r.id = rp.run_id " +
                        "    LEFT JOIN characters c ON c.id = rp.character_id " +
                        "    LEFT JOIN people p ON p.id = c.person_id " +
                        "    WHERE r.map_id = ? " +
                        "    AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "    GROUP BY ti.id, ti.name, COALESCE(p.alias, rp.raw_name)" +
                        ") " +
                        "SELECT d.item_id, d.item_name, d.user, d.total_count, rc.run_count, " +
                        "       d.total_count / rc.run_count AS avg_per_run " +
                        "FROM drops d " +
                        "JOIN run_counts rc ON rc.user = d.user " +
                        "ORDER BY d.item_id, avg_per_run DESC",
                (rs, rowNum) -> new ItemDropLeaderResponse(rs.getInt("item_id"), rs.getString("item_name"),
                        rs.getString("user"), rs.getLong("total_count"), rs.getLong("run_count"), rs.getDouble("avg_per_run")),
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to),
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
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
