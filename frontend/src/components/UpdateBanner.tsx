import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import styles from './UpdateBanner.module.css';

/**
 * Shown on every page whenever the build this person's plugin last advertised over X-Plugin-Version
 * is behind the current manifest version — see Person.new_plugin_version_available (backend:
 * PluginVersionService.isOutdated). Also shown to someone whose plugin has never authenticated at
 * all (nothing has reported a version, so it defaults to "go get it").
 *
 * The stakes are spelled out here because an outdated build's uploads are rejected by the server
 * (426 Upgrade Required) — so this banner is the main place a stale user finds out their runs
 * aren't being recorded.
 */
export function UpdateBanner() {
  const { person } = useAuth();

  if (!person?.new_plugin_version_available) {
    return null;
  }

  return (
    <div className={styles.banner}>
      A new version of the SCTracker plugin is available. <Link to="/account">Download it from your Account page</Link>. Runs
      uploaded from an outdated plugin are rejected by the server and will not be recorded.
    </div>
  );
}
