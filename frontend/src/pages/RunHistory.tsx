import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CharacterSummary, GameMap, PageResponse, PersonSummary, RunSummary } from '../api/types';
import { ROLES } from '../common/roles';
import { DEFAULT_MAP_ID, MAPS, mapById, sizeLabel } from '../common/maps';
import { TIME_WINDOWS, TIME_WINDOW_LABELS, timeWindowFrom, type TimeWindow } from '../common/timeWindows';
import { formatDate, formatDuration } from '../common/format';
import { Panel } from '../components/Panel';
import { StatusBadge } from '../components/StatusBadge';
import { RunTimelineChart } from '../components/RunTimelineChart';
import styles from './RunHistory.module.css';

const TABLE_PAGE_SIZE = 25;
// A separate, larger fetch just for the chart — the paginated table's page size (25) would make
// for a useless timeline. Not in the spec verbatim; a pragmatic bound so the chart has enough
// points to be meaningful without fetching the entire history unbounded.
const CHART_PAGE_SIZE = 500;

// "result" is one of these three, or '' (Any) — not a 1:1 mirror of the backend's completed/
// end_reason fields, since "completed" alone doesn't distinguish *how* an unfinished run ended.
// Matches the real end_reason enum (specs/backend/01: "wipe" / "resign" / "unknown") for the two
// non-completed cases; "completed" maps to the completed=true flag instead, since a completed run's
// end_reason isn't a meaningful distinct outcome the way wipe/resign are.
type Result = '' | 'completed' | 'resign' | 'wipe';

interface Filters {
  map: string;
  partySize: string;
  role: string;
  person: string;
  character: string;
  window: TimeWindow;
  result: Result;
}

const EMPTY_FILTERS: Filters = { map: DEFAULT_MAP_ID, partySize: '', role: '', person: '', character: '', window: 'all', result: '' };

function buildQuery(filters: Filters, page: number, size: number): string {
  const params = new URLSearchParams();
  if (filters.map) params.set('map', filters.map);
  if (filters.partySize) params.set('partySize', filters.partySize);
  if (filters.role) params.set('role', filters.role);
  if (filters.person) params.set('person', filters.person);
  if (filters.character) params.set('character', filters.character);
  const from = timeWindowFrom(filters.window);
  if (from) params.set('from', from);
  if (filters.result === 'completed') params.set('completed', 'true');
  if (filters.result === 'resign' || filters.result === 'wipe') params.set('end_reason', filters.result);
  params.set('page', String(page));
  params.set('size', String(size));
  return params.toString();
}

