import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import styles from './UpdateBanner.module.css';

/**
 * Shown on every page whenever this person's plugin copy is stale relative to the currently
 * detected dll build — see Person.new_plugin_version_available (backend:
 * AuthController.newPluginVersionAvailable). Also shown to someone who has never downloaded at
 * all (no timestamp recorded means nothing to compare, so it defaults to "go get it").
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
