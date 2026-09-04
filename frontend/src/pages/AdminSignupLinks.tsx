import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { GeneratedSignupLink, SignupLink } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate } from '../common/format';
import styles from './AdminSignupLinks.module.css';

const SIGNUP_LINKS_KEY = ['admin', 'signup-links'] as const;
const DEFAULT_MAX_USES = 10;

function inviteUrl(token: string): string {
  return `${window.location.origin}/signup?invite=${token}`;
}

function status(link: SignupLink): string {
  if (link.revoked_at) {
    return `Revoked ${formatDate(link.revoked_at)}`;
  }
  if (link.use_count >= link.max_uses) {
    return 'Used up';
  }
  return 'Active';
}

/**
 * Admin-only — gated by AdminRoute. Mints multi-use signup links: one shareable URL good for up to
 * `max_uses` signups (default 10), as an alternative to handing out single-use signup keys.
 * specs/frontend/08-admin-signup-links.md.
 */
export function AdminSignupLinks() {
  const queryClient = useQueryClient();
  const [label, setLabel] = useState('');
  const [maxUses, setMaxUses] = useState('');
  const [revealed, setRevealed] = useState<GeneratedSignupLink | null>(null);
  const [copied, setCopied] = useState(false);

  const linksQuery = useQuery({
    queryKey: SIGNUP_LINKS_KEY,
    queryFn: () => api.get<SignupLink[]>('/admin/signup-links'),
  });

  const generateMutation = useMutation({
    mutationFn: () =>
      api.post<GeneratedSignupLink>('/admin/signup-links', {
        label: label.trim() || undefined,
        max_uses: maxUses.trim() ? Number(maxUses) : undefined,
      }),
    onSuccess: (link) => {
      setRevealed(link);
      setCopied(false);
      setLabel('');
      setMaxUses('');
      queryClient.invalidateQueries({ queryKey: SIGNUP_LINKS_KEY });
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/admin/signup-links/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SIGNUP_LINKS_KEY }),
  });

  function handleGenerate(e: FormEvent) {
    e.preventDefault();
    generateMutation.mutate();
  }

  function handleRevoke(id: number) {
    if (window.confirm('Revoke this signup link? Nobody else will be able to sign up with it.')) {
      revokeMutation.mutate(id);
    }
  }

  return (
    <div>
      <h1>Signup Links</h1>
      <Panel>
        <p>
          A signup link lets up to <strong>{DEFAULT_MAX_USES}</strong> people (by default) create an account without
          a personal signup key. Share one link instead of handing out keys one at a time. Revoke a link to cut it
          off early; it also stops working once it hits its limit.
        </p>

        {revealed && (
          <div className={styles.revealBox}>
            <p className={styles.warning}>This link won't be shown again — copy it now.</p>
            <code className={styles.linkUrl} data-testid="signup-link-url">{inviteUrl(revealed.token)}</code>
            <button
              onClick={() => {
                navigator.clipboard?.writeText(inviteUrl(revealed.token));
                setCopied(true);
              }}
            >
              {copied ? 'Copied' : 'Copy link'}
            </button>
            <button onClick={() => setRevealed(null)}>Dismiss</button>
          </div>
        )}

        <ErrorBanner error={generateMutation.error ?? revokeMutation.error} />

        <form className={styles.generateForm} onSubmit={handleGenerate}>
          <label>
            Label (optional)
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="e.g. Discord recruitment" />
          </label>
          <label>
            Max signups
            <input
              type="number"
              min={1}
              max={100}
              value={maxUses}
              onChange={(e) => setMaxUses(e.target.value)}
              placeholder={String(DEFAULT_MAX_USES)}
            />
          </label>
          <button type="submit" disabled={generateMutation.isPending}>
            Generate link
          </button>
        </form>

        {linksQuery.isLoading && <p>Loading…</p>}
        {linksQuery.data && (
          <table>
            <thead>
              <tr>
                <th>Label</th>
                <th>Created</th>
                <th>Uses</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {linksQuery.data.map((link) => {
                const inactive = link.revoked_at != null || link.use_count >= link.max_uses;
                return (
                  <tr key={link.id} className={inactive ? styles.revoked : undefined}>
                    <td>{link.label ?? '—'}</td>
                    <td>{formatDate(link.created_at)}</td>
                    <td>
                      {link.use_count} / {link.max_uses}
                    </td>
                    <td>{status(link)}</td>
                    <td>
                      {!link.revoked_at && (
                        <button onClick={() => handleRevoke(link.id)} disabled={revokeMutation.isPending}>
                          Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
