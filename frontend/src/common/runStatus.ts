// Run outcome → status color/label, shared by StatusBadge (table rows) and RunTimelineChart (the
// dataviz skill's chart marks) so both use the exact same identity mapping.
//
// The four hex values below are the dataviz skill's fixed status palette (references/palette.md)
// — "fixed, never themed": pre-validated for CVD-safety/contrast, deliberately NOT swapped for
// GW1 theme tones the way chart chrome (surface/gridlines/axis) is. Could not run the skill's
// validator script against these specific mappings in this environment (no Node.js here) — flagged
// in FRONTEND_IMPLEMENTATION_PROGRESS.md.
export type RunStatusKind = 'completed' | 'wipe' | 'resign' | 'unknown';

export interface RunStatusInfo {
  kind: RunStatusKind;
  label: string;
  color: string;
}

const STATUS_GOOD = '#0ca30c';
const STATUS_WARNING = '#fab219';
const STATUS_SERIOUS = '#ec835a';
const STATUS_CRITICAL = '#d03b3b';

export function runStatus(completed: boolean, endReason: string): RunStatusInfo {
  if (completed) {
    return { kind: 'completed', label: 'Completed', color: STATUS_GOOD };
  }
  switch (endReason) {
    case 'wipe':
      return { kind: 'wipe', label: 'Wiped', color: STATUS_CRITICAL };
    case 'resign':
      return { kind: 'resign', label: 'Resigned', color: STATUS_WARNING };
    default:
      return { kind: 'unknown', label: 'Unknown', color: STATUS_SERIOUS };
  }
}
