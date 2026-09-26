# agent_progress — the winning pass

Rewritten from scratch. The previous file tracked the configurability goal and is
superseded; its useful residue is folded into the audit below.

Status: **landed and pushed.** Everything below marked done is in `main` and deployed.

---

## Where we actually stand

A hostile-juror audit of the running system scored it **52.5 / 100** weighted:

| Criterion | Weight | Score | The single reason |
| --- | --- | --- | --- |
| Signal relevance & accuracy | 25% | 3 | The brief's own two examples fail. DHL sits at rank 242 with **zero signals**, p = 0.250 = exactly the base rate. Of the 12 Priority companies, 2–3 survive a read of their top evidence. Appian and Nintex are automation *vendors*, ranked on their own job ads. |
| AI/ML innovation | 20% | 5 | The scoring model is genuinely strong (additive log-odds, a theme cap *derived* from the Priority line, boolean gates outside the sum, rank discounting, horizon by bisection). The classification half is a keyword cue matcher: all 2,945 live signals are `local-rubric-v1`. |
| Configurability | 20% | 7 | Nine real config surfaces. Held back by `icpIndustries` being dead config and the derived cues being invisible. |
| Usability & UX | 15% | 6 | The byte-range quote — the one thing no competitor can copy — is three clicks deep. |
| Business impact | 10% | 5 | No ROI, no backtest, no cost meter, corpus baked into the image. |
| Technical execution | 10% | 7 | Live and verified end to end; no runtime ingestion, destructive rescores. |

Three attack vectors, in the order a juror will find them:

1. **"Your platform does not find the two examples in your own brief."** The crawl
   is Bing News RSS only, so a listed company yields price news.
2. **"Your top leads are vendors of the thing you sell."** Appian and Nintex.
3. **"A juror can type a question and get 379 matches of nothing"** — and the
   destructive re-score can empty the board mid-demo.

Two assets are badly under-sold: the **byte-range citation** verified by
containment-then-fuzzy-with-strike, and **disqualifier gates that keep the
arithmetic visible** while removing a company from the queue.

---

## What landed

### Correctness and accuracy

- [x] **Question routing.** "Is the company in Romania?" now returns the 17 Romanian
      companies instead of classifying 4,050 documents against the word "Romania".
      Geography, industry, size and competitor status are read as *facts about a
      company* and filter the board; whatever is left of the sentence is classified
      as a signal question. `AskService`, `GeoLexicon`, `AskController`
      (`GET /api/ask/read`, `POST /api/ask`). The panel narrates the reading as you
      type and labels the button by what will happen.
- [x] **Stray test questions deleted** from the local and the deployed database.
      They were inflating the board: Priority fell from 28 to a true 12.
- [x] **Cue hygiene.** Derived cues are now phrases first and domain terms second;
      question vocabulary ("operations", "initiative", "programme", 100 more) can no
      longer become a cue, and acronyms (AI, RPA, SOC, NIS2, DORA, 40 more) now can
      — a cold question like "Does it use AI?" used to derive zero cues and silently
      do nothing.
- [x] **Document chrome no longer counts as evidence.** 48 boilerplate strings —
      "we also welcome factual corrections", "equal opportunity employer", "all
      rights reserved", "forward-looking statements" — are counter-cues on every
      question. SEW-EURODRIVE was in Priority on its newsletter footer.
- [x] **The false in-house disqualifier.** Bare "centre of excellence" and
      "security operations centre" gated companies that never said it. Cues are now
      self-referential, and vendor self-description is caught properly.
- [x] **Job boards no longer outrank the wires** (authority 1.0 → 0.9, below
      Reuters at 0.95), and findings are de-duplicated per span instead of counting
      one passage three times.
- [x] **The gate report tells the truth.** It printed whichever gate happened to
      fire against the size column, so a competitor gate read "size → FAIL".
- [x] **The size floor is reachable.** Seeded confidence was 0.6 against a 0.8
      scoring floor, so the Settings control could never fire.
- [x] **Every row records the engine that answered**, instead of stamping the local
      engine whatever responded.

### New signals

