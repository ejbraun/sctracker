import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * A leaderboard/loserboard table row for a single run — renders the caller's own cells, then
 * appends a "Go to run" link cell. A plain anchor, not a whole-row click/hover — that read as
 * accidental next to ExpandToggle's own click target sharing the row. `.panel a` (Panel.module.css)
 * already styles it, since every table using this lives inside a Panel.
 */
export function RunLinkRow({ runId, children }: { runId: number; children: ReactNode }) {
  return (
    <tr>
      {children}
      <td>
        <Link to={`/runs/${runId}`}>Go to run #{runId}</Link>
      </td>
    </tr>
  );
}
