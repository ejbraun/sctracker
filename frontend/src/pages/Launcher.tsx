import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AccountModulesResponse } from '../api/types';
import { Panel } from '../components/Panel';
import { PatchNotes } from '../components/PatchNotes';
import { versionSuffix } from '../common/modules';
import styles from './Downloads.module.css';

/** specs/frontend/09-downloads.md. */
export function Launcher() {
  // ?type=module so only launcher components come back; the backend also drops ui_visible=false
  // rows here, so gwrl-base / gwrl-<feature> (the launcher's own self-update payloads) never appear
  // — gwrl-install, the human-downloaded install archive, is the only visible one by design.
  const modulesQuery = useQuery({
    queryKey: ['account', 'modules', 'module'],
    queryFn: () => api.get<AccountModulesResponse>('/account/modules?type=module'),
  });
  const modules = modulesQuery.data?.modules ?? [];
  const launcher = modules.find((m) => m.key === 'gwrl-install');

  return (
    <div>
      <h1>Launcher</h1>

      {modulesQuery.isLoading && <p>Loading…</p>}

      {!modulesQuery.isLoading && !launcher && (
        <Panel className={styles.section}>
          <h2>GW Launcher Reforged</h2>
          <p>
            The launcher is granted per account. Ask an admin to enable it for you, then this page
            will show the download.
          </p>
        </Panel>
      )}

      {launcher && (
        <Panel className={styles.section}>
          <h2>GW Launcher Reforged</h2>
          <p>
            The GW Launcher Reforged (GWRL) install archive — everything needed to run it. It syncs the
            feature modules you're entitled to using a machine key (from <strong>Account</strong>). This
            download is tied to your account.
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
    </div>
  );
}
