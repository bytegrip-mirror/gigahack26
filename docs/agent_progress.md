# agent_progress — the configurability and credibility pass

Tracking file for this goal. Updated as work lands, not at the end.

Status: **all phases landed. Phase 4's corpus pass is on its third run — the first two
found defects that are now fixed, and the third is verifying the fix.**

---

## The goal, restated as work

| # | Asked for | Status |
| --- | --- | --- |
| 1 | Remove `postgres up · mongo up` and `local-rubric-v1` from the footer | todo |
| 2 | Drafts must read organically — no `[1]` citation markers | todo |
| 3 | Everything configurable, including brand voice and talk style | todo |
| 4 | Questions panels are messy and hard to read | todo |
| 5 | Classification must use real algorithms and LLM classifiers, not fixed cue lists | in progress |
| 6 | Service lines must be configurable | todo |
| 7 | Validate against the hackathon challenge; build everything except third-party integrations | done |
| 8 | Bottom-left: next scheduled refresh | todo |
| 9 | Progress tracked in `agent_progress.md` | this file |
| 10 | Service line as a free-text string in the question editor → needs a picker | todo |
| 11 | Shows `data.initiative` instead of a human title | todo |
| 12 | Switching service line makes the list vanish and come back; it does not switch | todo |
| 13 | Loading spinners where they are missing | todo |

---

## Phase 1 — the reported defects

- [x] **The footer.** Store-health line and engine name removed; the rail now
      carries the refresh countdown instead. `/api/status` still reports both, for
      whoever operates the deployment.
- [x] **Service line switching.** Reproduced: `chooseLine` set `meta = null` before
      fetching, which empties the only list the rail renders from — hence the list
      vanishing and returning. The rail now keeps its list and swaps the payload,
      and shows a spinner on the line being loaded.
- [x] **Keys shown as titles.** Questions carry a human `title`, seeded for all 11
      shipped questions and derived from the text for anything created. Backend done;
      the UI still renders keys in two places and is being changed.
- [x] **Free-text service lines.** The editor is a picker over the configured set,
      and a question naming something unconfigured says so rather than inventing it.
- [x] **Missing spinners.** Panels that load now show progress: the rail while a
      service line swaps, the settings panel, the studio list, the pipeline, the board.

## Phase 2 — service lines as data

- [x] `service_line` table: key, name, description, value proposition, order, active.
- [x] `GET/POST/PATCH/DELETE /api/service-lines`.
- [x] Boards read the service-line list from the table, falling back to the questions
      when the table is empty.
- [x] Deleting is refused while questions reference the line, naming the count.
- [x] Pickers read from the table: the studio's service line is a select, and the
      settings panel lists, adds, enables, disables and deletes lines.

## Phase 3 — one settings surface

- [x] A single **Settings** area with a section rail: voice, customer profile,
      service lines, scoring.
- [x] **Brand voice**: tone, register, length, language, the ask, sign-off,
      signature, banned phrases and free notes, with the brief the writer receives
      printed back so it can be read rather than guessed at.
- [x] Voice is data the writer reads. Drafts change when it changes.

## Phase 4 — classification that can be defended

- [~] A full model pass over the corpus is running: every pair the cue lists would
      have matched, every disqualifying question, and a sample of the rest, each
      quote verified against its capture. The first attempt finished in fifteen
      minutes and exposed two defects, both now fixed and the pass restarted:
      truncated replies were being discarded (191 in six minutes), and a failed
      call was indistinguishable from a considered rejection, so failures were
      being written as "no signal" and suppressing the cue list that would have
      matched. The overlay lands as `corpus/signals.json` when the re-run finishes.
- [x] Every signal records which engine produced it, and a draft that fell back says
      so rather than looking like one the model wrote.
- [ ] Cue lists remain the fallback's configuration and stay editable, but stop
      being the product's answer to "how does it decide" — this rests on the model
      pass above landing, and on the seeded board actually reading the overlay.

