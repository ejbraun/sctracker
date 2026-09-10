import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { LeaderboardEntry } from '../api/types';
import { Panel } from '../components/Panel';
import { RunLinkRow } from '../components/RunLinkRow';
import { formatDate, formatDuration } from '../common/format';
import styles from './JettersCompetition.module.css';

/**
 * Jetter's Dungeon Competition — a special event board: fastest 2-player ("duo") completions of
 * three specific dungeons, each run under its own "Gambit" skill restriction. Display only — runs
 * appear automatically from any completed party_size=2 clear on these maps; the bans are enforced
 * on the honour system for the competition, this board does not verify skill usage. Separate from
 * the per-map /leaderboards/:mapId view on purpose.
 */

const UNIVERSAL_BANS = ['Whirling Defense', 'Mirrored Stance', 'Necrotic Traversal'];

interface CompetitionDungeon {
  mapId: string;
  name: string;
  /** Secondary label, e.g. the boss/HM name. */
  subtitle?: string;
  gambit: string;
  rule: string;
}

const DUNGEONS: CompetitionDungeon[] = [
  {
    mapId: '701',
    name: 'Secret Lair of the Snowmen',
    gambit: 'Skill Prevention',
    rule: 'Banned for this dungeon: Obsidian Flesh, Spell Breaker, Vow of Silence.',
  },
  {
    mapId: '570',
    name: 'Catacombs of Kathandrax',
    gambit: 'Eye of the North Skills',
    rule:
      'Every PvE-only skill from Eye of the North is banned. Non-EotN PvE-only skills ' +
      '(Kurzick / Luxon, Sunspear, Lightbringer, etc.) are fair game.',
  },
  {
    mapId: '615',
    name: 'Bogroot Growths',
    subtitle: 'Khabuus',
    gambit: 'Anti-Melee',
    rule:
      'No melee abilities, skills, or auto-attacks used for damage — including any skill that ' +
      'augments auto-attacks. You may carry a melee weapon, just don’t deal damage with it. ' +
      'Accidental autos are fine, don’t panic.',
  },
];

function DuoBoard({ mapId }: { mapId: string }) {
  const query = useQuery({
    queryKey: ['jetters', 'overall', mapId],
    queryFn: () => api.get<LeaderboardEntry[]>(`/leaderboards/maps/${mapId}/overall?limit=10&partySize=2`),
  });

  if (query.isLoading) {
    return <p>Loading…</p>;
  }
  if (!query.data || query.data.length === 0) {
    return <p className={styles.empty}>No completed duo runs recorded yet.</p>;
  }

  return (
    <table className={styles.board}>
      <thead>
        <tr>
          <th>Rank</th>
          <th>Time</th>
          <th>Date</th>
          <th>Duo</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {query.data.map((entry, index) => (
          <RunLinkRow key={entry.run_id} runId={entry.run_id}>
            <td className={styles.rank}>{index + 1}</td>
            <td>{formatDuration(entry.duration_ms)}</td>
            <td>{formatDate(entry.utc_start)}</td>
            <td>
              <div className={styles.duo}>
                {entry.participants.map((p, i) => (
                  <span key={i}>{p.alias ?? p.character_name ?? p.raw_name}</span>
                ))}
              </div>
            </td>
          </RunLinkRow>
        ))}
      </tbody>
    </table>
  );
}

export function JettersCompetition() {
  return (
    <div>
      <h1>Jetter's Dungeon Competition</h1>
      <p className={styles.blurb}>
        A special competition across three dungeons. Fastest 2-player (duo) completions only. Each
        dungeon carries its own skill restriction — its “Gambit” — on top of the universal bans
        below.
      </p>

      <Panel className={styles.rules}>
        <h2>Universal bans — every dungeon</h2>
        <ul>
          {UNIVERSAL_BANS.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
        <p className={styles.elite}>
          <strong>No duplicate elite skills</strong> between the two party members, on every
          dungeon.
        </p>
      </Panel>

      {DUNGEONS.map((d, i) => (
        <Panel key={d.mapId} className={styles.section}>
          <h2>
            Dungeon #{i + 1}: {d.name}
            {d.subtitle && <span className={styles.subtitle}> ({d.subtitle})</span>}
          </h2>
          <p className={styles.gambit}>
            <span className={styles.gambitTag}>Gambit — {d.gambit}</span> {d.rule}
          </p>
          <DuoBoard mapId={d.mapId} />
        </Panel>
      ))}

      <p className={styles.footnote}>
        Runs appear automatically from any completed 2-player clear of these maps. Skill bans are
        followed on the honour system for the competition — this board does not verify skill usage.
      </p>
    </div>
  );
}
