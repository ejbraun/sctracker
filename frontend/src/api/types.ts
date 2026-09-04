// Wire-format types matching the backend's snake_case JSON
// (spring.jackson.property-naming-strategy=SNAKE_CASE) — see specs/backend/*.md for each shape.

export interface Person {
  id: number;
  username: string;
  alias: string | null;
  // True if the build this person's plugin last advertised over X-Plugin-Version is below the
  // current manifest version, or if their plugin has never authenticated at all (backend:
  // PluginVersionService.isOutdated).
  new_plugin_version_available: boolean;
  // Membership in the admins table (see AdminAuthInterceptor) — gates the "User Management" nav
  // link/route and every /api/admin/** call. Toggled by an existing admin from User Management
  // (PATCH /api/admin/users/{id}/admin); the bootstrap first admin is a hand DB insert.
  is_admin: boolean;
}

/** Row shape for the admin-only "User Management" page — GET/PATCH /api/admin/users. */
export interface AdminUser {
  id: number;
  username: string;
  alias: string | null;
  can_report_failures: boolean;
  // Toggled via PATCH /api/admin/users/{id}/admin — an admin can't revoke their own.
  is_admin: boolean;
}

/**
 * Registry row for the admin-only "Modules" page — GET/POST/PATCH/DELETE /api/admin/modules.
 * current_* are read-only (ModuleManifestCache fills them from the artifact's manifest).
 */
export type ModuleType = 'plugin' | 'module';

export interface AdminModule {
  id: number;
  module_key: string;
  display_name: string;
  // 'plugin' = a GWToolbox++ plugin DLL; 'module' = a ProjectPotato launcher module.
  type: ModuleType;
  is_public: boolean;
  enabled: boolean;
  bucket_prefix: string;
  artifact_object: string;
  manifest_object: string | null;
  content_type: string;
  current_version: number | null;
  current_sha256: string | null;
  version_detected_at: string | null;
  sort_order: number;
}

/**
 * GET /api/admin/modules/discover — a plugins/<Folder>/ or launcher/<Folder>/ directory in the
 * bucket that has an artifact (.dll / .zip / .exe) but no `modules` row yet. suggested_type follows
 * the prefix ('plugin' under plugins/, 'module' under launcher/); the admin can override it, and
 * still picks display_name + is_public, then imports via POST /admin/modules.
 */
export interface DiscoveredModule {
  folder_name: string;
  suggested_key: string;
  suggested_display_name: string;
  suggested_type: ModuleType;
  bucket_prefix: string;
  artifact_object: string;
  manifest_object: string | null;
  has_manifest: boolean;
}

/**
 * GET /api/account/modules — the logged-in user's module entitlements (public + whatever's been
 * granted), the session-authed web counterpart to the machine-key /module-entitlements. download_url
 * is /SCTracker.dll for sctracker and /api/account/modules/{key}/download for everything else (a
 * session-authed stream, so a gated module like gwrl-install downloads straight from a browser link).
 */
export interface AccountModule {
  key: string;
  display_name: string;
  type: ModuleType;
  is_public: boolean;
  version: number | null;
  sha256: string | null;
  download_url: string;
}

export interface AccountModulesResponse {
  modules: AccountModule[];
}

/**
 * One row of the per-user module checklist — GET /api/admin/users/{personId}/modules.
 * Public modules come back granted=false but are shown as "always available".
 */
export interface AdminUserModule {
  module_key: string;
  display_name: string;
  is_public: boolean;
  granted: boolean;
  granted_at: string | null;
  granted_by: number | null;
}

/** GET /api/admin/runs/unregistered-count — how many runs the wipe below would delete. */
export interface UnregisteredRunsCount {
  count: number;
}

/** POST /api/admin/runs/wipe-unregistered response. */
export interface WipeUnregisteredRunsResult {
  deleted_count: number;
}

/**
 * GET /plugin-version — top-level, not under /api (see PluginVersionController), so it's fetched
 * directly rather than through the /api-prefixed api client. Same shape the plugin itself checks
 * against on load.
 */
