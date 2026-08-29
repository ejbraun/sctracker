import { useState } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { UpdateBanner } from './UpdateBanner';
import { DEFAULT_MAP_ID } from '../common/maps';
import styles from './Layout.module.css';

/** Wraps every protected route — nav bar + logout, per specs/frontend/01-auth.md. */
export function Layout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { person } = useAuth();
  // Drives the hamburger on narrow screens; ignored by the desktop layout, where the links are
  // always shown (Layout.module.css).
  const [menuOpen, setMenuOpen] = useState(false);

  async function handleLogout() {
    await api.post('/logout');
    // Drop all cached server state, not just the auth query — stale leaderboard/history data
    // from the previous account shouldn't linger (specs/frontend/01-auth.md).
    queryClient.clear();
    navigate('/login');
  }

  function closeMenu() {
    setMenuOpen(false);
  }

  return (
    <div className={styles.shell}>
      <nav className={styles.nav}>
        <div className={styles.navBar}>
          <span className={styles.brand}>gwsctracker</span>
          <button
            type="button"
            className={styles.menuToggle}
            aria-expanded={menuOpen}
            aria-label="Toggle navigation menu"
            onClick={() => setMenuOpen((open) => !open)}
          >
            ☰
          </button>
        </div>
        <div className={`${styles.links} ${menuOpen ? styles.linksOpen : ''}`}>
          <Link className={styles.navLink} to="/" onClick={closeMenu}>
            Leaderboards
          </Link>
          <Link className={styles.navLink} to={`/loserboards/${DEFAULT_MAP_ID}`} onClick={closeMenu}>
            Loserboards
          </Link>
          <Link className={styles.navLink} to="/runs" onClick={closeMenu}>
            Run History
          </Link>
          <Link className={styles.navLink} to="/characters" onClick={closeMenu}>
            Characters
          </Link>
          <Link className={styles.navLink} to="/account" onClick={closeMenu}>
            Account
          </Link>
          <Link className={styles.navLink} to="/how-to-use" onClick={closeMenu}>
            How to Use
          </Link>
          {person?.is_admin && (
            <Link className={styles.navLink} to="/admin/users" onClick={closeMenu}>
              User Management
            </Link>
          )}
          {person?.is_admin && (
            <Link className={styles.navLink} to="/admin/runs" onClick={closeMenu}>
              Run Cleanup
            </Link>
          )}
          <span className={styles.spacer} />
          <button
            onClick={() => {
              closeMenu();
              handleLogout();
            }}
          >
            Logout
          </button>
        </div>
      </nav>
      <div className={styles.content}>
        <UpdateBanner />
        <Outlet />
      </div>
    </div>
  );
}
