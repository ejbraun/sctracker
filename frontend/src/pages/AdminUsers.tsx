import { Fragment, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AdminUser, CharacterSummary } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';

/**
 * Admin-only — gated by AdminRoute. Grants/revokes people.can_report_failures, and (via the
 * expandable row) views and registers characters for any user through /api/admin/users.
 */
export function AdminUsers() {
  const queryClient = useQueryClient();
  const [expandedId, setExpandedId] = useState<number | null>(null);

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
                <th>Characters</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {usersQuery.data.map((user) => (
                <Fragment key={user.id}>
                  <tr>
                    <td>{user.username}</td>
                    <td>{user.alias ?? '—'}</td>
                    <td>{user.is_admin ? 'Yes' : '—'}</td>
                    <td>{user.can_report_failures ? 'Yes' : 'No'}</td>
                    <td>
                      <button onClick={() => setExpandedId((cur) => (cur === user.id ? null : user.id))}>
                        {expandedId === user.id ? 'Hide' : 'View'}
                      </button>
                    </td>
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
                  {expandedId === user.id && (
                    <tr>
                      <td colSpan={6}>
                        <UserCharacters personId={user.id} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}

/** Registered characters for one user, plus a form to register another on their behalf. */
function UserCharacters({ personId }: { personId: number }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');

  const charactersQuery = useQuery({
    queryKey: ['admin', 'users', personId, 'characters'],
    queryFn: () => api.get<CharacterSummary[]>(`/admin/users/${personId}/characters`),
  });

  const addMutation = useMutation({
    mutationFn: (characterName: string) =>
      api.post<CharacterSummary>(`/admin/users/${personId}/characters`, { character_name: characterName }),
    onSuccess: () => {
      setName('');
      queryClient.invalidateQueries({ queryKey: ['admin', 'users', personId, 'characters'] });
    },
  });

  return (
    <div>
      {charactersQuery.isLoading && <p>Loading…</p>}
      {charactersQuery.data && charactersQuery.data.length === 0 && <p>No registered characters.</p>}
      {charactersQuery.data && charactersQuery.data.length > 0 && (
        <ul>
          {charactersQuery.data.map((character) => (
            <li key={character.id}>{character.character_name}</li>
          ))}
        </ul>
      )}

      <ErrorBanner error={addMutation.error} />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) addMutation.mutate(name.trim());
        }}
      >
        <input
          aria-label="Character name"
          placeholder="Character name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button type="submit" disabled={addMutation.isPending || !name.trim()}>
          Add character
        </button>
      </form>
    </div>
  );
}
