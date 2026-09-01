import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type {
  FailureReasonEntry,
  LeaderboardEntry,
  OutdatedPlugin,
  RoleUserDeaths,
  RunDetail,
  SectionEntry,
  UserResign,
  UserStreak,
} from '../api/types';
import { ExpandToggle } from '../components/ExpandToggle';
import { Panel } from '../components/Panel';
import { RoleBadge } from '../components/RoleBadge';
import { RunLinkRow } from '../components/RunLinkRow';
import { formatDate, formatDuration } from '../common/format';
import { TIME_WINDOWS, TIME_WINDOW_LABELS, timeWindowFrom, type TimeWindow } from '../common/timeWindows';
import { DEFAULT_MAP_ID, configHasRoles, defaultPartySize, rolesForConfig } from '../common/maps';
import { MapSizePicker } from '../components/MapSizePicker';
import styles from './LoserboardsPage.module.css';

/**
 * The mirror image of LeaderboardPage — mirrors its time-window filter and its `/loserboards/:mapId`
 * map-scoped route (see specs/features/fow-and-party-size.md §6.3).
 */
export function LoserboardsPage() {
  const { mapId = DEFAULT_MAP_ID } = useParams<{ mapId: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [timeWindow, setTimeWindow] = useState<TimeWindow>('all');
  const from = timeWindowFrom(timeWindow);
  // Same expand-to-top-5 behavior as LeaderboardPage's ranked-run tables — "Slowest Completions"
  // defaults open, mirroring "Fastest To Complete Instance" there.
  const [worstExpanded, setWorstExpanded] = useState(true);

  const partySize = Number(searchParams.get('partySize')) || defaultPartySize(mapId) || 8;
  const sizeAndWindow = `partySize=${partySize}${from ? `&from=${encodeURIComponent(from)}` : ''}`;

  const worstQuery = useQuery({
    queryKey: ['loserboard', 'worst', mapId, partySize, timeWindow],
    queryFn: () =>
      api.get<LeaderboardEntry[]>(`/loserboards/maps/${mapId}/worst?limit=10&${sizeAndWindow}`),
  });

  const roleDeathsQuery = useQuery({
    queryKey: ['loserboard', 'role-deaths', mapId, partySize, timeWindow],
    queryFn: () =>
      api.get<RoleUserDeaths[]>(`/loserboards/maps/${mapId}/role-deaths?${sizeAndWindow}`),
    enabled: configHasRoles(mapId, partySize),
  });

  const globalFailsQuery = useQuery({
    queryKey: ['loserboard', 'global-fails', mapId, partySize, timeWindow],
    queryFn: () =>
      api.get<UserResign[]>(`/loserboards/maps/${mapId}/global-fails?${sizeAndWindow}`),
  });

  const failureReasonsQuery = useQuery({
    queryKey: ['loserboard', 'role-failure-reasons', mapId, partySize, timeWindow],
    queryFn: () =>
      api.get<FailureReasonEntry[]>(
        `/loserboards/maps/${mapId}/role-failure-reasons?${sizeAndWindow}`,
      ),
    enabled: configHasRoles(mapId, partySize),
  });

  const badStreakQuery = useQuery({
    queryKey: ['loserboard', 'streaks', 'bad', mapId, partySize, timeWindow],
    queryFn: () =>
      api.get<UserStreak[]>(`/loserboards/maps/${mapId}/streaks/bad?limit=10&${sizeAndWindow}`),
  });

  // Not map-scoped, unlike every other query here — this is per-person plugin-version state, nothing
  // about a run. "Active within the selected time window AND behind the current minimum version."
  const outdatedPluginsQuery = useQuery({
    queryKey: ['loserboard', 'outdated-plugins', timeWindow],
    queryFn: () =>
      api.get<OutdatedPlugin[]>(`/loserboards/outdated-plugins${from ? `?from=${encodeURIComponent(from)}` : ''}`),
  });

  // The set of objective names isn't statically known — pull them from the slowest completed
  // run's detail, same approach LeaderboardPage uses off its own "overall" query.
  const firstRunId = worstQuery.data?.[0]?.run_id;
  const firstRunDetailQuery = useQuery({
    queryKey: ['run-detail-for-sections', firstRunId],
    queryFn: () => api.get<RunDetail>(`/runs/${firstRunId}`),
    enabled: firstRunId != null,
  });
  const objectiveNames = firstRunDetailQuery.data?.objectives.map((o) => o.name) ?? [];

  return (
    <div>
      <h1>Loserboards</h1>

      <Panel className={styles.section}>
        <MapSizePicker
          mapId={mapId}
          partySize={partySize}
          onMapChange={(nextMapId, size) => navigate(`/loserboards/${nextMapId}?partySize=${size}`)}
          onSizeChange={(n) => setSearchParams({ partySize: String(n) })}
        />
      </Panel>

      <Panel className={styles.section}>
        <label>
          Time Window
          <select value={timeWindow} onChange={(e) => setTimeWindow(e.target.value as TimeWindow)}>
            {TIME_WINDOWS.map((w) => (
              <option key={w} value={w}>
                {TIME_WINDOW_LABELS[w]}
              </option>
            ))}
          </select>
        </label>
      </Panel>

      <div className={styles.grid}>
        <Panel className={styles.section}>
          <h2>Slowest Completions</h2>
          {worstQuery.isLoading && <p>Loading…</p>}
          {worstQuery.data && worstQuery.data.length === 0 && (
            <p className={styles.emptyState}>No completed runs recorded for this map yet.</p>
          )}
          {worstQuery.data && worstQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>Time</th>
                  <th>Date</th>
                  <th>Party</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {worstQuery.data.slice(0, worstExpanded ? 5 : 1).map((entry, index) => (
                  <RunLinkRow key={entry.run_id} runId={entry.run_id}>
                    <td className={styles.rank}>
                      {index === 0 && worstQuery.data.length > 1 && (
                        <ExpandToggle expanded={worstExpanded} onToggle={() => setWorstExpanded((v) => !v)} />
                      )}
                      {index + 1}
                    </td>
                    <td>{formatDuration(entry.duration_ms)}</td>
                    <td>{formatDate(entry.utc_start)}</td>
                    <td>
                      <div className={styles.participants}>
                        {entry.participants.map((p, i) => (
                          <span key={i} className={styles.participant}>
                            {p.alias ?? p.raw_name} <RoleBadge role={p.role} />
                          </span>
                        ))}
                      </div>
                    </td>
                  </RunLinkRow>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        {/* FoW 8-man has no role composition (role_model = NULL) — the by-role boards would be empty. */}
        {configHasRoles(mapId, partySize) && (
        <Panel className={styles.section}>
          <h2>Deaths By Role</h2>
          {roleDeathsQuery.isLoading && <p>Loading…</p>}
          {roleDeathsQuery.data &&
            rolesForConfig(mapId, partySize).map((role, index) => {
              // Already sorted deaths/run-desc by the backend; filtering preserves that relative order.
              const rows = roleDeathsQuery.data.filter((r) => r.role === role);
              return (
                <div key={role}>
                  <h3 className={index === 0 ? undefined : styles.subsection}>
                    <RoleBadge role={role} />
                  </h3>
                  {rows.length === 0 ? (
                    <p className={styles.emptyState}>No runs recorded for this role yet.</p>
                  ) : (
                    <table>
                      <thead>
                        <tr>
                          <th>User</th>
                          <th>Total runs</th>
                          <th>Deaths</th>
                          <th>Deaths/run</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((r) => (
                          <tr key={r.user}>
                            <td>{r.user}</td>
                            <td>{r.total_runs}</td>
                            <td>{r.deaths}</td>
                            <td>{r.avg_deaths.toFixed(1)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              );
            })}
        </Panel>
        )}

        {configHasRoles(mapId, partySize) && (
        <Panel className={styles.section}>
          <h2>Blamed By Role</h2>
          {failureReasonsQuery.isLoading && <p>Loading…</p>}
          {failureReasonsQuery.data &&
            rolesForConfig(mapId, partySize).map((role, index) => {
              // Already sorted fails/run-desc by the backend; filtering preserves that relative order.
              // Only users actually blamed at least once — a zero-blame row adds no signal here.
              const rows = failureReasonsQuery.data.filter((r) => r.role === role && r.fails > 0);
              return (
                <div key={role}>
                  <h3 className={index === 0 ? undefined : styles.subsection}>
                    <RoleBadge role={role} />
                  </h3>
                  {rows.length === 0 ? (
                    <p className={styles.emptyState}>No blames recorded for this role yet.</p>
                  ) : (
                    <table>
                      <thead>
                        <tr>
                          <th>User</th>
                          <th>Total runs</th>
                          <th>Times Blamed</th>
                          <th>Blamed/run</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((r) => (
                          <tr key={r.user}>
                            <td>{r.user}</td>
                            <td>{r.total_runs}</td>
                            <td>{r.fails}</td>
                            <td>{r.avg_fails.toFixed(2)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              );
            })}
        </Panel>
        )}

        <Panel className={styles.section}>
          <h2>Resign Fails By User</h2>
          {globalFailsQuery.isLoading && <p>Loading…</p>}
          {globalFailsQuery.data && globalFailsQuery.data.length === 0 && (
            <p className={styles.emptyState}>No runs recorded for this map yet.</p>
          )}
          {globalFailsQuery.data && globalFailsQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>User</th>
                  <th>Total runs</th>
                  <th>Resigns</th>
                  <th>Resign %</th>
                </tr>
              </thead>
              <tbody>
                {globalFailsQuery.data.map((r) => (
                  <tr key={r.user}>
                    <td>{r.user}</td>
                    <td>{r.total_runs}</td>
                    <td>{r.resigns}</td>
                    <td>{r.percentage.toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Longest Resign/Wipe Streak</h2>
          {badStreakQuery.isLoading && <p>Loading…</p>}
          {badStreakQuery.data && badStreakQuery.data.length === 0 && (
            <p className={styles.emptyState}>No runs recorded for this map yet.</p>
          )}
          {badStreakQuery.data && badStreakQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>User</th>
                  <th>Streak</th>
                  <th>From</th>
                  <th>To</th>
                </tr>
              </thead>
              <tbody>
                {badStreakQuery.data.map((entry, index) => (
                  <tr key={entry.user}>
                    <td className={styles.rank}>{index + 1}</td>
                    <td>{entry.user}</td>
                    <td>{entry.streak}</td>
                    <td>{formatDate(entry.streak_start)}</td>
                    <td>{formatDate(entry.streak_end)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Players On An Outdated Plugin</h2>
          {outdatedPluginsQuery.isLoading && <p>Loading…</p>}
          {outdatedPluginsQuery.data && outdatedPluginsQuery.data.length === 0 && (
            <p className={styles.emptyState}>No active players are behind on the plugin.</p>
          )}
          {outdatedPluginsQuery.data && outdatedPluginsQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>User</th>
                  <th>Plugin version</th>
                  <th>Last seen</th>
                </tr>
              </thead>
              <tbody>
                {outdatedPluginsQuery.data.map((entry) => (
                  <tr key={entry.user}>
                    <td>{entry.user}</td>
                    <td>{entry.plugin_version ?? 'unknown (very old)'}</td>
                    <td>{formatDate(entry.last_seen)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Slowest To Reach Objective</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>Party</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <SlowestSectionRow key={name} mapId={mapId} partySize={partySize} objectiveName={name} from={from} />
                ))}
              </tbody>
            </table>
          )}
        </Panel>
      </div>
    </div>
  );
}

/** The mirror of LeaderboardPage's GlobalSectionRow(metric="start") — slowest instead of fastest to
 * reach each objective. Not role-gated: unlike "who owns this objective's clear time," everyone in
 * the party experienced the slow pace, so the full participant list is shown. */
function SlowestSectionRow({ mapId, partySize, objectiveName, from }: { mapId: string; partySize: number; objectiveName: string; from: string | null }) {
  const [expanded, setExpanded] = useState(false);
  const slowestQuery = useQuery({
    queryKey: ['loserboard', 'section', 'start', mapId, partySize, objectiveName, from],
    queryFn: () =>
      api.get<SectionEntry[]>(
        `/loserboards/maps/${mapId}/sections/${encodeURIComponent(objectiveName)}/start?partySize=${partySize}${from ? `&from=${encodeURIComponent(from)}` : ''}`,
      ),
  });

  const entries = slowestQuery.data ?? [];
  const visible = entries.slice(0, expanded ? 5 : 1);

  if (visible.length === 0) {
    return (
      <tr>
        <td className={styles.rank}>—</td>
        <td>{objectiveName}</td>
        <td>—</td>
        <td>—</td>
        <td>—</td>
        <td>
          <span className={styles.emptyState}>—</span>
        </td>
        <td></td>
      </tr>
    );
  }

  return (
    <>
      {visible.map((entry, index) => (
        <RunLinkRow key={entry.run_id} runId={entry.run_id}>
          <td className={styles.rank}>
            {index === 0 && entries.length > 1 && <ExpandToggle expanded={expanded} onToggle={() => setExpanded((v) => !v)} />}
            {index + 1}
          </td>
          <td>{objectiveName}</td>
          <td>{formatDuration(entry.duration_ms)}</td>
          <td>{formatDuration(entry.start_ms)}</td>
          <td>{formatDuration(entry.done_ms)}</td>
          <td>
            {entry.participants.length > 0 ? (
              <div className={styles.participants}>
                {entry.participants.map((p, i) => (
                  <span key={i} className={styles.participant}>
                    {p.alias ?? p.raw_name} <RoleBadge role={p.role} />
                  </span>
                ))}
              </div>
            ) : (
              <span className={styles.emptyState}>—</span>
            )}
          </td>
        </RunLinkRow>
      ))}
    </>
  );
}
