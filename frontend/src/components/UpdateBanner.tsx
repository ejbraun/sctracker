import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import styles from './UpdateBanner.module.css';

/**
 * Shown on every page once a person who has downloaded the plugin before has an outdated copy —
 * see Person.new_plugin_version_available (backend: AuthController.newPluginVersionAvailable).
 * Never shown to someone who's never downloaded at all; that's not "outdated," just "hasn't started."
 */
export function UpdateBanner() {
  const { person } = useAuth();

  if (!person?.new_plugin_version_available) {
    return null;
  }

  return (
    <div className={styles.banner}>
      A new version of the SCTracker plugin is available. <Link to="/account">Download it from your Account page</Link>.
    </div>
  );
}
