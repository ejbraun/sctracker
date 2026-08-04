import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { LeaderboardEntry, PersonalBestEntry, PersonalSectionBest, RunDetail, SectionEntry } from '../api/types';
import { Panel } from '../components/Panel';
import { RoleBadge } from '../components/RoleBadge';
import { formatDate, formatDuration } from '../common/format';
import { TIME_WINDOWS, TIME_WINDOW_LABELS, timeWindowFrom, type TimeWindow } from '../common/timeWindows';
import styles from './LeaderboardPage.module.css';

/** specs/frontend/04-leaderboards.md, plus a time-window filter applied across all three panels. */
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
      <h1>Leaderboard</h1>

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

      <Panel className={styles.section}>
        <h2>Overall</h2>

        <h3>Global</h3>
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

        <h3 className={styles.subsection}>Yours</h3>
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
        <h2>Sections</h2>
        {objectiveNames.length === 0 && <p className={styles.emptyState}>No section data available yet.</p>}
        {objectiveNames.length > 0 && (
          <>
            <h3>Global</h3>
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

            <h3 className={styles.subsection}>Yours</h3>
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
          </>
        )}
      </Panel>
    </div>
  );
}

function GlobalSectionRow({ mapId, objectiveName, from }: { mapId: string; objectiveName: string; from: string | null }) {
  const overallQuery = useQuery({
    queryKey: ['leaderboard', 'section', mapId, objectiveName, from],
    queryFn: () =>
      api.get<SectionEntry[]>(
        `/leaderboards/maps/${mapId}/sections/${encodeURIComponent(objectiveName)}${from ? `?from=${encodeURIComponent(from)}` : ''}`,
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

function YourSectionRow({ mapId, objectiveName, from }: { mapId: string; objectiveName: string; from: string | null }) {
  const personalQuery = useQuery({
    queryKey: ['leaderboard', 'me', 'section', mapId, objectiveName, from],
    queryFn: () =>
      api.get<PersonalSectionBest | undefined>(
        `/leaderboards/me/maps/${mapId}/sections/${encodeURIComponent(objectiveName)}${from ? `?from=${encodeURIComponent(from)}` : ''}`,
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
