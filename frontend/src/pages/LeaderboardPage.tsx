import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type {
  GamblingStoneLeader,
  ItemDropLeader,
  LeaderboardEntry,
  PersonalBestEntry,
  PersonalSectionBest,
  RoleMvpAwardEntry,
  RunDetail,
  SectionEntry,
  UserStreak,
} from '../api/types';
import { ExpandToggle } from '../components/ExpandToggle';
import { Panel } from '../components/Panel';
import { RoleBadge } from '../components/RoleBadge';
import { RunLinkRow } from '../components/RunLinkRow';
import { formatDate, formatDuration } from '../common/format';
import { TIME_WINDOWS, TIME_WINDOW_LABELS, timeWindowFrom, type TimeWindow } from '../common/timeWindows';
import { ROLES } from '../common/roles';
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
  // Every ranked-run table on this page can collapse to just its #1 entry or expand to its top 5 via
  // ExpandToggle — data is already fetched (limit=10), just sliced client side, so toggling never
  // triggers a refetch. Both default open.
  const [overallExpanded, setOverallExpanded] = useState(true);
  const [personalOverallExpanded, setPersonalOverallExpanded] = useState(true);

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

  const roleMvpAwardsQuery = useQuery({
    queryKey: ['leaderboard', 'role-mvp-awards', mapId, timeWindow],
    queryFn: () =>
      api.get<RoleMvpAwardEntry[]>(`/leaderboards/maps/${mapId}/role-mvp-awards${from ? `?from=${encodeURIComponent(from)}` : ''}`),
    enabled: mapId != null,
  });

  const gamblersAnonymousQuery = useQuery({
    queryKey: ['leaderboard', 'gamblers-anonymous', mapId, timeWindow],
    queryFn: () =>
      api.get<GamblingStoneLeader[]>(`/leaderboards/maps/${mapId}/gamblers-anonymous${from ? `?from=${encodeURIComponent(from)}` : ''}`),
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
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {overallQuery.data.slice(0, overallExpanded ? 5 : 1).map((entry, index) => (
                  <RunLinkRow key={entry.run_id} runId={entry.run_id}>
                    <td className={styles.rank}>
                      {index === 0 && overallQuery.data.length > 1 && (
                        <ExpandToggle expanded={overallExpanded} onToggle={() => setOverallExpanded((v) => !v)} />
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
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {personalOverallTopQuery.data.slice(0, personalOverallExpanded ? 5 : 1).map((entry, index) => (
                  <RunLinkRow key={entry.run_id} runId={entry.run_id}>
                    <td className={styles.rank}>
                      {index === 0 && personalOverallTopQuery.data.length > 1 && (
                        <ExpandToggle expanded={personalOverallExpanded} onToggle={() => setPersonalOverallExpanded((v) => !v)} />
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

        <Panel className={styles.section}>
          <h2>Fastest Quest Duration</h2>
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
                  <th>User(s)</th>
                  <th></th>
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
                  <th></th>
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
                  <th>Rank</th>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                  <th></th>
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
                  <th></th>
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
                  <th>Rank</th>
                  <th>Objective</th>
                  <th>Overall</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>User(s)</th>
                  <th></th>
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
                  <th></th>
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
          <h2>MVP By Role</h2>
          {roleMvpAwardsQuery.isLoading && <p>Loading…</p>}
          {roleMvpAwardsQuery.data &&
            ROLES.map((role, index) => {
              // Already sorted awards/run-desc by the backend; filtering preserves that relative order.
              const rows = roleMvpAwardsQuery.data.filter((r) => r.role === role);
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
                          <th>MVP Awards</th>
                          <th>Awards/run</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((r) => (
                          <tr key={r.user}>
                            <td>{r.user}</td>
                            <td>{r.total_runs}</td>
                            <td>{r.awards}</td>
                            <td>{r.avg_awards.toFixed(2)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              );
            })}
        </Panel>

        <Panel className={styles.section}>
          <h2>Gamblers Anonymous</h2>
          {gamblersAnonymousQuery.isLoading && <p>Loading…</p>}
          {gamblersAnonymousQuery.data && gamblersAnonymousQuery.data.length === 0 && (
            <p className={styles.emptyState}>No gambling recorded for this map yet.</p>
          )}
          {gamblersAnonymousQuery.data && gamblersAnonymousQuery.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>User</th>
                  <th>Net Stones</th>
                  <th>Runs Gambled</th>
                </tr>
              </thead>
              <tbody>
                {gamblersAnonymousQuery.data.map((entry, index) => (
                  <tr key={entry.user}>
                    <td className={styles.rank}>{index + 1}</td>
                    <td>{entry.user}</td>
                    <td>{entry.net_stones}</td>
                    <td>{entry.runs_gambled}</td>
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
  const [expanded, setExpanded] = useState(false);
  const suffix = metric === 'duration' ? '' : `/${metric}`;
  const overallQuery = useQuery({
    queryKey: ['leaderboard', 'section', metric, mapId, objectiveName, from],
    queryFn: () =>
      api.get<SectionEntry[]>(
        `/leaderboards/maps/${mapId}/sections/${encodeURIComponent(objectiveName)}${suffix}${from ? `?from=${encodeURIComponent(from)}` : ''}`,
      ),
  });

  const entries = overallQuery.data ?? [];
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

  const cells = (
    <>
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
    </>
  );

  return personalQuery.data ? (
    <RunLinkRow runId={personalQuery.data.run_id}>{cells}</RunLinkRow>
  ) : (
    <tr>
      {cells}
      <td></td>
    </tr>
  );
}