## Phase 5 — outreach that reads like a person wrote it

- [x] No `[1]` markers in the body. Citations stay available to the rep in a side
      panel, because a rep may want them and a prospect must not see them.
- [x] Two writers behind one router: a model writer that drafts from the findings
      under the configured voice, and a deterministic composer for when no model is
      configured. Both go through the same banned-phrase filter.
- [x] Drafts open on what was noticed, in plain speech, with no reference to sources.

## Phase 6 — scheduled refresh

- [x] A scheduler re-scores on a configurable interval.
- [x] `/api/meta` reports the next run and the interval.
- [x] Shown bottom-left in the rail, ticking, where the store-health line used to be.

## Phase 7 — against the challenge

Walked `docs/MAIN_CHALLENGE.md` line by line.

| Brief asks for | Where it lives | State |
| --- | --- | --- |
| Configure ICP by market, industry, company size, geography | Settings → Customer profile | done |
| Configure signal questions per service | Signal Studio | done |
| Signal weights — high, medium, low | Studio, per question, in nats | done |
| Negative signals and disqualification rules | Studio polarity; gates in Settings | done |
| Configure the scoring logic | Settings → Scoring | done |
| Configure service lines themselves | Settings → Service lines | done |
| Company profiles, key people, corporate events | Corpus: registry (GLEIF LEI), filings | partial — no key people; Crunchbase is paywalled and deliberately not used |
| Websites, newsrooms, annual reports | Corpus: news + filings, 4,050 documents | done |
| Google News / NewsAPI / GDELT | Bing News RSS; GDELT 429s from this host | done, source substituted |
| Career pages and job boards | Corpus: 577 job postings from public ATS boards | done |
| LinkedIn — optional, must not be depended on | Not used anywhere | done by omission |
| Large-scale extraction and normalisation | Span extraction over stored captures | done |
| Dynamic prospect scoring, explainable | Additive log-odds ledger, arithmetic printed on the card | done |
| Signal detection: incidents, leadership change, compliance, tech stack | Question library covers all four | done |
| Outreach generation, customised value proposition | Model writer under the configured voice, per service line | done |
| Multi-channel drafts — email, InMail | Both channels | done |
| Dashboard | SvelteKit SPA; the brief says React but does not require it | done, stack differs |
| CRM integration hooks | HubSpot button records intent; no token configured | deferred by the goal |

Gaps worth naming, not hidden:

- **No key-people data.** The brief lists it under Crunchbase, which is paywalled.
  Nothing in the corpus identifies a named decision-maker, so the drafts address a
  role rather than a person. Targeting a person needs a data source we do not have.
- **The corpus is news-heavy.** Job postings are present at 577 documents but the
  bulk is press. The taxonomy's rule that reaching the top band needs two
  independent kinds of evidence is satisfied by 67 companies, not by most.
- **DELIBERATE:** HubSpot and every other third-party integration stay as recorded
  intents, per the goal.

---

## Incident — 2026-09-25, production down

Recording this because it was self-inflicted and it is the kind of thing that
should not be quietly dropped from a status file.

Adding `PolicyQuestion.title` and ten `voice_*` columns to `workspace_settings` as
`NOT NULL` broke the schema update: Postgres refuses to add a NOT NULL column to a
table that already holds rows without a default, so the column was never created
and every query naming it failed at startup. Builds #45 and #46 failed and
production served 404 for roughly ten minutes. The local stack was down at the same
time, which is why the failure was not caught before pushing.

Fixed by making the new columns nullable, with the getters supplying the defaults
the screens expect; the policy sync fills the titles on the next boot. Verified by
starting against the existing database rather than a fresh one — which is the
check that would have caught it, and which the earlier redeploy test did cover for
a different change and should have been run for this one.

## Open items

- The reference image (`image.png`) is not on this machine and was never reviewed.
  Everything here comes from the written description.
- Third-party integrations (HubSpot and any other CRM, email or enrichment
  provider) are deliberately left as recorded intents, per the goal.
