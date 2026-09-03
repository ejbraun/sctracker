import { Fragment, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AdminUser, AdminUserModule, CharacterSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';

/**
 * Admin-only — gated by AdminRoute. Grants/revokes people.can_report_failures, and (via the
 * expandable row) views and registers characters for any user through /api/admin/users.
 */
export function AdminUsers() {
  const queryClient = useQueryClient();
  const { person } = useAuth();
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

  const setAdminMutation = useMutation({
    mutationFn: ({ id, isAdmin }: { id: number; isAdmin: boolean }) =>
      api.patch<AdminUser>(`/admin/users/${id}/admin`, { is_admin: isAdmin }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });

  return (
    <div>
      <h1>User Management</h1>
      <Panel>
        <p>Grant or revoke admin access and permission to report run failures via the plugin. You can't remove your own admin access.</p>

        <ErrorBanner error={setCanReportFailuresMutation.error} />
        <ErrorBanner error={setAdminMutation.error} />

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
                    <td>
                      {user.is_admin ? 'Yes' : '—'}{' '}
                      <button
                        onClick={() => setAdminMutation.mutate({ id: user.id, isAdmin: !user.is_admin })}
                        disabled={setAdminMutation.isPending || user.id === person?.id}
                        title={user.id === person?.id ? "You can't change your own admin access" : undefined}
                      >
                        {user.is_admin ? 'Revoke' : 'Grant'}
                      </button>
                    </td>
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
                        <UserModules personId={user.id} />
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

/** Per-user module grants — toggle access to each gated module; public ones are always available. */
function UserModules({ personId }: { personId: number }) {
  const queryClient = useQueryClient();

  const modulesQuery = useQuery({
    queryKey: ['admin', 'users', personId, 'modules'],
    queryFn: () => api.get<AdminUserModule[]>(`/admin/users/${personId}/modules`),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ moduleKey, granted }: { moduleKey: string; granted: boolean }) =>
      granted
        ? api.delete<void>(`/admin/users/${personId}/modules/${moduleKey}`)
        : api.put<void>(`/admin/users/${personId}/modules/${moduleKey}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users', personId, 'modules'] }),
  });

  return (
    <div>
      <h3>Modules</h3>
      <ErrorBanner error={toggleMutation.error} />
      {modulesQuery.isLoading && <p>Loading…</p>}
      {modulesQuery.data && modulesQuery.data.length === 0 && <p>No modules registered.</p>}
      {modulesQuery.data && modulesQuery.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Module</th>
              <th>Access</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {modulesQuery.data.map((module) => (
              <tr key={module.module_key}>
                <td>
                  {module.display_name} <code>{module.module_key}</code>
                </td>
                <td>{module.is_public ? 'Public' : module.granted ? 'Granted' : 'No access'}</td>
                <td>
                  {module.is_public ? (
                    <span>always available</span>
                  ) : (
                    <button
                      onClick={() =>
                        toggleMutation.mutate({ moduleKey: module.module_key, granted: module.granted })
                      }
                      disabled={toggleMutation.isPending}
                    >
                      {module.granted ? 'Revoke' : 'Grant'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
