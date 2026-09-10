// Supported maps and their party-size configurations. Static reference data (specs/frontend/00 —
// "Static reference data"), mirroring the backend's map_configs table. /api/maps is still the
// source for names/ids at runtime, but the display order, short labels, and the set of party sizes
// each map supports live here. See specs/features/fow-and-party-size.md, specs/features/dungeons.md.

import { PRIMARY_PROFESSION_ROLES, TRAPPER_ROLES } from './roles';

export interface MapChoice {
  /** GW map_id, as a string (route-param form). For a multi-level dungeon this is the ENTRY level
   *  (Level 1) — the id every level of that run is published under. */
  id: string;
  /** Short label for compact UI — "UW", "FoW", "CoF". */
  short: string;
  name: string;
  /** Party sizes this map supports, ascending. Mirrors map_configs rows. */
  partySizes: number[];
  /** Preselected size for pages that need one before the user picks. Defaults to partySizes[0]. */
  defaultSize?: number;
  /** Optional grouping for the map <select> — renders as an <optgroup>. */
  group?: 'Elite Area' | 'Dungeon';
}

// Elite areas first (original + highest-traffic), then the Eye of the North dungeons alphabetically.
// Every dungeon supports every party size 1-8 and is role-less at every size
// (map_configs.role_model = NULL) — the Fissure-of-Woe-non-duo / Domain of Anguish shape. Party
// size is the all-human roster count; a multi-level dungeon's id is its entry (Level 1) map id.
const DUNGEON_SIZES = [1, 2, 3, 4, 5, 6, 7, 8];
export const MAPS: MapChoice[] = [
  { id: '72', short: 'UW', name: 'The Underworld', partySizes: [8], group: 'Elite Area' },
  {
    id: '34',
    short: 'FoW',
    name: 'The Fissure of Woe',
    partySizes: [1, 2, 3, 4, 5, 6, 7, 8],
    // The duo is the canonical FoW speed clear — default to it even though it isn't partySizes[0].
    defaultSize: 2,
    group: 'Elite Area',
  },
  { id: '474', short: 'DoA', name: 'Domain of Anguish', partySizes: [8], group: 'Elite Area' },

  // A dungeon defaults to the 8-man; low-man clears are picked explicitly.
  { id: '584', short: 'AH', name: "Arachni's Haunt", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '612', short: 'BC', name: 'Bloodstone Caves', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '615', short: 'BG', name: 'Bogroot Growths', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '570', short: 'CoK', name: 'Catacombs of Kathandrax', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '560', short: 'CoF', name: 'Cathedral of Flames', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '635', short: 'DD', name: 'Darkrime Delves', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '704', short: 'FIL', name: "Fronis Irontoe's Lair", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '630', short: 'Frost', name: "Frostmaw's Burrows", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '607', short: 'HotS', name: 'Heart of the Shiverpeaks', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '576', short: 'OP', name: 'Ooze Pit', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '578', short: 'Oola', name: "Oola's Lab", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '617', short: 'RP', name: "Raven's Point", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '573', short: 'RM', name: "Rragar's Menagerie", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '701', short: 'Snowmen', name: 'Secret Lair of the Snowmen', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '628', short: 'SoD', name: 'Sepulchre of Dragrimmar', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '581', short: 'SoO', name: 'Shards of Orr', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '623', short: 'Slavers', name: "Slavers' Exile", partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
  { id: '604', short: 'Vlox', name: 'Vloxen Excavations', partySizes: DUNGEON_SIZES, defaultSize: 8, group: 'Dungeon' },
];

// Role model per (map, party_size), mirroring the backend's map_configs.role_model. `null` = the
// config has no role model (every Fissure of Woe size except the duo, Domain of Anguish, and every
// dungeon), which hides the by-role panels and makes personal section bests un-gated. Kept static
// for first paint; the authoritative source is GameMap.configs from /api/maps.
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
  // Eye of the North dungeons — role-less at every party size 1-8 (055-seed-dungeons.xml).
  ...Object.fromEntries(
    ['584', '612', '615', '570', '560', '635', '704', '630', '607', '576', '578', '617', '573', '701', '628', '581', '623', '604'].flatMap(
      (id) => DUNGEON_SIZES.map((n) => [`${id}:${n}`, null]),
    ),
  ),
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
