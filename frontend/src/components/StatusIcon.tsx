import type { RunStatusKind } from '../common/runStatus';

/**
 * One shape per run-status kind — circle/X/triangle/diamond — so status is never carried by color
 * alone (dataviz skill non-negotiable). Shared between StatusBadge (table rows, small) and
 * RunTimelineChart (chart marks, larger), so the shape language is identical in both places.
 * Each shape gets a dark stroke outline so it stays legible on the parchment surface even where
 * the status fill color's own contrast is weak (warning/serious are sub-3:1 on a light surface by
 * the skill's own numbers — the outline plus the always-present legend/tooltip label are the
 * mitigation, per its "icon + label, never color alone" rule).
 */
export function StatusIcon({ kind, color, size = 12 }: { kind: RunStatusKind; color: string; size?: number }) {
  const s = size;
  const half = s / 2;
  const stroke = 'var(--gw-text-on-parchment)';

  switch (kind) {
    case 'completed':
      return (
        <svg width={s} height={s} viewBox={`0 0 ${s} ${s}`} aria-hidden="true">
          <circle cx={half} cy={half} r={half - 1} fill={color} stroke={stroke} strokeWidth={1} />
        </svg>
      );
    case 'wipe':
      return (
        <svg width={s} height={s} viewBox={`0 0 ${s} ${s}`} aria-hidden="true">
          <line x1={1} y1={1} x2={s - 1} y2={s - 1} stroke={color} strokeWidth={2.5} strokeLinecap="round" />
          <line x1={s - 1} y1={1} x2={1} y2={s - 1} stroke={color} strokeWidth={2.5} strokeLinecap="round" />
        </svg>
      );
    case 'resign':
      return (
        <svg width={s} height={s} viewBox={`0 0 ${s} ${s}`} aria-hidden="true">
          <polygon points={`${half},1 ${s - 1},${s - 1} 1,${s - 1}`} fill={color} stroke={stroke} strokeWidth={1} />
        </svg>
      );
    case 'unknown':
      return (
        <svg width={s} height={s} viewBox={`0 0 ${s} ${s}`} aria-hidden="true">
          <polygon points={`${half},1 ${s - 1},${half} ${half},${s - 1} 1,${half}`} fill={color} stroke={stroke} strokeWidth={1} />
        </svg>
      );
    default:
      return null;
  }
}
