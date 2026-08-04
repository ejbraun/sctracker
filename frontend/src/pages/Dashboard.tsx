import { useEffect } from 'react';
import type { ChangeEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { GameMap } from '../api/types';
import { DEFAULT_MAP_ID } from '../common/maps';
import { Panel } from '../components/Panel';
import styles from './Dashboard.module.css';

/**
 * specs/frontend/04-leaderboards.md — map picker, "/". A dropdown rather than a search list, since
 * maps is now a curated, well-defined set (specs/backend/01) — currently just Underworld, so
 * there's nothing to search for and nothing else the dropdown could show.
 */
export function Dashboard() {
  const navigate = useNavigate();

  const mapsQuery = useQuery({
    queryKey: ['maps'],
    queryFn: () => api.get<GameMap[]>('/maps'),
  });

  // With only one map to offer, a real click can never change the <select>'s value, so onChange
  // below would never fire and this page would be a dead end. Auto-advance instead — once more
  // maps exist, this no-ops (length > 1) and picking a genuinely different option below works
  // normally, since a real value change always fires onChange.
  useEffect(() => {
    if (mapsQuery.data?.length === 1) {
      navigate(`/leaderboards/${mapsQuery.data[0].id}`, { replace: true });
    }
  }, [mapsQuery.data, navigate]);

  function handleChange(e: ChangeEvent<HTMLSelectElement>) {
    navigate(`/leaderboards/${e.target.value}`);
  }

  return (
    <div>
      <h1>Leaderboards</h1>
      <Panel>
        <label className={styles.mapPicker}>
          Map
          <select value={DEFAULT_MAP_ID} onChange={handleChange} disabled={mapsQuery.isLoading}>
            {mapsQuery.data?.map((map) => (
              // Maps with a null name (not yet backfilled) show their raw id rather than being
              // hidden — still valid runs, just not pretty-named yet (spec 04).
              <option key={map.id} value={map.id}>
                {map.name ?? `Map #${map.id}`}
              </option>
            ))}
          </select>
        </label>
      </Panel>
    </div>
  );
}
