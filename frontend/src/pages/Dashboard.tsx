import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DEFAULT_MAP_ID, defaultPartySize } from '../common/maps';
import { MapSizePicker } from '../components/MapSizePicker';
import { Panel } from '../components/Panel';
import styles from './Dashboard.module.css';

/**
 * specs/frontend/04-leaderboards.md — landing page. Pick a map + party size, then jump to that
 * map's Leaderboards / Loserboards / Run History. See specs/features/fow-and-party-size.md.
 */
export function Dashboard() {
  const [mapId, setMapId] = useState(DEFAULT_MAP_ID);
  const [partySize, setPartySize] = useState<number>(defaultPartySize(DEFAULT_MAP_ID) ?? 8);

  return (
    <div>
      <h1>gwsctracker</h1>
      <Panel className={styles.mapPicker}>
        <MapSizePicker
          mapId={mapId}
          partySize={partySize}
          onMapChange={setMapId}
          onSizeChange={setPartySize}
        />
        <nav className={styles.links}>
          <Link to={`/leaderboards/${mapId}?partySize=${partySize}`}>View Leaderboards</Link>
          <Link to={`/loserboards/${mapId}?partySize=${partySize}`}>View Loserboards</Link>
          <Link to={`/runs?map=${mapId}&partySize=${partySize}`}>View Run History</Link>
        </nav>
      </Panel>
    </div>
  );
}
