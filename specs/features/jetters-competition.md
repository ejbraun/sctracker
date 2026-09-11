# Feature Spec — Jetter's Dungeon Competition board

Status: **implemented** (frontend only). A dedicated event page, separate from the per-map
`/leaderboards/:mapId` view.

## What it is

`/jetters` — the fastest **2-player (duo) completions** of three specific dungeons, one section
each, with the competition's skill restrictions shown as text.

| # | Dungeon | map_id | Gambit | Banned |
|---|---|---|---|---|
| 1 | Secret Lair of the Snowmen | 701 | Skill Prevention | Obsidian Flesh, Spell Breaker, Vow of Silence |
| 2 | Catacombs of Kathandrax | 570 | Eye of the North Skills | every EotN PvE-only skill (non-EotN PvE-only — Kurzick/Luxon, Sunspear, Lightbringer — are fine) |
| 3 | Bogroot Growths (Khabuus) | 615 | Anti-Melee | all melee abilities / skills / auto-attacks used for damage, incl. auto-attack-augmenting skills. Carrying a melee weapon is fine; accidental autos are fine. |

Universal, every dungeon: **Whirling Defense**, **Mirrored Stance**, **Necrotic Traversal** are
banned.

## Official rules (verbatim from the organizer)

Rendered as an ordered list on the page (`RULES` in `JettersCompetition.tsx`) — numbered so rule
13's internal "refer to rule #15" resolves correctly against rule 1 (conduct) through rule 15
(disqualification discretion):

1. Don't be an asshole to others or your partner.
2. No scripts can be used.
3. Teams must complete the three chosen dungeons — exactly two team members, no more, no less.
4. All dungeons in Hard Mode; "complete" = the dungeon quest's final update.
5. Winner = shortest combined time across all three dungeons.
6. Each dungeon separately timed with a minutes:seconds timer — `/age` does not suffice.
7. Each party member posts their build template in team chat before entering (after recording
   starts), so Gambit compliance can be checked.
8. One team member records the run (proof of completion + time); that member holds the timer.
9. Submissions go directly to Jetter via Discord PM.
10. No duplicate Elite skills between party members per dungeon instance (e.g. only one member can
    run Shadow Form in dungeon 1); reusable in a later dungeon.
11. Personal consumables/cons allowed except: seals / skill-reset / morale-boost consumables, all
    summoning stones (incl. the new merchant), Lunars, Rocks. Four Leaf Clovers are fine.
12. 0 morale (not negative, not positive) before entering.
13. Both party members must participate in the final boss kill (no solo + AFK partner) unless the
    team's strategy deliberately splits roles — good faith, or refer to rule 15.
14. Max 2 resurrection scrolls per party member per dungeon (whole dungeon, not per level).
15. Organizer's discretion to disqualify/void a run that doesn't honor the spirit of the
    competition (loophole abuse, solo-oriented bars, scripting).

## Design decisions

- **Display only, no verification.** The board does not check skill usage — bans are followed on
  the honour system for the competition. The tracker can't verify most of these anyway (partner
  skill bars are invisible in GW1; melee auto-attack damage and "elite never cast" aren't
  observable), so an automated checker would only ever *flag suspected* violations. Out of scope.
- **Runs auto-appear.** Any `party_size = 2, completed = true` run on maps 701 / 570 / 615 shows
  up — the same `runs` rows the normal leaderboards use. No opt-in flag, no submission step.
- **This board is a live reference, not the official standings** (rule 9: entries go to Jetter via
  Discord PM with a recording + build templates). Said explicitly in the page footnote so it isn't
  mistaken for the scoring mechanism.

## Implementation

**Frontend only. No backend change.** Each section calls the existing
`GET /api/leaderboards/maps/{mapId}/overall?limit=10&partySize=2` (spec 05 — "fastest completed
full runs", already `partySize`-filtered). Files:

- `frontend/src/pages/JettersCompetition.tsx` + `.module.css` — the page. Dungeon list + gambit
  text are a static `DUNGEONS` const; universal bans a static array.
- `frontend/src/App.tsx` — `<Route path="/jetters" element={<JettersCompetition />} />`.
- `frontend/src/components/Layout.tsx` — nav link "Jetter's Comp".

Depends on the dungeon `map_configs` `(id, 2, NULL)` rows from
[`dungeons.md`](dungeons.md) / changeset `055` — without a size-2 config the plugin drops duo
dungeon runs and the board stays empty.

## Not done / follow-up

- The competition has a start/end date — the page shows all-time duo runs, not a windowed range.
  Add a `?from=`/`?to=` pair (the `overall` endpoint already takes `from`) if the board should be
  scoped to the event window.
- No tie-break / per-player-best dedup beyond what `overall` does (fastest run per row, a person
  can appear twice).