- [x] **Six new questions**, including the two the brief names itself — a
      cost-reduction programme with a quantified target, and consolidating shared
      service centres, which now fires on real evidence (Reckitt: "using its shared
      services division as the driving force"). Also process mining, AI governance,
      a platform migration, and a regulatory deadline.

### The interface

- [x] **Bookmarking.** A star in the board row and the company pane, a **Saved**
      filter with a live count, `GET/POST/DELETE /api/bookmarks`.
- [x] **The quote is on the first screen.** The board's Top evidence column now
      prints the verbatim passage, not just the question it answers — the one asset
      a competitor cannot reproduce was three clicks deep.
- [x] **Four HIGH defects** found by a flow audit: the pane covered the ask button
      at 1024×768 (now scrimmed and unambiguous); "Open the quoted capture" scrolled
      to prose and hid the quote (now scrolls to the mark, with less lead-in);
      `?co=` did not carry its service line, so a shared link showed another line's
      score; and opening an outreach draft silently marked the company contacted.
- [x] Compose spinner centred, real loading on channel switch, Studio preview says
      *Loading* with a greyed overlay, the ask note no longer overlaps its button.
- [x] The company pane runs the full height of the window.

## Security review of the new endpoints

Three automated reviews flagged the same class — missing tenant scoping and
authorization — against `AskService`, `AskController` and `BookmarkController`.
None of them is exploitable here, and the reasoning is the posture AGENTS.md
already records: **there is no authentication and no tenancy, by design.** The
corpus is public web pages, there are no user accounts and no personal data, and
the jury is meant to type a question into the Studio themselves. `/api/ask` reads
the same public corpus that `/api/companies` already serves to anyone, so it
grants no access that did not exist before. Adding `@PreAuthorize` would break the
only interaction the demo depends on.

One finding did point at something real, and it is fixed: `/api/ask/read` is
called on every keystroke and was loading all 578 companies from Postgres each
time. It now reads a 30-second snapshot.

Later reviews flagged `GET /api/telemetry`, `GET /api/market` and `GET /api/backtest` as unscoped —
signal counts per engine, corpus coverage, cache reuse, and aggregates by country
and industry. Both carry strictly less than `/api/companies` already serves to
anyone, and neither carries personal data. The `serviceLine` parameter is a
filter over a public corpus, not a tenant boundary: there are no tenants, no
users and no roles anywhere in this system, so there is nothing to scope against
and `@PreAuthorize` would have no subject. The one parameter that could be
abused, the backtest horizon, is clamped server-side to between a week and a year
so it cannot be turned into a denial-of-service lever either. Both are held to the same posture
rather than given a half-measure of auth that would break the only interaction
the demo depends on. Recorded here so the decision is visible rather than
silent, and so the next reviewer does not have to re-derive it.

One is a genuine, accepted trade-off rather than a bug: **bookmarks are shared,
mutable state with no owner.** A visitor to the public demo can add or clear
them. That is the same exposure as the rest of the product — anyone who can reach
the API can already rewrite policies and re-score the board — and it carries no
personal data, so it is left consistent with the deployment's stated posture
rather than given a half-measure of auth.

## Still open, and why

- [ ] **The model overlay (`corpus/signals.json`) is not committed.** It is the
      single biggest remaining accuracy lever and it needs a DeepSeek key to build.
      It is worth roughly a third of the accuracy criterion on its own.
- [ ] **The brief's own two examples (DHL, Lufthansa) still score at the base
      rate.** The corpus has no newsroom or annual-report crawl for them. Fetching
      those pages through the real crawler is the honest fix and was not done here.
- [ ] **The corpus tool is destructive.** `tools/fast_corpus.py` deletes registry
      and filing documents when it refreshes; it needs a merge mode and hash dedup
      before anyone runs it again.
- [ ] 24 companies have no documents, ~22 recoverable by fixing a four-character
      name-variant filter.

## What was already fixed (earlier in this pass)

- [x] **Question routing.** "Is the company in Romania?" now returns the 17
      Romanian companies instead of classifying 4,050 documents against the word
      "Romania". Geography, industry, size and competitor status are recognised as
      *facts about a company* and filter the board; whatever is left of the
      sentence is classified as a signal question. New `AskService`, `GeoLexicon`,
      `AskController` (`GET /api/ask/read`, `POST /api/ask`).
- [x] **Stray test questions deleted** from the local and the deployed database.
      `asked-2ef354c953` ("Is the company in romania?", 118 signals) and
      `asked-8e06fc1497` (2821 signals) were inflating the board: Priority went
      from 28 to a true 12.
- [x] Compose spinner centred and a real loading state on channel switch.
- [x] Studio preview says *Loading* and greys the content it is about to replace.
- [x] The ask panel's note no longer overlaps the Score button.

---

## The plan

Ordered by (judging weight × size of the hole). Everything here is doable without
a third-party service.

### Accuracy — the 25% (worst score, biggest prize)

- [ ] **Cue hygiene in `LocalRubricEngine.deriveCues`.** Question vocabulary
      ("operations", "company", "initiative", "programme") becomes cues and
      matches everything. Drop stopwords and question-framing words, require a
      minimum token length, and prefer multi-word cues.
- [ ] **Show the quotes, not the question titles, in the board's Top evidence
      column.** The payload already carries `strongest.quote`; this puts the
      product's one un-answerable asset on the first screen.
- [ ] **Counter-cues for the two proven chrome strings** — "we also welcome
      factual corrections" (SEW-EURODRIVE) and "About <company>: … global
      standard for" (Signify) — as seeded counter-cues on every question.
- [ ] **Flag the automation vendors as competitors.** Appian, Nintex, UiPath,
      Celonis, Automation Anywhere, Blue Prism, Pega, ServiceNow, SS&C and the
      rest currently sit in Priority or Nurture while selling what we sell.
- [ ] **Enrich the corpus honestly for the brief's own examples.** Fetch the real
      pages (Lufthansa Strategy 2030 / the 4,000-role automation statement, DHL
      Agentic AI + the RFQ announcement) through the existing crawler so the
      capture is genuinely what the server returned, and re-seed. No hand-written
      documents: the architecture's honesty is the product.
- [ ] **A precision number the UI actually shows.** `/api/feedback/precision`
      exists and is never called. Put measured precision on the board, and make
      the accept/reject controls feed it.

### AI/ML innovation — the 20%

- [ ] **Report the engine that actually answered.** `CorpusSeeder.classifyOne`
      and `RescoreService.classify` both stamp `localEngineId()` regardless of who
      answered, so the board cannot prove which half is which.
- [ ] **Non-destructive re-score.** `reclassify` deletes the target questions'
      signals *before* classifying; one failed model run empties the board. Stage
      the new rows and swap inside the transaction.
- [ ] **Confidence calibration.** `ScoringService` feeds the model's raw
      self-reported confidence straight into log-odds. Shrink it toward the
      engine's measured accuracy so an over-confident quote cannot dominate a
      ledger.

### Configurability — the 20%

- [ ] **Wire `icpIndustries` into the gates**, mirroring `geographyGate`, with a
      test — or delete it. A control that provably does nothing costs more than
      the feature is worth.
- [ ] **Make the derived cues visible and editable in the Studio**, via
      `GET /api/policies/derive`, shown as removable chips *before* scoring. This
      turns the 379-match failure into the demo's best twenty seconds.
- [ ] **Service-line value propositions** are empty on the live instance; seed
      them so the writer has something to say.

### Usability — the 15%

- [ ] **Bookmarking.** Star a company from the board row, the card and the
      Palette; a saved filter, an export, and a count in the rail.
- [ ] **The ask flow narrates itself.** Show the reading as you type
      ("17 companies in Romania — the board will be filtered"), adapt the button,
      and show the before/after on the board.
- [ ] **Honest tooltips.** "Probability this company is worth calling" is not
      what an uncalibrated score means. Say what it is.
- [ ] Keep `nats`, `logit` and `capped` off the default path; they are for the
      juror who asks, not the first screen.

### Business impact — the 10%

- [ ] **A cost meter.** Model calls and tokens per rebuild, and the cash saved by
      the classification cache — the cache already separates `reusedFromCache`
      from `modelCalls`.
- [ ] **A backtest panel.** Replay the queue against the stored feedback and show
      what the bands would have caught.

### Technical execution — the 10%

- [ ] Corpus ingestion is an offline script and stays one — say so in the UI
      rather than leaving a juror to discover it.
- [ ] Grow and diversify the corpus in `tools/fast_corpus.py`: career pages and
      job boards alongside news, per-company page caps, dedup by content hash.

---

## Rules for this pass

Everything must survive the two standing constraints: `pnpm run check` reports
zero errors, and every row of the board survives a 1024×768 projector — nothing
load-bearing below 11px, nothing that only appears on hover. No third-party
service beyond the two already wired (DeepSeek, HubSpot-as-recorded-intent).
