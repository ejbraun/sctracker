// Wire-format types matching the backend's snake_case JSON
// (spring.jackson.property-naming-strategy=SNAKE_CASE) — see specs/backend/*.md for each shape.

export interface Person {
  id: number;
  username: string;
  alias: string | null;
  // True if this person's last plugin download predates the currently detected dll build (see
  // PluginDllVersionInitializer on the backend), or if they've never recorded a download at all.
  new_plugin_version_available: boolean;
}

/** Minimal, alias-only view of another person — backs the Run History "person" filter dropdown. */
export interface PersonSummary {
  id: number;
  alias: string;
}

/**
 * Minimal view of a character — backs the Run History "character" filter dropdown. person_id lets
 * the "person" and "character" filters cross-filter each other without a round trip per keystroke.
 */
export interface CharacterSummary {
  id: number;
  character_name: string;
  person_id: number;
}

export interface MachineKey {
  id: number;
  label: string | null;
  created_at: string;
  revoked_at: string | null;
}

export interface GeneratedMachineKey {
  id: number;
  key: string;
  label: string | null;
}

export interface PlayerCharacter {
  id: number;
  character_name: string;
  person_id: number;
}

export interface GameMap {
  id: number;
  name: string | null;
}

export interface ParticipantSummary {
  raw_name: string;
  character_name: string | null;
  alias: string | null;
  role: string | null;
}

export interface LeaderboardEntry {
  run_id: number;
  duration_ms: number;
  utc_start: string;
  participants: ParticipantSummary[];
}

export interface SectionEntry {
  run_id: number;
  duration_ms: number;
  utc_start: string;
  // Steady-clock-relative offsets from the run's start, not absolute timestamps — same as
  // RunDetail's objective start_ms/done_ms.
  start_ms: number | null;
  done_ms: number | null;
  // Only the participants whose role is gated in for this objective (role_objectives) — who
  // actually earned this time, not the whole party.
  participants: ParticipantSummary[];
}

// "Loserboards" — per-role, per-user death toll. Not gated by role_objectives — a death is a
// directly-recorded fact about that participant in that run, not inferred from where the party
// wiped. Not scoped to completed runs.
export interface RoleUserDeaths {
  role: string;
  user: string;
  total_runs: number;
  deaths: number;
  avg_deaths: number;
}

// "Loserboards" — per-user resign stats ("Global fails"), not attributable to a single role since
// resigning is a group decision. `percentage` is 0-100, not a 0-1 fraction.
export interface UserResign {
  user: string;
  total_runs: number;
  resigns: number;
  percentage: number;
}

// One user's single best-ever consecutive-run streak on a map — reused by both the Leaderboards
// "Longest Completed Streak" table and the Loserboards "Longest Resign/Wipe Streak" table.
export interface UserStreak {
  user: string;
  streak: number;
  streak_start: string;
  streak_end: string;
}

// "Leaderboards" — one (tracked item, user) row for "Luckiest Players": total reserved drops of
// that item, summed across every run the user participated in, luckiest (highest) first within
// each item. The set of tracked items isn't statically known on the frontend — item_name is
// derived directly from this response's rows (already grouped contiguously by item_id server-side)
// rather than from any hardcoded list, so a newly-tracked item just appears automatically.
export interface ItemDropLeader {
  item_id: number;
  item_name: string;
  user: string;
  total_count: number;
}

export interface PersonalSectionBest {
  duration_ms: number;
  // Steady-clock-relative offsets from the run's start, not absolute timestamps.
  start_ms: number | null;
  done_ms: number | null;
  // Single-element list (the one linked character/role that earned this time) — a list so the
  // "Users" column renders the same way as SectionEntry's.
  participants: ParticipantSummary[];
}

export interface PersonalBest {
  duration_ms: number;
}

// Same shape as LeaderboardEntry — the "Yours" table matches "Global"'s schema.
export interface PersonalBestEntry {
  run_id: number;
  duration_ms: number;
  utc_start: string;
  participants: ParticipantSummary[];
}

export interface RunSummary {
  run_id: number;
  map_id: number;
  map_name: string | null;
  utc_start: string;
  end_reason: string;
  completed: boolean;
  duration_ms: number | null;
  participant_count: number;
}

export interface ObjectiveEntry {
  sequence: number;
  name: string;
  status: number;
  start_ms: number | null;
  done_ms: number | null;
  duration_ms: number | null;
  // Nesting depth — found in a real payload sample, not originally specced. Always 0 so far;
  // nothing renders it yet.
  indent: number;
}

export interface ParticipantEntry {
  party_index: number;
  raw_name: string;
  character_id: number | null;
  character_name: string | null;
  primary_profession: string;
  secondary_profession: string | null;
  role: string | null;
  // Found in a real payload sample, not originally specced: party slots can be AI-controlled
  // heroes/henchmen, not just human players.
  is_player: boolean;
  is_hero: boolean;
  is_henchman: boolean;
  // Found in a real payload sample, not originally specced: how many times this participant died.
  deaths: number;
}

export interface RunDetail {
  run_id: number;
  map_id: number;
  map_name: string | null;
  utc_start: string;
  // NOT a timestamp — a steady_clock-based ms counter with no absolute meaning, confirmed against
  // a real payload sample. Renamed from the original instance_start (typed as a string/date) —
  // don't try to format this as a date.
  instance_start_ms: number | null;
  end_reason: string;
  completed: boolean;
  duration_ms: number | null;
  objectives: ObjectiveEntry[];
  participants: ParticipantEntry[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}
