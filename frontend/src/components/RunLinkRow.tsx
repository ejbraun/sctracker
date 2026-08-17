import type { ReactNode, KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * A leaderboard/loserboard `<tr>` that navigates to /runs/:runId on click — same destination
 * RunHistory's per-cell Link already goes to, just applied to the whole row since these tables
 * don't have one obvious "title" cell to hang a Link off of. role="link" + Enter-to-activate keeps
 * it keyboard-reachable despite `<tr>` not being natively focusable/interactive.
 */
export function RunLinkRow({ runId, children }: { runId: number; children: ReactNode }) {
  const navigate = useNavigate();

  const go = () => navigate(`/runs/${runId}`);
  const onKeyDown = (e: KeyboardEvent<HTMLTableRowElement>) => {
    if (e.key === 'Enter') {
      go();
    }
  };

  return (
    <tr className="gw-clickable-row" role="link" tabIndex={0} onClick={go} onKeyDown={onKeyDown}>
      {children}
    </tr>
  );
}
