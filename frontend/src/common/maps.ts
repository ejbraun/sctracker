// Supported maps and their party-size configurations. Static reference data (specs/frontend/00 —
// "Static reference data"), mirroring the backend's map_configs table. /api/maps is still the
// source for names/ids at runtime, but the display order, short labels, and the set of party sizes
// each map supports live here. See specs/features/fow-and-party-size.md.

import { PRIMARY_PROFESSION_ROLES, TRAPPER_ROLES } from './roles';

export interface MapChoice {
  /** GW map_id, as a string (route-param form). */
  id: string;
  /** Short label for compact UI — "UW", "FoW". */
  short: string;
  name: string;
  /** Party sizes this map supports, ascending. Mirrors map_configs rows. */
  partySizes: number[];
  /** Preselected size for pages that need one before the user picks. Defaults to partySizes[0]. */
  defaultSize?: number;
}

export const MAPS: MapChoice[] = [
  { id: '72', short: 'UW', name: 'The Underworld', partySizes: [8] },
  {
    id: '34',
    short: 'FoW',
    name: 'The Fissure of Woe',
    partySizes: [1, 2, 3, 4, 5, 6, 7, 8],
    // The duo is the canonical FoW speed clear — default to it even though it isn't partySizes[0].
    defaultSize: 2,
  },
  { id: '474', short: 'DoA', name: 'Domain of Anguish', partySizes: [8] },
];

// Role model per (map, party_size), mirroring the backend's map_configs.role_model. `null` = the
// config has no role model (every Fissure of Woe size except the duo has no fixed composition),
// which hides the by-role panels and makes personal section bests un-gated. Kept static for first
// paint; the authoritative source is GameMap.configs from /api/maps.
const ROLE_MODEL: Record<string, string | null> = {
  '72:8': 'trapper',
  '34:1': null,
  '34:2': 'primary_profession',
  '34:3': null,
  '34:4': null,
  '34:5': null,
  '34:6': null,
  '34:7': null,
  '34:8': null,
  // Domain of Anguish — 8-man only, no fixed role composition (like FoW 8-man).
  '474:8': null,
};

export const roleModelFor = (mapId: string, partySize: number): string | null | undefined =>
  ROLE_MODEL[`${mapId}:${partySize}`];

/** Whether this (map, party size) config is role-gated — i.e. has a role model. */
export const configHasRoles = (mapId: string, partySize: number): boolean =>
  roleModelFor(mapId, partySize) != null;

/** The role codes that apply to this (map, party size) — what the by-role board panels iterate. */
export const rolesForConfig = (mapId: string, partySize: number): readonly string[] => {
  switch (roleModelFor(mapId, partySize)) {
    case 'trapper':
      return TRAPPER_ROLES;
    case 'primary_profession':
      return PRIMARY_PROFESSION_ROLES;
    default:
      return [];
  }
};

// The default map for pages that need one before the user picks (Dashboard, Run History's map
// filter). Underworld — the original and highest-traffic map.
export const DEFAULT_MAP_ID = '72';

export const mapById = (id: string): MapChoice | undefined => MAPS.find((m) => m.id === id);

export const defaultPartySize = (mapId: string): number | undefined => {
  const map = mapById(mapId);
  return map?.defaultSize ?? map?.partySizes[0];
};

/** "Solo" for 1, "Duo" for 2, otherwise "N-Man". */
export const sizeLabel = (n: number): string => (n === 1 ? 'Solo' : n === 2 ? 'Duo' : `${n}-Man`);

// Gambling-stone / ecto data is only collected on the Underworld (the post-Dhuum gambling ritual),
// so the "Gamblers Anonymous" / "Luckiest Players" panels are hidden for other maps rather than
// shown perpetually empty.
export const mapSupportsGambling = (mapId: string): boolean => mapId === '72';
