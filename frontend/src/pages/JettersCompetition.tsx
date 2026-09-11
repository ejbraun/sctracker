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

// The official rule text, verbatim from the competition organizer. Numbered so "refer to rule #15"
// in rule 13 resolves correctly — rule 1 is the conduct line, rule 15 is the discretion/DQ clause.
const RULES = [
  'Don’t be an asshole to others or your partner.',
  'No scripts can be used.',
  'Teams must complete the three chosen dungeons — with no more and no less than two team members.',
  'All dungeons must be completed in Hard Mode. The dungeon is not “complete” until the dungeon ' +
    'quest does its final update.',
  'The team with the shortest combined time for all the dungeons will be the winner.',
  'Each dungeon must be separately timed. (A timer showing minutes and seconds is required) — ' +
    '/age will not suffice.',
  'Before entering the dungeon, each party member is required to post their build template in the ' +
    'team chat before beginning the dungeon (obviously after the recording has started), in order ' +
    'to check compliance with the listed Gambits.',
  'Each team needs one person recording the run for proof of completion and time. The person ' +
    'recording the run should have the timer.',
  'All submissions should be sent directly to Jetter via Discord PM.',
  'Party members cannot have duplicate Elite skills (example: only one party member can run Shadow ' +
    'Form in dungeon 1). You can use the skill again in a subsequent dungeon, but only one party ' +
    'member at a time can have it per instance.',
  'All personal consumables and cons are allowed except for: (1) seals or any other consumable ' +
    'that resets skill usage or gives a morale boost, (2) any/all summoning stones (this includes ' +
    'the new merchant), (3) Lunars, and (4) Rocks. Four Leaf Clovers are okay to use.',
  'All party members must have 0 morale before entering the dungeon (no negative or positive ' +
    'morale).',
  'All party members must participate in the killing of the final boss of each dungeon (no solo ' +
    'builds while your teammate AFKs) — unless your team’s strategy is specifically designed ' +
    'around splitting up, with each party member doing a specific job/role. The goal of this rule ' +
    'is to keep both party members actively involved: follow it in good faith, or refer to rule #15.',
  'Only 2 resurrection scrolls are allowed per party member, per dungeon (not 2 per level — 2 for ' +
    'the entire dungeon).',
  'If the organizer believes a team tried not to honor the spirit of the competition (loophole ' +
    'finding, running bars meant to solo the dungeon, scripting), the organizer reserves the right ' +
    'to disqualify or void that attempt.',
];

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
        <h2>Universal skill bans — every dungeon</h2>
        <ul>
          {UNIVERSAL_BANS.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
      </Panel>

      <Panel className={styles.rules}>
        <h2>Official rules</h2>
        <ol className={styles.ruleList}>
          {RULES.map((r, i) => (
            <li key={i}>{r}</li>
          ))}
        </ol>
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
        This board is a live reference, not the official standings — it auto-lists every completed
        2-player clear of these maps and does not verify rule or Gambit compliance. Per rule 9,
        official competition entries are decided from recordings + build templates submitted
        directly to Jetter via Discord PM.
      </p>
    </div>
  );
}