export interface PluginVersion {
  version: number;
  compiled_at: string;
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
  // Supported (party_size, role_model) configs for this map — mirrors map_configs. The frontend's
  // static MAPS registry (common/maps.ts) is the primary source for the size selector; this is
  // available for reconciliation. role_model is null for a config with no role model.
  configs: { party_size: number; role_model: string | null }[];
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

// "Loserboards" — per-role, per-user count of how many times that role was flagged (via the
// plugin's post-run failure popup) as at fault for a run's failure. `user` is whichever character
// held that role in the flagged run.
export interface FailureReasonEntry {
  role: string;
  user: string;
  total_runs: number;
  fails: number;
  avg_fails: number;
}

// "Leaderboards" — the positive-side mirror of FailureReasonEntry: per-role, per-user count of how
// many times that role earned the MVP award (via the plugin's post-run MVP popup). `user` is
// whichever character held that role in each awarded run.
export interface RoleMvpAwardEntry {
  role: string;
  user: string;
  total_runs: number;
  awards: number;
  avg_awards: number;
}

// "Loserboards" — a user whose plugin last authenticated (any machine-key call, most reliably the
// once-per-launch /can-report-run-failure) on a version below the current minimum, within the
// selected time window. plugin_version is null for a client too old to send X-Plugin-Version. Not
// map-scoped — nothing about a run is involved.
export interface OutdatedPlugin {
  user: string;
  plugin_version: number | null;
  last_seen: string;
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
// that item, summed across every run the user participated in, plus their average per run (total
// divided by every run they've participated in on the map, not just runs the item dropped in) —
// luckiest by that average first within each item. The set of tracked items isn't statically known
// on the frontend — item_name is derived directly from this response's rows (already grouped
// contiguously by item_id server-side) rather than from any hardcoded list, so a newly-tracked item
// just appears automatically.
export interface ItemDropLeader {
  item_id: number;
  item_name: string;
  user: string;
  total_count: number;
  run_count: number;
  avg_per_run: number;
}

// "Leaderboards" — "Gamblers Anonymous": per-user net Ghastly Summoning Stones won (positive) or
// lost (negative) gambling with other party members at the end of a successful run, summed across
// every completed run on the map where the user actually gambled. run_count only counts runs where
// they gambled, not every completed run they played in. Already sorted biggest-net-winner-first by
// the backend.
export interface GamblingStoneLeader {
  user: string;
  runs_gambled: number;
  net_stones: number;
}

export interface PersonalSectionBest {
  run_id: number;
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
  // Roster size the run was uploaded with (8 for Underworld, 2 for a Fissure of Woe duo), frozen at
  // creation. participant_count can lag while multi-uploads for the same run trickle in; the UI
  // shows party_size.
  party_size: number;
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

// One run_participant flagged as at fault for a run's failure via the plugin's post-run popup, or a
// deliberate "nobody was at fault" assertion. `nobody` is structural, not a "Nobody" sentinel string
// in display_name — a real character could plausibly be named "Nobody". role/display_name are null
// when nobody is true.
export interface RunFailureReasonEntry {
  nobody: boolean;
  role: string | null;
  display_name: string | null;
}

// The run_participant credited as MVP via the plugin's post-run popup, or a deliberate "nobody
// stood out" assertion — same shape as RunFailureReasonEntry, just singular (a run has at most one
// MVP award). `nobody` is structural, not a "Nobody" sentinel string in display_name.
// role/display_name are null when nobody is true.
export interface RunMvpAwardEntry {
  nobody: boolean;
  role: string | null;
  display_name: string | null;
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
  // Roster size the run was uploaded with — 8 (Underworld) or 2 (Fissure of Woe duo).
  party_size: number;
  objectives: ObjectiveEntry[];
  participants: ParticipantEntry[];
  failure_reasons: RunFailureReasonEntry[];
  // null when no MVP vote has resolved for this run at all — distinct from an explicit "Nobody" result.
  mvp_award: RunMvpAwardEntry | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}
