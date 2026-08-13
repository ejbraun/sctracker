import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AdminUser } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';

/** Admin-only — gated by AdminRoute. Grants/revokes people.can_report_failures via /api/admin/users. */
export function AdminUsers() {
  const queryClient = useQueryClient();

  const usersQuery = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => api.get<AdminUser[]>('/admin/users'),
  });

  const setCanReportFailuresMutation = useMutation({
    mutationFn: ({ id, canReportFailures }: { id: number; canReportFailures: boolean }) =>
      api.patch<AdminUser>(`/admin/users/${id}/can-report-failures`, { can_report_failures: canReportFailures }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });

  return (
    <div>
      <h1>User Management</h1>
      <Panel>
        <p>Grant or revoke permission to report run failures via the plugin. Admin status itself isn't managed here.</p>

        <ErrorBanner error={setCanReportFailuresMutation.error} />

        {usersQuery.isLoading && <p>Loading…</p>}
        {usersQuery.data && (
          <table>
            <thead>
              <tr>
                <th>Username</th>
                <th>Alias</th>
                <th>Admin</th>
                <th>Can Report Failures</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {usersQuery.data.map((user) => (
                <tr key={user.id}>
                  <td>{user.username}</td>
                  <td>{user.alias ?? '—'}</td>
                  <td>{user.is_admin ? 'Yes' : '—'}</td>
                  <td>{user.can_report_failures ? 'Yes' : 'No'}</td>
                  <td>
                    <button
                      onClick={() =>
                        setCanReportFailuresMutation.mutate({ id: user.id, canReportFailures: !user.can_report_failures })
                      }
                      disabled={setCanReportFailuresMutation.isPending}
                    >
                      {user.can_report_failures ? 'Revoke' : 'Grant'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
