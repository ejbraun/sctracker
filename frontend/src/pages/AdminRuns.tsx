import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { UnregisteredRunsCount, WipeUnregisteredRunsResult } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';

/**
 * Admin-only — gated by AdminRoute. Retroactively applies UploadRunService's "4+ registered
 * characters" upload rule to runs ingested before that rule existed. Hard delete, no undo — the
 * count is fetched up front so the confirm dialog names an exact number, not a blind "are you sure".
 */
export function AdminRuns() {
  const queryClient = useQueryClient();

  const countQuery = useQuery({
    queryKey: ['admin', 'runs', 'unregistered-count'],
    queryFn: () => api.get<UnregisteredRunsCount>('/admin/runs/unregistered-count'),
  });

  const wipeMutation = useMutation({
    mutationFn: () => api.post<WipeUnregisteredRunsResult>('/admin/runs/wipe-unregistered'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'runs', 'unregistered-count'] }),
  });

  function handleWipe() {
    const count = countQuery.data?.count ?? 0;
    if (count === 0) {
      return;
    }
    if (window.confirm(`Permanently delete ${count} run(s) with fewer than 4 registered characters? This cannot be undone.`)) {
      wipeMutation.mutate();
    }
  }

  const count = countQuery.data?.count ?? 0;

  return (
    <div>
      <h1>Run Cleanup</h1>
      <Panel>
        <p>
          Permanently deletes every run with fewer than 4 registered characters among its party — the same
          minimum <code>/upload-run</code> now enforces on new uploads, applied retroactively to runs ingested
          before that rule existed. This cannot be undone.
        </p>

        <ErrorBanner error={wipeMutation.error} />

        {countQuery.isLoading && <p>Loading…</p>}
        {countQuery.data && (
          <p>
            <strong>{count}</strong> run(s) currently match for deletion.
          </p>
        )}
        {wipeMutation.data && <p>Deleted {wipeMutation.data.deleted_count} run(s).</p>}

        <button onClick={handleWipe} disabled={wipeMutation.isPending || count === 0}>
          Wipe unregistered runs
        </button>
      </Panel>
    </div>
  );
}
