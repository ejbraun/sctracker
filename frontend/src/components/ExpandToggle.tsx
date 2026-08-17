/**
 * The per-row/per-panel arrow that toggles a ranked leaderboard/loserboard list between showing
 * just its #1 entry (collapsed, the default) and its top 5 (expanded). Lives inside a RunLinkRow's
 * first cell, so its click has to stop short of that row's own navigate-to-run click handler.
 */
export function ExpandToggle({ expanded, onToggle }: { expanded: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      className="gw-expand-toggle"
      aria-expanded={expanded}
      aria-label={expanded ? 'Show top result only' : 'Show top 5 results'}
      onClick={(e) => {
        e.stopPropagation();
        onToggle();
      }}
    >
      {expanded ? '▾' : '▸'}
    </button>
  );
}
