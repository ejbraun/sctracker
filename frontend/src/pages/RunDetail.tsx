import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import type { RunDetail as RunDetailDto } from '../api/types';
import { Panel } from '../components/Panel';
import { StatusBadge } from '../components/StatusBadge';
import { RoleBadge } from '../components/RoleBadge';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate, formatDuration } from '../common/format';
import styles from './RunDetail.module.css';

/** specs/frontend/05-run-history.md — "/runs/:id". */
export function RunDetail() {
  const { id } = useParams<{ id: string }>();

  const runQuery = useQuery({
    queryKey: ['run', id],
    queryFn: () => api.get<RunDetailDto>(`/runs/${id}`),
    enabled: id != null,
    retry: (failureCount, error) => !(error instanceof ApiError && error.status === 404) && failureCount < 3,
  });

  if (runQuery.isLoading) {
    return <p>Loading…</p>;
  }

  if (runQuery.error instanceof ApiError && runQuery.error.status === 404) {
    return <p>Run not found.</p>;
  }

  if (!runQuery.data) {
    return <ErrorBanner error={runQuery.error} />;
  }

  const run = runQuery.data;

  return (
    <div>
      <h1>{run.map_name ?? `Map #${run.map_id}`}</h1>
      <Panel className={styles.header}>
        <span>{formatDate(run.utc_start)}</span>
        <StatusBadge completed={run.completed} endReason={run.end_reason} />
        <span>{formatDuration(run.duration_ms)}</span>
      </Panel>

      <Panel className={styles.section}>
        <h2>Objectives</h2>
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Name</th>
              <th>Status</th>
              <th>Start</th>
              <th>Done</th>
              <th>Duration</th>
            </tr>
          </thead>
          <tbody>
            {run.objectives.map((objective) => (
              <tr key={objective.sequence}>
                <td>{objective.sequence + 1}</td>
                <td>{objective.name}</td>
                <td>{['Not reached', 'In progress', 'Done'][objective.status] ?? objective.status}</td>
                <td>{formatDuration(objective.start_ms)}</td>
                <td>{formatDuration(objective.done_ms)}</td>
                <td>{formatDuration(objective.duration_ms)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>

      {run.failure_reasons.length > 0 && (
        <Panel className={styles.section}>
          <h2>Failure Reasons</h2>
          <ul>
            {run.failure_reasons.map((reason, index) => (
              <li key={index}>
                {reason.nobody ? (
                  'Nobody'
                ) : (
                  <>
                    {reason.display_name} <RoleBadge role={reason.role} />
                  </>
                )}
              </li>
            ))}
          </ul>
        </Panel>
      )}

      <Panel className={styles.section}>
        <h2>Participants</h2>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Profession</th>
              <th>Role</th>
              <th>Deaths</th>
            </tr>
          </thead>
          <tbody>
            {run.participants.map((participant) => (
              <tr key={participant.party_index}>
                <td
                  className={participant.character_id == null ? styles.unlinked : undefined}
                  title={participant.character_id == null ? 'This character has not been claimed by any user' : undefined}
                >
                  {participant.character_name ?? participant.raw_name}
                </td>
                <td>{participant.primary_profession}</td>
                <td>
                  <RoleBadge role={participant.role} />
                </td>
                <td>{participant.deaths}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  );
}
