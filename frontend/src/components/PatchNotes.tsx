import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import styles from './PatchNotes.module.css';

/**
 * A `<details>` disclosure that lazy-fetches a module's patch notes (plain text, not JSON — a raw
 * `fetch`, not the `api` client) the first time it's expanded, then shows them in a scrollable box.
 * `url` is already the full account-scoped path from `AccountModule.patch_notes_url`. Shared by the
 * Plugins and Launcher pages (specs/frontend/09-downloads.md).
 */
export function PatchNotes({ url }: { url: string }) {
  const [open, setOpen] = useState(false);

  const notesQuery = useQuery({
    queryKey: ['patch-notes', url],
    queryFn: async () => {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error('failed to fetch patch notes');
      }
      return response.text();
    },
    enabled: open,
  });

  return (
    <details className={styles.patchNotes} onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className={styles.patchNotesSummary}>Patch notes</summary>
      {notesQuery.isLoading && <p>Loading…</p>}
      {notesQuery.isError && <p>Couldn't load patch notes.</p>}
      {notesQuery.data && <pre className={styles.patchNotesBox}>{notesQuery.data}</pre>}
    </details>
  );
}
