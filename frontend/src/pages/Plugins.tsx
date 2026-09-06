import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AccountModule, AccountModulesResponse, PluginVersion } from '../api/types';
import { Panel } from '../components/Panel';
import { PatchNotes } from '../components/PatchNotes';
import { versionSuffix } from '../common/modules';
import styles from './Downloads.module.css';

// SCTracker has its own hand-written panel above the list — it's the one plugin this site needs.
// Every other `type: plugin` module the user can see gets a generic panel, with per-key copy and a
// per-key badge where the "optional plugin you drop in and enable" default doesn't fit — e.g.
// GWToolboxdll is the toolbox itself, not a plugin that runs inside it. (The launcher-internal
// toolbox build is normally hidden via ui_visible=false and never reaches this page at all; the
// override survives for the window between deploy and an admin toggling it off.)
const DROP_IN_BLURB =
  'Optional — not needed to submit runs. A GWToolbox++ plugin: put the .dll in your GWToolbox++ Plugins folder and enable it in the plugin manager.';
const PLUGIN_BLURB: Record<string, string> = {
  gwtoolbox:
    "This is the toolbox itself (GWToolboxdll.dll), not a plugin that runs inside it. It's a custom build — a fork of GWToolbox++ that stays synced with upstream but carries additional functionality to work with GW Launcher Reforged, so it will drift from upstream over time. GWRL injects this build automatically — you only need this download if you're running GWToolbox++ without it.",
};
const PLUGIN_TAG: Record<string, string> = {
  gwtoolbox: 'toolbox build',
};

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

/** specs/frontend/09-downloads.md. */
export function Plugins() {
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

  // The logged-in user's plugin entitlements — public plugins (SCTracker, DBBox, …) plus anything
  // granted. ?type=plugin so the launcher's `type: module` components never come back here; the
  // backend also drops ui_visible=false rows for this route.
  const modulesQuery = useQuery({
    queryKey: ['account', 'modules', 'plugin'],
    queryFn: () => api.get<AccountModulesResponse>('/account/modules?type=plugin'),
  });
  const modules = modulesQuery.data?.modules ?? [];
  // SCTracker keeps its dedicated hand-written panel (own /SCTracker.dll route, /plugin-version for
  // the number) — this is only consulted for its patch_notes_url.
  const sctracker = modules.find((m) => m.key === 'sctracker');
  const pluginDownloads = modules.filter((m) => m.key !== 'sctracker');

  return (
    <div>
      <h1>Plugins</h1>

      <Panel className={styles.section}>
        <h2>
          SCTracker <span className={styles.requiredTag}>required</span>
        </h2>
        <p>
          This is the only plugin you need — it's what uploads your runs to this site. SCTracker is a
          third-party GWToolbox++ plugin: put <code>SCTracker.dll</code> in your GWToolbox++ <code>Plugins</code>{' '}
          folder, enable it in GWToolbox's plugin manager, then paste a machine key (from{' '}
          <strong>Account</strong>) into its settings.
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
    </div>
  );
}
