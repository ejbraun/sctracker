import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { LeaderboardEntry, RoleUserDeaths, RoleUserFail, UserResign } from '../api/types';
import { Panel } from '../components/Panel';
import { RoleBadge } from '../components/RoleBadge';
import { formatDate, formatDuration } from '../common/format';
import { TIME_WINDOWS, TIME_WINDOW_LABELS, timeWindowFrom, type TimeWindow } from '../common/timeWindows';
import { ROLES } from '../common/roles';
import { DEFAULT_MAP_ID } from '../common/maps';
import styles from './LoserboardsPage.module.css';

/**
 * The mirror image of LeaderboardPage — mirrors its time-window filter, but map-agnostic (no
 * :mapId route param) since there's only one supported map, same convention RunHistory already
 * uses for its default map filter.
 */
export function LoserboardsPage() {
  const [timeWindow, setTimeWindow] = useState<TimeWindow>('all');
  const from = timeWindowFrom(timeWindow);

  const worstQuery = useQuery({
    queryKey: ['loserboard', 'worst', timeWindow],
    queryFn: () =>
      api.get<LeaderboardEntry[]>(`/loserboards/maps/${DEFAULT_MAP_ID}/worst?limit=10${from ? `&from=${encodeURIComponent(from)}` : ''}`),
  });

  const roleDeathsQuery = useQuery({
    queryKey: ['loserboard', 'role-deaths', timeWindow],
    queryFn: () =>
      api.get<RoleUserDeaths[]>(`/loserboards/maps/${DEFAULT_MAP_ID}/role-deaths${from ? `?from=${encodeURIComponent(from)}` : ''}`),
  });

  const roleFailsQuery = useQuery({
    queryKey: ['loserboard', 'role-fails', timeWindow],
    queryFn: () =>
      api.get<RoleUserFail[]>(`/loserboards/maps/${DEFAULT_MAP_ID}/role-fails${from ? `?from=${encodeURIComponent(from)}` : ''}`),
  });

  const globalFailsQuery = useQuery({
    queryKey: ['loserboard', 'global-fails', timeWindow],
    queryFn: () =>
      api.get<UserResign[]>(`/loserboards/maps/${DEFAULT_MAP_ID}/global-fails${from ? `?from=${encodeURIComponent(from)}` : ''}`),
  });

  return (
    <div>
      <h1>Loserboards</h1>

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
        <h2>Worst Completions</h2>
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
              </tr>
            </thead>
            <tbody>
              {worstQuery.data.map((entry, index) => (
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
        <h2>Deaths By Role</h2>
        {roleDeathsQuery.isLoading && <p>Loading…</p>}
        {roleDeathsQuery.data &&
          ROLES.map((role, index) => {
            // Already sorted deaths-desc by the backend; filtering preserves that relative order.
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

      <Panel className={styles.section}>
        <h2>Quest Fails By Role</h2>
        {roleFailsQuery.isLoading && <p>Loading…</p>}
        {roleFailsQuery.data &&
          ROLES.map((role, index) => {
            // Already sorted fails-desc by the backend; filtering preserves that relative order.
            const rows = roleFailsQuery.data.filter((r) => r.role === role);
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
                        <th>Fails</th>
                        <th>Fail %</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((r) => (
                        <tr key={r.user}>
                          <td>{r.user}</td>
                          <td>{r.total_runs}</td>
                          <td>{r.fails}</td>
                          <td>{r.percentage.toFixed(1)}%</td>
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
    </div>
  );
}
