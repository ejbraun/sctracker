package com.howl.uwtracker.loserboards;

import com.howl.uwtracker.leaderboards.dto.UserStreakResponse;
import com.howl.uwtracker.loserboards.dto.RezScrollEntryResponse;
import com.howl.uwtracker.loserboards.dto.RoleUserDeathsResponse;
import com.howl.uwtracker.loserboards.dto.UserResignResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Raw SQL for "Loserboards" aggregates — same rationale as
 * {@code com.howl.uwtracker.leaderboards.LeaderboardQueryRepository}: cross-entity aggregates that
 * don't map cleanly onto a single Spring Data repository.
 */
@Repository
public class LoserboardQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoserboardQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * One row per user who has ever participated on this map — "Global fails" (resigns), not
     * role-scoped. Ordered by resign rate (percentage), not raw resign count — someone with 2
     * resigns in 2 runs belongs above someone with 5 resigns in 50.
     */
    public List<UserResignResponse> findGlobalFails(Integer mapId, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT COALESCE(p.alias, rp.raw_name) AS user, " +
                        "COUNT(*) AS total_runs, " +
                        "SUM(CASE WHEN r.end_reason = 'resign' AND r.completed = FALSE THEN 1 ELSE 0 END) AS resigns " +
                        "FROM run_participants rp " +
                        "JOIN runs r ON r.id = rp.run_id " +
                        "LEFT JOIN characters c ON c.id = rp.character_id " +
                        "LEFT JOIN people p ON p.id = c.person_id " +
                        "WHERE r.map_id = ? " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "GROUP BY COALESCE(p.alias, rp.raw_name) " +
                        "ORDER BY (resigns / total_runs) DESC",
                (rs, rowNum) -> {
                    long totalRuns = rs.getLong("total_runs");
                    long resigns = rs.getLong("resigns");
                    double percentage = totalRuns == 0 ? 0.0 : (resigns * 100.0) / totalRuns;
                    return new UserResignResponse(rs.getString("user"), totalRuns, resigns, percentage);
                },
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
    }

    /**
     * One row per (role, user) that has ever played that role on this map, deaths summed. Not
     * scoped to completed runs — a death counts whether the run wiped or succeeded. {@code user} is
     * {@code COALESCE(alias, raw_name)} — an unlinked participant is grouped by raw name, same
     * fallback the frontend already uses for display. Excludes participants with a null role
     * (unresolved profession combo) since they don't belong to any of the 8 role sections. Ordered
     * by deaths-per-run, not raw total — someone with 20 deaths across 100 runs isn't worse than
     * someone with 5 deaths across 5.
     */
    public List<RoleUserDeathsResponse> findRoleDeaths(Integer mapId, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT rp.role AS role, COALESCE(p.alias, rp.raw_name) AS user, " +
                        "COUNT(*) AS total_runs, SUM(rp.deaths) AS total_deaths " +
                        "FROM run_participants rp " +
                        "JOIN runs r ON r.id = rp.run_id " +
                        "LEFT JOIN characters c ON c.id = rp.character_id " +
                        "LEFT JOIN people p ON p.id = c.person_id " +
                        "WHERE r.map_id = ? AND rp.role IS NOT NULL " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "GROUP BY rp.role, COALESCE(p.alias, rp.raw_name) " +
                        "ORDER BY (total_deaths / total_runs) DESC",
                (rs, rowNum) -> {
                    long totalRuns = rs.getLong("total_runs");
                    long deaths = rs.getLong("total_deaths");
                    double avgDeaths = totalRuns == 0 ? 0.0 : ((double) deaths) / totalRuns;
                    return new RoleUserDeathsResponse(rs.getString("role"), rs.getString("user"), totalRuns, deaths, avgDeaths);
                },
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to));
    }

    /**
     * Longest run of consecutive non-completed runs (resign or wipe, combined into one "bad
     * outcome" category — not tracked separately) per user, best streak only, ranked. Same
     * gaps-and-islands shape as {@code LeaderboardQueryRepository.findLongestCompletedStreak}, just
     * an inverted {@code is_hit} predicate. A run with {@code end_reason = 'unknown'} is neither a
     * "completed" hit nor a "bad" hit — it's its own island and correctly breaks a streak either way.
     */
    public List<UserStreakResponse> findLongestBadStreak(Integer mapId, int limit, Instant from, Instant to) {
        return jdbcTemplate.query(
                "WITH person_runs AS (" +
                        "    SELECT DISTINCT COALESCE(p.alias, rp.raw_name) AS user, r.id AS run_id, r.utc_start, " +
                        "           CASE WHEN r.completed = FALSE AND r.end_reason IN ('resign', 'wipe') THEN 1 ELSE 0 END AS is_hit " +
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
     * One row per (run, participant), ranked by that individual's own rez_scroll_uses in that run —
     * "Most res scroll uses in a run." Per-player, not summed across the party: a player who single-
     * handedly rez-scrolled a run belongs at the top, not diluted by teammates who used none. Not
     * scoped to completed runs, same reasoning as {@link #findRoleDeaths}: heavy scroll usage often
     * happens trying to save a run that still wipes.
     */
    public List<RezScrollEntryResponse> findMostRezScrollUses(Integer mapId, int limit, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT r.id AS run_id, r.utc_start AS utc_start, COALESCE(p.alias, rp.raw_name) AS user, " +
                        "rp.role AS role, rp.rez_scroll_uses AS rez_scroll_uses " +
                        "FROM run_participants rp " +
                        "JOIN runs r ON r.id = rp.run_id " +
                        "LEFT JOIN characters c ON c.id = rp.character_id " +
                        "LEFT JOIN people p ON p.id = c.person_id " +
                        "WHERE r.map_id = ? " +
                        "AND (? IS NULL OR r.utc_start >= ?) AND (? IS NULL OR r.utc_start <= ?) " +
                        "ORDER BY rez_scroll_uses DESC " +
                        "LIMIT ?",
                (rs, rowNum) -> new RezScrollEntryResponse(rs.getLong("run_id"), rs.getTimestamp("utc_start").toInstant(),
                        rs.getString("user"), rs.getString("role"), rs.getInt("rez_scroll_uses")),
                mapId, toTimestamp(from), toTimestamp(from), toTimestamp(to), toTimestamp(to), limit);
    }

    /** {@code java.time.Instant} isn't one of JDBC 4.2's mandated {@code setObject} conversions; convert explicitly. */
    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
