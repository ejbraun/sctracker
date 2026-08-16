import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { ItemDropLeader, LeaderboardEntry, PersonalBestEntry, PersonalSectionBest, RunDetail, SectionEntry, UserStreak } from '../api/types';
import { Panel } from '../components/Panel';
import { RoleBadge } from '../components/RoleBadge';
import { formatDate, formatDuration } from '../common/format';
import { TIME_WINDOWS, TIME_WINDOW_LABELS, timeWindowFrom, type TimeWindow } from '../common/timeWindows';
import styles from './LeaderboardPage.module.css';

/**
 * specs/frontend/04-leaderboards.md, plus a time-window filter applied across every panel. Global
 * and "Yours" are separate panels rather than stacked sub-sections within one panel, per section —
 * so each pair (instance completion, Quest Duration, Quest Take, Quest Finish) renders as two
 * independent panels side by side in the grid.
 */
export function LeaderboardPage() {
  const { mapId } = useParams<{ mapId: string }>();
  const [timeWindow, setTimeWindow] = useState<TimeWindow>('all');
  const from = timeWindowFrom(timeWindow);

  const overallQuery = useQuery({
    queryKey: ['leaderboard', 'overall', mapId, timeWindow],
    queryFn: () => api.get<LeaderboardEntry[]>(`/leaderboards/maps/${mapId}/overall?limit=10${from ? `&from=${encodeURIComponent(from)}` : ''}`),
    enabled: mapId != null,
  });

  const personalOverallTopQuery = useQuery({
    queryKey: ['leaderboard', 'me', 'overall', 'top', mapId, timeWindow],
    queryFn: () =>
      api.get<PersonalBestEntry[]>(`/leaderboards/me/maps/${mapId}/overall/top?limit=10${from ? `&from=${encodeURIComponent(from)}` : ''}`),
    enabled: mapId != null,
  });

  const streakQuery = useQuery({
    queryKey: ['leaderboard', 'streaks', 'completed', mapId, timeWindow],
    queryFn: () =>
      api.get<UserStreak[]>(`/leaderboards/maps/${mapId}/streaks/completed?limit=10${from ? `&from=${encodeURIComponent(from)}` : ''}`),
    enabled: mapId != null,
  });

  const luckiestPlayersQuery = useQuery({
    queryKey: ['leaderboard', 'luckiest-players', mapId, timeWindow],
    queryFn: () =>
      api.get<ItemDropLeader[]>(`/leaderboards/maps/${mapId}/luckiest-players${from ? `?from=${encodeURIComponent(from)}` : ''}`),
    enabled: mapId != null,
  });
  // The set of tracked items isn't statically known on the frontend — derive it from the response's
  // own rows (already grouped contiguously by item_id server-side) rather than a hardcoded list.
  const itemNames = luckiestPlayersQuery.data ? Array.from(new Set(luckiestPlayersQuery.data.map((d) => d.item_name))) : [];

  // The set of objective names for a map isn't statically known — pull them from the fastest
  // overall run's detail as a starting point (spec 04's documented approach).
  const firstRunId = overallQuery.data?.[0]?.run_id;
  const firstRunDetailQuery = useQuery({
    queryKey: ['run-detail-for-sections', firstRunId],
    queryFn: () => api.get<RunDetail>(`/runs/${firstRunId}`),
    enabled: firstRunId != null,
  });
  const objectiveNames = firstRunDetailQuery.data?.objectives.map((o) => o.name) ?? [];

  if (!mapId) {
    return null;
  }

  return (
    <div>
      <h1>Leaderboards</h1>

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
          <h2>Fastest To Complete Instance</h2>
          {overallQuery.isLoading && <p>Loading…</p>}
          {overallQuery.data && overallQuery.data.length === 0 && (
            <p className={styles.emptyState}>No completed runs recorded for this map yet.</p>
          )}
          {overallQuery.data && overallQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>Time</th>
                  <th>Date</th>
                  <th>Party</th>
                </tr>
              </thead>
              <tbody>
                {overallQuery.data.map((entry, index) => (
                  <tr key={entry.run_id}>
                    <td className={styles.rank}>{index + 1}</td>
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
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Your Fastest Completions</h2>
          {personalOverallTopQuery.isLoading && <p>Loading…</p>}
          {personalOverallTopQuery.data && personalOverallTopQuery.data.length === 0 && (
            <p className={styles.emptyState}>No completed run yet.</p>
          )}
          {personalOverallTopQuery.data && personalOverallTopQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>Time</th>
                  <th>Date</th>
                  <th>Party</th>
                </tr>
              </thead>
              <tbody>
                {personalOverallTopQuery.data.map((entry, index) => (
                  <tr key={entry.run_id}>
                    <td className={styles.rank}>{index + 1}</td>
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
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Fastest Quest Duration</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <GlobalSectionRow key={name} mapId={mapId} objectiveName={name} from={from} />
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Your Fastest Quest Duration</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <YourSectionRow key={name} mapId={mapId} objectiveName={name} from={from} />
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Fastest Quest Take</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <GlobalSectionRow key={name} mapId={mapId} objectiveName={name} from={from} metric="start" />
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Your Fastest Quest Take</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <YourSectionRow key={name} mapId={mapId} objectiveName={name} from={from} metric="start" />
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Fastest Quest Finish</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <GlobalSectionRow key={name} mapId={mapId} objectiveName={name} from={from} metric="finish" />
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Your Fastest Quest Finish</h2>
          {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
          {objectiveNames.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                </tr>
              </thead>
              <tbody>
                {objectiveNames.map((name) => (
                  <YourSectionRow key={name} mapId={mapId} objectiveName={name} from={from} metric="finish" />
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel className={styles.section}>
          <h2>Longest Completed Streak</h2>
          {streakQuery.isLoading && <p>Loading…</p>}
          {streakQuery.data && streakQuery.data.length === 0 && (
            <p className={styles.emptyState}>No completed runs recorded for this map yet.</p>
          )}
          {streakQuery.data && streakQuery.data.length > 0 && (
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
                {streakQuery.data.map((entry, index) => (
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
          <h2>Luckiest Players</h2>
          {luckiestPlayersQuery.isLoading && <p>Loading…</p>}
          {luckiestPlayersQuery.data && luckiestPlayersQuery.data.length === 0 && (
            <p className={styles.emptyState}>No tracked item drops recorded for this map yet.</p>
          )}
          {itemNames.map((itemName, index) => {
            // Already grouped contiguously and sorted avg-per-run-desc within each item by the backend.
            const rows = (luckiestPlayersQuery.data ?? []).filter((d) => d.item_name === itemName);
            return (
              <div key={itemName}>
                <h3 className={index === 0 ? undefined : styles.subsection}>{itemName}</h3>
                {itemName === 'Glob of Ectoplasm' && (
                  <p className={styles.emptyState}>Note: does not count ectoplasm from the chest.</p>
                )}
                <table>
                  <thead>
                    <tr>
                      <th>User</th>
                      <th>Count</th>
                      <th>Runs</th>
                      <th>Avg/Run</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.user}>
                        <td>{r.user}</td>
                        <td>{r.total_count}</td>
                        <td>{r.run_count}</td>
                        <td>{r.avg_per_run.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          })}
        </Panel>
      </div>
    </div>
  );
}

/** metric="duration" (default) ranks by fastest completion (Quest Duration), per specs/backend/02;
 * metric="start" ranks the same objective by fastest time-since-run-start to *reach* it instead
 * (Quest Take) — party pacing, not clear speed; metric="finish" ranks by elapsed time-since-run-
 * start at *completion* (Quest Finish) — role-gated like duration, just a different reference
 * point. Same response shape either way (SectionEntryResponse), just a different ORDER BY server-side. */
function GlobalSectionRow({
  mapId,
  objectiveName,
  from,
  metric = 'duration',
}: {
  mapId: string;
  objectiveName: string;
  from: string | null;
  metric?: 'duration' | 'start' | 'finish';
}) {
  const suffix = metric === 'duration' ? '' : `/${metric}`;
  const overallQuery = useQuery({
    queryKey: ['leaderboard', 'section', metric, mapId, objectiveName, from],
    queryFn: () =>
      api.get<SectionEntry[]>(
        `/leaderboards/maps/${mapId}/sections/${encodeURIComponent(objectiveName)}${suffix}${from ? `?from=${encodeURIComponent(from)}` : ''}`,
      ),
  });

  const fastest = overallQuery.data?.[0];

  return (
    <tr>
      <td>{objectiveName}</td>
      <td>{fastest ? formatDuration(fastest.duration_ms) : '—'}</td>
      <td>{fastest ? formatDuration(fastest.start_ms) : '—'}</td>
      <td>{fastest ? formatDuration(fastest.done_ms) : '—'}</td>
      <td>
        {fastest && fastest.participants.length > 0 ? (
          <div className={styles.participants}>
            {fastest.participants.map((p, i) => (
              <span key={i} className={styles.participant}>
                {p.alias ?? p.raw_name} <RoleBadge role={p.role} />
              </span>
            ))}
          </div>
        ) : (
          <span className={styles.emptyState}>—</span>
        )}
      </td>
    </tr>
  );
}

function YourSectionRow({
  mapId,
  objectiveName,
  from,
  metric = 'duration',
}: {
  mapId: string;
  objectiveName: string;
  from: string | null;
  metric?: 'duration' | 'start' | 'finish';
}) {
  const suffix = metric === 'duration' ? '' : `/${metric}`;
  const personalQuery = useQuery({
    queryKey: ['leaderboard', 'me', 'section', metric, mapId, objectiveName, from],
    queryFn: () =>
      api.get<PersonalSectionBest | undefined>(
        `/leaderboards/me/maps/${mapId}/sections/${encodeURIComponent(objectiveName)}${suffix}${from ? `?from=${encodeURIComponent(from)}` : ''}`,
      ),
  });

  return (
    <tr>
      <td>{objectiveName}</td>
      <td>
        {personalQuery.data ? (
          formatDuration(personalQuery.data.duration_ms)
        ) : (
          // Could mean "no PB yet" OR "role_objectives mapping not seeded for this objective" —
          // the API can't currently distinguish the two (specs/backend/05, specs/frontend/04).
          <span className={styles.emptyState}>—</span>
        )}
      </td>
      <td>{personalQuery.data ? formatDuration(personalQuery.data.start_ms) : <span className={styles.emptyState}>—</span>}</td>
      <td>{personalQuery.data ? formatDuration(personalQuery.data.done_ms) : <span className={styles.emptyState}>—</span>}</td>
      <td>
        {personalQuery.data && personalQuery.data.participants.length > 0 ? (
          <div className={styles.participants}>
            {personalQuery.data.participants.map((p, i) => (
              <span key={i} className={styles.participant}>
                {p.alias ?? p.raw_name} <RoleBadge role={p.role} />
              </span>
            ))}
          </div>
        ) : (
          <span className={styles.emptyState}>—</span>
        )}
      </td>
    </tr>
  );
}
