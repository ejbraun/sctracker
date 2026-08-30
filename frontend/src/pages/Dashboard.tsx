import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DEFAULT_MAP_ID, defaultPartySize } from '../common/maps';
import { MapSizePicker } from '../components/MapSizePicker';
import { Panel } from '../components/Panel';
import styles from './Dashboard.module.css';

/**
 * specs/frontend/04-leaderboards.md — landing page ("/"). A short guide, styled like How to Use's
 * "First-time setup" block, that walks the user through picking a map + party size and opening a
 * board. See specs/features/fow-and-party-size.md.
 */
export function Dashboard() {
  const [mapId, setMapId] = useState(DEFAULT_MAP_ID);
  const [partySize, setPartySize] = useState<number>(defaultPartySize(DEFAULT_MAP_ID) ?? 8);

  return (
    <div>
      <Panel className={styles.section}>
        <h2>Choose what to look at</h2>
        <p className={styles.intro}>Pick a map and party size, then open a board.</p>

        <ol className={styles.steps}>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Choose a map</p>
              <p className={styles.stepBody}>The Underworld or The Fissure of Woe.</p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Choose a party size</p>
              <p className={styles.stepBody}>
                8-Man for the Underworld; Duo or 8-Man for the Fissure of Woe.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Open a board</p>
              <p className={styles.stepBody}>Leaderboards, Loserboards, or Run History for that map and size.</p>
            </div>
          </li>
        </ol>

        <div className={styles.picker}>
          <MapSizePicker
            mapId={mapId}
            partySize={partySize}
            onMapChange={(m, size) => {
              setMapId(m);
              setPartySize(size);
            }}
            onSizeChange={setPartySize}
          />
        </div>

        <nav className={styles.links}>
          <Link to={`/leaderboards/${mapId}?partySize=${partySize}`}>View Leaderboards</Link>
          <Link to={`/loserboards/${mapId}?partySize=${partySize}`}>View Loserboards</Link>
          <Link to={`/runs?map=${mapId}&partySize=${partySize}`}>View Run History</Link>
        </nav>
      </Panel>
    </div>
  );
}
