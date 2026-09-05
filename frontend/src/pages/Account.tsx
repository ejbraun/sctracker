import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type {
  AccountModule,
  AccountModulesResponse,
  GeneratedMachineKey,
  MachineKey,
  Person,
  PluginVersion,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate } from '../common/format';
import styles from './Account.module.css';

// SCTracker has its own hand-written panel above the list — it's the one plugin this site needs.
// Every other public `type: plugin` module the user can see gets a generic panel, with per-key
// copy and a per-key badge where the "optional plugin you drop in and enable" default doesn't fit
// — e.g. GWToolboxdll is the toolbox itself, not a plugin that runs inside it, so it gets its own
// wording and badge rather than being described (and tagged) the same way as an actual plugin.
const DROP_IN_BLURB =
  'Optional — not needed to submit runs. A GWToolbox++ plugin: put the .dll in your GWToolbox++ Plugins folder and enable it in the plugin manager.';
const PLUGIN_BLURB: Record<string, string> = {
  gwtoolbox:
    "This is the toolbox itself (GWToolboxdll.dll), not a plugin that runs inside it. It's a custom build — a fork of GWToolbox++ that stays synced with upstream but carries additional functionality to work with GW Launcher Reforged, so it will drift from upstream over time. GWRL injects this build automatically — you only need this download if you're running GWToolbox++ without it.",
};
const PLUGIN_TAG: Record<string, string> = {
  gwtoolbox: 'toolbox build',
};

/** `(vN)` only for a real, positive build number — a manifest with no `version` deserializes to 0. */
function versionSuffix(version: number | null): string {
  return version != null && version > 0 ? ` (v${version})` : '';
}

/**
 * A `<details>` disclosure that lazy-fetches a module's patch notes (plain text, not JSON — a raw
 * `fetch`, not the `api` client) the first time it's expanded, then shows them in a scrollable box.
 * `url` is already the full account-scoped path from `AccountModule.patch_notes_url`.
 */
function PatchNotes({ url }: { url: string }) {
  const [open, setOpen] = useState(false);

  const notesQuery = useQuery({
    queryKey: ['patch-notes', url],
    queryFn: async () => {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error('failed to fetch patch notes');
      }
      return response.text();
    },
    enabled: open,
  });

  return (
    <details className={styles.patchNotes} onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className={styles.patchNotesSummary}>Patch notes</summary>
      {notesQuery.isLoading && <p>Loading…</p>}
      {notesQuery.isError && <p>Couldn't load patch notes.</p>}
      {notesQuery.data && <pre className={styles.patchNotesBox}>{notesQuery.data}</pre>}
    </details>
  );
}

function PluginDownloadPanel({ module }: { module: AccountModule }) {
  return (
    <Panel className={styles.section}>
      <h2>
        {module.display_name} <span className={styles.optionalTag}>{PLUGIN_TAG[module.key] ?? 'optional'}</span>
      </h2>
      <p>{PLUGIN_BLURB[module.key] ?? DROP_IN_BLURB}</p>
      <p>
        <a className={styles.downloadLink} href={module.download_url} download>
          Download {module.display_name}
          {versionSuffix(module.version)}
        </a>
      </p>
      {module.patch_notes_url && <PatchNotes url={module.patch_notes_url} />}
    </Panel>
  );
}

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

  // The logged-in user's module entitlements — public modules (the GWToolbox / DBBox plugin dlls)
  // plus anything granted (e.g. the gated GWRL launcher). Drives which download panels render.
  const modulesQuery = useQuery({
    queryKey: ['account', 'modules'],
    queryFn: () => api.get<AccountModulesResponse>('/account/modules'),
  });
  const modules = modulesQuery.data?.modules ?? [];
  // Every plugin except SCTracker (which keeps its dedicated /SCTracker.dll panel), in the
  // backend's sort_order; the launcher is a `type: module` and gets its own panel.
  const pluginDownloads = modules.filter((m) => m.type === 'plugin' && m.key !== 'sctracker');
  const launcher = modules.find((m) => m.key === 'gwrl-install');
  // SCTracker's panel below is otherwise fully hand-written (dedicated /SCTracker.dll route,
  // /plugin-version for the version number) — this is only consulted for its patch_notes_url.
  const sctracker = modules.find((m) => m.key === 'sctracker');

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
        <h2>
          SCTracker <span className={styles.requiredTag}>required</span>
        </h2>
        <p>
          This is the only plugin you need — it's what uploads your runs to this site. SCTracker is a
          third-party GWToolbox++ plugin: put <code>SCTracker.dll</code> in your GWToolbox++ <code>Plugins</code>{' '}
          folder, enable it in GWToolbox's plugin manager, then paste a machine key (below) into its settings.
        </p>
        {pluginDownloads.length > 0 && (
          <p>
            Any other plugin downloads below are <strong>optional extras</strong> — not required to submit runs.
          </p>
        )}
        <p>
          <a className={styles.downloadLink} href="/SCTracker.dll" download>
            Download SCTracker.dll{pluginVersionQuery.data && ` (v${pluginVersionQuery.data.version})`}
          </a>
        </p>
        {sctracker?.patch_notes_url && <PatchNotes url={sctracker.patch_notes_url} />}
      </Panel>

      {pluginDownloads.map((module) => (
        <PluginDownloadPanel key={module.key} module={module} />
      ))}

      {launcher && (
        <Panel className={styles.section}>
          <h2>Launcher</h2>
          <p>
            The GW Launcher Reforged (GWRL) install archive — everything needed to run it. It syncs the
            feature modules you're entitled to using a machine key (below). This download is tied to your
            account.
          </p>
          <p>
            <a className={styles.downloadLink} href={launcher.download_url} download>
              Download launcher
              {versionSuffix(launcher.version)}
            </a>
          </p>
          {launcher.patch_notes_url && <PatchNotes url={launcher.patch_notes_url} />}
        </Panel>
      )}

      <Panel className={styles.section}>
        <h2>Machine keys</h2>
        <p>Used by the plugin to authenticate uploads to your account.</p>

        {revealed && (
          <div className={styles.revealBox}>
            <p className={styles.warning}>
              This key won't be shown again — store it in the GW1 plugin config now.
            </p>
            <code className={styles.rawKey} data-testid="raw-machine-key">{revealed.key}</code>
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
