import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { UpdateBanner } from './UpdateBanner';
import styles from './Layout.module.css';

/** Wraps every protected route — nav bar + logout, per specs/frontend/01-auth.md. */
export function Layout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { person } = useAuth();

  async function handleLogout() {
    await api.post('/logout');
    // Drop all cached server state, not just the auth query — stale leaderboard/history data
    // from the previous account shouldn't linger (specs/frontend/01-auth.md).
    queryClient.clear();
    navigate('/login');
  }

  return (
    <div className={styles.shell}>
      <nav className={styles.nav}>
        <span className={styles.brand}>gwsctracker</span>
        <Link className={styles.navLink} to="/">
          Leaderboards
        </Link>
        <Link className={styles.navLink} to="/loserboards">
          Loserboards
        </Link>
        <Link className={styles.navLink} to="/runs">
          Run History
        </Link>
        <Link className={styles.navLink} to="/characters">
          Characters
        </Link>
        <Link className={styles.navLink} to="/account">
          Account
        </Link>
        <Link className={styles.navLink} to="/how-to-use">
          How to Use
        </Link>
        {person?.is_admin && (
          <Link className={styles.navLink} to="/admin/users">
            User Management
          </Link>
        )}
        {person?.is_admin && (
          <Link className={styles.navLink} to="/admin/runs">
            Run Cleanup
          </Link>
        )}
        <span className={styles.spacer} />
        <button onClick={handleLogout}>Logout</button>
      </nav>
      <div className={styles.content}>
        <UpdateBanner />
        <Outlet />
      </div>
    </div>
  );
}
