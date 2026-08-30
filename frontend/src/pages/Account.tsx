import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { GeneratedMachineKey, MachineKey, Person, PluginVersion } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate } from '../common/format';
import styles from './Account.module.css';

/** specs/frontend/02-account.md. */
export function Account() {
  const { person } = useAuth();
  const queryClient = useQueryClient();
  const [label, setLabel] = useState('');
  const [revealed, setRevealed] = useState<GeneratedMachineKey | null>(null);
  const [alias, setAlias] = useState(person?.alias ?? '');

  const aliasMutation = useMutation({
    mutationFn: () => api.patch<Person>('/account/alias', { alias: alias.trim() || null }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['account', 'me'] }),
  });

  const keysQuery = useQuery({
    queryKey: ['machine-keys'],
    queryFn: () => api.get<MachineKey[]>('/account/machine-keys'),
  });

  const generateMutation = useMutation({
    mutationFn: () => api.post<GeneratedMachineKey>('/account/machine-keys', { label: label || undefined }),
    onSuccess: (key) => {
      setRevealed(key);
      setLabel('');
      queryClient.invalidateQueries({ queryKey: ['machine-keys'] });
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/account/machine-keys/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['machine-keys'] }),
  });

  // Fire-and-forget alongside the actual browser-native download below — records the timestamp the
  // "new plugin version available" banner needs, but never blocks/interferes with the download
  // itself (no preventDefault; the <a download> navigation happens regardless of this succeeding).
  const recordDownloadMutation = useMutation({
    mutationFn: () => api.post('/plugin/download'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['account', 'me'] }),
  });

  // Top-level, not /api-prefixed (see PluginVersionController) — fetched directly rather than
  // through the api client, same as the /SCTracker.dll link itself. Purely cosmetic (shows which
  // build the download link points at); a failure here just leaves the version number off the link.
  const pluginVersionQuery = useQuery({
    queryKey: ['plugin-version'],
    queryFn: async () => {
      const response = await fetch('/plugin-version');
      if (!response.ok) {
        throw new Error('failed to fetch plugin version');
      }
      return (await response.json()) as PluginVersion;
    },
    retry: false,
  });

  function handleRevoke(id: number) {
    if (window.confirm('Revoke this machine key? Uploads using it will stop working.')) {
      revokeMutation.mutate(id);
    }
  }

  return (
    <div>
      <h1>Account</h1>

      <Panel className={styles.section}>
        <h2>Profile</h2>
        <p>Username: {person?.username}</p>
        <p>
          Alias: {person?.alias ?? '—'}{' '}
          <span className={styles.aliasHint}>(shown to other players instead of your username; used to look you up)</span>
        </p>

        <ErrorBanner error={aliasMutation.error} />

        <form
          className={styles.aliasForm}
          onSubmit={(e) => {
            e.preventDefault();
            aliasMutation.mutate();
          }}
        >
          <label>
            Alias
            <input value={alias} onChange={(e) => setAlias(e.target.value)} placeholder="e.g. Howl" />
          </label>
          <button type="submit" disabled={aliasMutation.isPending}>
            Save alias
          </button>
        </form>
      </Panel>

      <Panel className={styles.section}>
        <h2>Plugin</h2>
        <p>
          SCTracker is a third-party GWToolbox++ plugin that uploads your runs. Put <code>SCTracker.dll</code>{' '}
          in your GWToolbox++ <code>Plugins</code> folder, enable it in GWToolbox's plugin manager, then paste a
          machine key (below) into its settings.
        </p>
        <p>
          <a className={styles.downloadLink} href="/SCTracker.dll" download onClick={() => recordDownloadMutation.mutate()}>
            Download SCTracker.dll{pluginVersionQuery.data && ` (v${pluginVersionQuery.data.version})`}
          </a>
        </p>
      </Panel>

      <Panel className={styles.section}>
        <h2>Machine keys</h2>
        <p>Used by the plugin to authenticate uploads to your account.</p>

        {revealed && (
          <div className={styles.revealBox}>
            <p className={styles.warning}>
              This key won't be shown again — store it in the GW1 plugin config now.
            </p>
            <code className={styles.rawKey}>{revealed.key}</code>
            <button onClick={() => navigator.clipboard?.writeText(revealed.key)}>Copy to clipboard</button>
            <button onClick={() => setRevealed(null)}>Dismiss</button>
          </div>
        )}

        <ErrorBanner error={generateMutation.error ?? revokeMutation.error} />

        <form
          className={styles.keyForm}
          onSubmit={(e) => {
            e.preventDefault();
            generateMutation.mutate();
          }}
        >
          <label>
            Label (optional)
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="e.g. my desktop" />
          </label>
          <button type="submit" disabled={generateMutation.isPending}>
            Generate new key
          </button>
        </form>

        {keysQuery.isLoading && <p>Loading…</p>}
        {keysQuery.data && (
          <table>
            <thead>
              <tr>
                <th>Label</th>
                <th>Created</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {keysQuery.data.map((key) => (
                <tr key={key.id} className={key.revoked_at ? styles.revoked : undefined}>
                  <td>{key.label ?? '—'}</td>
                  <td>{formatDate(key.created_at)}</td>
                  <td>{key.revoked_at ? `Revoked ${formatDate(key.revoked_at)}` : 'Active'}</td>
                  <td>
                    {!key.revoked_at && (
                      <button onClick={() => handleRevoke(key.id)} disabled={revokeMutation.isPending}>
                        Revoke
                      </button>
                    )}
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