/** specs/frontend/05-run-history.md, plus a duration-over-time chart above the table. */
export function RunHistory() {
  // Seed the map / party-size filters from the query string when present (e.g. the Dashboard links
  // to /runs?map=34&partySize=2). Read once on mount; the controls own the state after that.
  const [searchParams] = useSearchParams();
  const [filters, setFilters] = useState<Filters>(() => ({
    ...EMPTY_FILTERS,
    map: searchParams.get('map') ?? EMPTY_FILTERS.map,
    partySize: searchParams.get('partySize') ?? EMPTY_FILTERS.partySize,
  }));
  const [page, setPage] = useState(0);

  // Party-size options for the selected map — its own configured sizes, or every known size when
  // "Any" map is selected.
  const sizeOptions = useMemo(() => {
    if (filters.map) return mapById(filters.map)?.partySizes ?? [];
    return Array.from(new Set(MAPS.flatMap((m) => m.partySizes))).sort((a, b) => a - b);
  }, [filters.map]);

  const mapsQuery = useQuery({ queryKey: ['maps'], queryFn: () => api.get<GameMap[]>('/maps') });
  const peopleQuery = useQuery({ queryKey: ['people'], queryFn: () => api.get<PersonSummary[]>('/people') });
  const allCharactersQuery = useQuery({
    queryKey: ['characters-all'],
    queryFn: () => api.get<CharacterSummary[]>('/characters/all'),
  });

  const chartQuery = useQuery({
    queryKey: ['runs-chart', filters],
    queryFn: () => api.get<PageResponse<RunSummary>>(`/runs?${buildQuery(filters, 0, CHART_PAGE_SIZE)}`),
  });

  const tableQuery = useQuery({
    queryKey: ['runs-table', filters, page],
    queryFn: () => api.get<PageResponse<RunSummary>>(`/runs?${buildQuery(filters, page, TABLE_PAGE_SIZE)}`),
  });

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((f) => ({ ...f, [key]: value }));
    setPage(0);
  }

  // Changing the map can invalidate the current party-size selection (e.g. "Duo" while switching to
  // Underworld) — drop it back to "Any" when it's not an option for the newly-chosen map.
  function updateMapFilter(nextMapId: string) {
    setFilters((f) => {
      const allowed = nextMapId ? (mapById(nextMapId)?.partySizes ?? []) : MAPS.flatMap((m) => m.partySizes);
      const partySize = f.partySize && allowed.includes(Number(f.partySize)) ? f.partySize : '';
      return { ...f, map: nextMapId, partySize };
    });
    setPage(0);
  }

  // Person and character cross-filter each other: picking a person narrows the character dropdown
  // down to characters that are actually theirs (clearing the selection if it no longer applies);
  // picking a character fills in its owner's person automatically.
  function updatePersonFilter(personId: string) {
    setFilters((f) => {
      const selectedCharacter = allCharactersQuery.data?.find((c) => String(c.id) === f.character);
      const characterStillValid = !personId || !selectedCharacter || String(selectedCharacter.person_id) === personId;
      return { ...f, person: personId, character: characterStillValid ? f.character : '' };
    });
    setPage(0);
  }

  function updateCharacterFilter(characterId: string) {
    setFilters((f) => {
      const selectedCharacter = allCharactersQuery.data?.find((c) => String(c.id) === characterId);
      return { ...f, character: characterId, person: selectedCharacter ? String(selectedCharacter.person_id) : f.person };
    });
    setPage(0);
  }

  const charactersForPersonFilter =
    allCharactersQuery.data?.filter((c) => !filters.person || String(c.person_id) === filters.person) ?? [];

  return (
    <div>
      <h1>Run History</h1>

      <Panel className={styles.chartSection}>
        <h2>Runs by Elapsed Time - Faceted by Run Result</h2>
        {chartQuery.isLoading && <p>Loading…</p>}
        {chartQuery.data && <RunTimelineChart runs={chartQuery.data.items} />}
      </Panel>

      <Panel>
        <div className={styles.filterBar}>
          <label>
            Map
            <select value={filters.map} onChange={(e) => updateMapFilter(e.target.value)}>
              <option value="">Any</option>
              {mapsQuery.data?.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name ?? `Map #${m.id}`}
                </option>
              ))}
            </select>
          </label>
          <label>
            Party Size
            <select value={filters.partySize} onChange={(e) => updateFilter('partySize', e.target.value)}>
              <option value="">Any</option>
              {sizeOptions.map((n) => (
                <option key={n} value={n}>
                  {sizeLabel(n)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Person
            <select value={filters.person} onChange={(e) => updatePersonFilter(e.target.value)}>
              <option value="">Any</option>
              {peopleQuery.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.alias}
                </option>
              ))}
            </select>
          </label>
          <label>
            Role
            <select value={filters.role} onChange={(e) => updateFilter('role', e.target.value)}>
              <option value="">Any</option>
              {ROLES.map((r) => (
                <option key={r} value={r} style={{ textTransform: 'capitalize' }}>
                  {r}
                </option>
              ))}
            </select>
          </label>
          <label>
            Character
            <select value={filters.character} onChange={(e) => updateCharacterFilter(e.target.value)}>
              <option value="">Any</option>
              {charactersForPersonFilter.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.character_name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Time Window
            <select value={filters.window} onChange={(e) => updateFilter('window', e.target.value as TimeWindow)}>
              {TIME_WINDOWS.map((w) => (
                <option key={w} value={w}>
                  {TIME_WINDOW_LABELS[w]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Result
            <select value={filters.result} onChange={(e) => updateFilter('result', e.target.value as Result)}>
              <option value="">Any</option>
              <option value="completed">Completed</option>
              <option value="resign">Resigned</option>
              <option value="wipe">Wiped</option>
            </select>
          </label>
        </div>

        {tableQuery.isLoading && <p>Loading…</p>}
        {tableQuery.data && (
          <>
            <table>
              <thead>
                <tr>
                  <th>Map</th>
                  <th>Size</th>
                  <th>Date</th>
                  <th>Status</th>
                  <th>Duration</th>
                </tr>
              </thead>
              <tbody>
                {tableQuery.data.items.map((run) => (
                  <tr key={run.run_id}>
                    <td>
                      <Link className={styles.runLink} to={`/runs/${run.run_id}`}>
                        {run.map_name ?? `Map #${run.map_id}`}
                      </Link>
                    </td>
                    <td>{sizeLabel(run.party_size)}</td>
                    <td>{formatDate(run.utc_start)}</td>
                    <td>
                      <StatusBadge completed={run.completed} endReason={run.end_reason} />
                    </td>
                    <td>{formatDuration(run.duration_ms)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className={styles.pagination}>
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <span>
                Page {tableQuery.data.page + 1} of {Math.max(1, tableQuery.data.total_pages)}
              </span>
              <button disabled={page + 1 >= tableQuery.data.total_pages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          </>
        )}
      </Panel>
    </div>
  );
}
