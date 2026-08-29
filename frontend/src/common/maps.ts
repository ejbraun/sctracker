// Supported maps and their party-size configurations. Static reference data (specs/frontend/00 —
// "Static reference data"), mirroring the backend's map_configs table. /api/maps is still the
// source for names/ids at runtime, but the display order, short labels, and the set of party sizes
// each map supports live here. See specs/features/fow-and-party-size.md.

export interface MapChoice {
  /** GW map_id, as a string (route-param form). */
  id: string;
  /** Short label for compact UI — "UW", "FoW". */
  short: string;
  name: string;
  /** Party sizes this map supports, ascending. [0] is the default. Mirrors map_configs rows. */
  partySizes: number[];
}

export const MAPS: MapChoice[] = [
  { id: '72', short: 'UW', name: 'The Underworld', partySizes: [8] },
  { id: '34', short: 'FoW', name: 'The Fissure of Woe', partySizes: [2, 8] },
];

// Role model per (map, party_size), mirroring the backend's map_configs.role_model. `null` = the
// config has no role model (The Fissure of Woe 8-man has no fixed composition), which hides the
// by-role panels and makes personal section bests un-gated. Kept static for first paint; the
// authoritative source is GameMap.configs from /api/maps.
const ROLE_MODEL: Record<string, string | null> = {
  '72:8': 'trapper',
  '34:2': 'primary_profession',
  '34:8': null,
};

export const roleModelFor = (mapId: string, partySize: number): string | null | undefined =>
  ROLE_MODEL[`${mapId}:${partySize}`];

/** Whether this (map, party size) config is role-gated — i.e. has a role model. */
export const configHasRoles = (mapId: string, partySize: number): boolean =>
  roleModelFor(mapId, partySize) != null;

// The default map for pages that need one before the user picks (Dashboard, Run History's map
// filter). Underworld — the original and highest-traffic map.
export const DEFAULT_MAP_ID = '72';

export const mapById = (id: string): MapChoice | undefined => MAPS.find((m) => m.id === id);

export const defaultPartySize = (mapId: string): number | undefined => mapById(mapId)?.partySizes[0];

/** "Duo" for a 2-person party, otherwise "N-man". */
export const sizeLabel = (n: number): string => (n <= 2 ? 'Duo' : `${n}-man`);

// Gambling-stone / ecto data is only collected on the Underworld (the post-Dhuum gambling ritual),
// so the "Gamblers Anonymous" / "Luckiest Players" panels are hidden for other maps rather than
// shown perpetually empty.
export const mapSupportsGambling = (mapId: string): boolean => mapId === '72';
