# new_goals — fifty changes, ordered by what the rubric pays for

Written after a full re-audit of the running system on 2026-09-26: the live
board (578 companies, 4,050 captures, 2,472 signals, 16 questions), the whole
`api/` and `ui/` tree, `tools/`, `docs/agent_progress.md`, and the hostile-juror
grading in `docs/agent_research/30_adversarial_grading.md`.

The audit left the system at **52.5/100** weighted, with the losses concentrated
in three places a juror reaches in the first five minutes:

1. **The top of the board is vendors of the thing we sell.** Rank 2 is UiPath,
   rank 4 Nintex, rank 7 Celonis. All three carry `competitor: false`. The
   competitor flag catches IT *services* firms (83 of them: Accenture, Atos,
   Capgemini) and misses every *product* vendor — the exact attack the previous
   pass identified and did not close.
2. **The classification half is still a keyword matcher.** All 2,472 live
   signals are `local-rubric-v1`, and the brief's own two examples — DHL and
   Lufthansa — sit at the base rate with no evidence about automation at all.
   Their corpora are aircraft orders and earnings reports.
3. **The crawl is one source.** `sourceKind` is 72% `news` from Bing RSS.
   The brief names company websites, newsrooms, annual reports and strategy
   publications, and career pages. None of those is crawled.

Two things the audit already proved, which this plan spends:

- The DeepSeek key works and the endpoint answers in 1.4–5.6 s per document.
- **Bing News already carries the brief's own evidence** — "Lufthansa Set To Cut
  4,000 Jobs By 2030 As AI And Automation Take Over", "DHL Logistics Trend Radar
  8.0 Finds Agentic AI, Robotics and Workforce Transformation". The pipeline has
  the source; the query shapes are what is wrong.
- Headless Chrome fetches what plain requests cannot: `group.dhl.com` returns
  185 KB of press-release DOM where `curl` returns nothing, and Lufthansa's
  newsroom answers the browser where it 403s the request.

## How to read this

Each item carries the rubric criterion it buys, the files it touches, and how it
is verified. Items are ordered by `weight × size of hole`, so the first twenty
are worth more than the last thirty. Status is tracked in place.

**Global constraints, unchanged and applying to every item:** no comments in
code; `pnpm run check` at zero errors; every row survives a 1024×768 projector
with nothing load-bearing below 11px and nothing hover-only; the deployed path
and the local path both keep working; no third-party service beyond DeepSeek and
HubSpot-as-recorded-intent; every claim on screen resolves to a byte range in a
stored capture.

---

## A. Signal relevance & accuracy — 25%, currently 3/10

### A1. Flag product vendors as competitors, not just services firms
`tools/build_corpus.py` marks IT services and BPO firms. It misses the vendors
whose product *is* the service we sell. Add a `VENDOR_INDUSTRIES` rule
(`Automation Software`, `Process Mining`, `RPA`, `Software` when the name is on a
known vendor list) plus an explicit name list: UiPath, Nintex, Celonis, Appian,
Automation Anywhere, Blue Prism, Pega, ServiceNow, SS&C, Kryon, Nice, ABBYY,
Hyperscience, WorkFusion, Datamaran. Files: `tools/build_corpus.py`,
`tools/vendor_names.py`, corpus regeneration. **Verify:** live board shows zero
vendors in PRIORITY or NURTURE; `/api/companies` filtered by band lists them
under DISQUALIFIED with the competitor gate reason.

### A2. A vendor-self-description disqualifier per service line
The model prompt already says a vendor's marketing is not a buyer's signal; the
local engine has no cues for it. Add `vendor.self_description` as a
DISQUALIFYING question on each of the three service lines with cues for the
self-description patterns ("our platform", "our RPA solution", "we help
companies automate", "leading provider of"). **Verify:** a unit test asserting
that UiPath's own product copy scores BUILD and gates the company.

### A3. Show the disqualification on the row, not only in the drawer
The board row already carries `gateReason`. Print it as a `Tag` in the Band
column for DISQUALIFIED rows so a juror sees *why* without a click. Files:
`ui/src/lib/views/BoardView.svelte`. **Verify:** 1024×768 screenshot.

### A4. Query shapes that ask for the buying signal, not the company name
`tools/fast_corpus.py`'s `QUERY_SHAPES` is `{n}`, `{n} automation`,
`{n} cybersecurity`, `{n} artificial intelligence`. The brief's own Lufthansa
story is reachable by `"{n}" automation` but loses to six fresher aircraft-order
stories under `MAX_DOCS_PER_COMPANY = 6`. Rank candidates by signal-bearing
query first, recency second, and widen the shapes: efficiency programme, cost
cutting, shared services, process mining, digital transformation, workforce
restructuring, AI strategy. Files: `tools/fast_corpus.py`. **Verify:** dry-run
report showing Lufthansa's four automation/restructuring documents retrieved.

### A5. A browser fallback for WAF-blocked hosts
Plain `urllib` gets 403 from Lufthansa, 000 from DHL, and failed on 8 of 8
careers pages in the earlier research. Add `fetch_via_browser(url)` driving
`google-chrome --headless=new --dump-dom`, used when a plain fetch returns 403,
404-with-body-less, or raises. Files: `tools/browser_fetch.py`, used by
`newsroom_corpus.py` and `fast_corpus.py`. **Verify:** `group.dhl.com` press
releases land as captures with their real text.

### A6. Company newsrooms and strategy pages as a first-class source
New `tools/newsroom_corpus.py`: discover the newsroom from the homepage, read
press releases and strategy/sustainability pages, store as `SourceKind.NEWSROOM`
and `SourceKind.STRATEGY`. Add both to the enum with authority 0.9 and 0.95 —
a company's own dated statement about its own programme is stronger evidence
than a wire's paraphrase of it. Files: `tools/newsroom_corpus.py`,
`SourceKind.java`, `CorpusSeeder.parseKind`, `CorpusSeeder.authorityFor`.
**Verify:** DHL and Lufthansa carry strategy/newsroom captures.

### A7. Career pages and ATS boards for the companies that have none
51 companies have zero documents. `build_corpus.py` already speaks Greenhouse,
Lever, Ashby and SmartRecruiters; run that path for the empty set and for the
brief's examples. Files: `tools/build_corpus.py` (`--only`, `--tier=empty`).
**Verify:** zero-document count falls; `/api/status` captures rises.

### A8. Ship the model overlay — every row's engine becomes the model
`corpus/signals.json` is absent, so `CorpusSeeder` falls back to the local
engine for all 64,800 pairs. `tools/model_overlay.py` is written and measured:
1,670 cue-live pairs plus a 10% sample of the rest ≈ 8,000 calls, ~6 minutes at
24 workers. Build it, commit it, re-seed. **Verify:** `CorpusSeeder` logs
"stored N signals: M from the language model"; a sample row's `engine` reads
`deepseek-flash@deepseek`.

### A9. Measure the local engine's recall instead of assuming it
The sampled (cue-silent) documents in A8 are the instrument: they are pairs
where the cheap engine said nothing and the model was asked anyway. Report the
rate at which the model found a signal the local engine missed, on the
precision panel. **Verify:** a number on screen with its sample size.

### A10. Calibrate confidence instead of trusting it
`ScoringService` feeds the model's self-reported confidence straight into
log-odds. Shrink it toward the engine's measured accuracy:
`calibrated = 0.5 + (raw − 0.5) × k`, with `k` derived from the overlay's own
strike rate and stored in settings. Files: `ScoringService`, `ScoringProperties`,
`SettingsService.effective()`, `ScoringServiceTest`. **Verify:** the existing
literal-profile test still passes; a new test pins the shrink at the extremes.

### A11. Put measured precision on the board
`GET /api/feedback/precision` exists and `fetchPrecision()` in `api.ts` is dead
code — the UI never calls it. Wire it into a panel and make accept/reject feed
it. Files: `ui/src/lib/views/BoardView.svelte`, `Scorecard.svelte`, `api.ts`.
**Verify:** accepting and rejecting rows moves the number.

### A12. A review queue rather than a silent floor
Rows below the confidence floor are dropped. Keep them as *candidates* and show
them on a "check these" list — the brief asks for a low false-positive rate, and
showing the borderline is how you demonstrate the floor works. Files:
`LocalRubricEngine`, `ClassifierRouter`, `BoardRow`, board view. **Verify:** the
queue is non-empty and each row shows why it did not clear the floor.

---

## B. AI/ML innovation — 20%, currently 5/10

### B1. One model call per document per service line
Today the model is asked one question at a time: 16 calls per document. The
document does not change between them. Add `DocumentEngine`: one call, all of a
service line's questions in a single JSON answer, each with its own quote. That
is 3 calls per document instead of 16, it lets the model reason across questions
at once, and it is a genuine architectural claim — *"the model reads the
document once"* — rather than a vendor's batch endpoint. Files: new
`DocumentEngine.java`, `ClassifierEngine`, `CorpusSeeder`, `AdminController`,
overlay format. **Verify:** a test that a three-question answer produces three
signals with three distinct verified spans, and a measured call-count reduction.

### B2. Non-destructive re-score
`RescoreService.reclassify` deletes the target questions' signals *before*
classifying, so a failed model run empties the board mid-demo. Stage into a
staging list and swap inside the transaction. Files: `RescoreService`.
**Verify:** a test that a classification run throwing mid-way leaves the
previous signals intact.

### B3. Report the engine that actually answered, everywhere
`CorpusSeeder.readOverlay` labels overlay rows `"language-model"` while
`DeepSeekEngine.id()` is `"{model}@deepseek"`, so the board cannot prove which
half is which. Unify on the engine's own id. Files: `CorpusSeeder`,
`ClassificationCacheService.stats()` (which hardcodes two engine strings).
**Verify:** every distinct `engine` value in `signal` is a real engine id.

### B4. A cost meter
The classification cache already separates `reusedFromCache` from `modelCalls`.
Show calls, tokens, cache hits and the money saved, per rebuild and cumulative.
Files: `AdminController`, new `CostController`, `BoardView` rail or Settings.
**Verify:** the panel reports the real overlay build's own numbers.

### B5. A backtest panel
Replay the stored feedback and the decay clock: for companies the board called
90 days ago, what does the record show now. Ground truth outside the system is
the strongest accuracy evidence available and no competitor will have it. Files:
new `BacktestService`, `BacktestController`, a view. **Verify:** the panel
renders from real stored rows, with its own sample size printed.

### B6. An engine comparison view
Same corpus, same question, local versus model, side by side — the count, the
precision proxy, and the rows only one of them found. This is the honest version
of the two-tier story the earlier research told us to stop telling.
**Verify:** the panel shows 2,472 local signals against the overlay's count.

### B7. Question authoring from plain language
`AskService` already reads a sentence into facets plus a remainder. Turn the
remainder into a *draft policy question* with a derived rubric and cues, and let
the user save it in one click. Files: `AskService`, `AskController`,
`BoardView` ask panel. **Verify:** typing "companies in Romania cutting costs"
offers a saveable question.

### B8. Explain the score in words
The overlay's `reason` field is written and never shown. Render it next to the
score as the plain-language reading, separate from the arithmetic. Files:
`LedgerRow`, `Ledger.svelte`, `Scorecard.svelte`. **Verify:** every ledger row
with a model rationale shows it.

### B9. Corpus freshness and coverage in the status contract
`/api/status` reports counts but not *freshness* or *coverage* — the share of
companies with at least one document, the median age, the newest capture.
Publish them. Files: `StatusController`, `StatusResponse`. **Verify:** the
numbers match a direct query.

---

## C. Configurability — 20%, currently 7/10

### C1. Wire `icpIndustries` into a real gate
`ScoringService.gate` applies the industry filter whenever the list is
non-empty, but there is no `industryGate` boolean and `SettingsService.current()`
seeds it as `""` — so the control on the Customer profile screen provably does
nothing. Add the flag, the migration, and a test. Files: `WorkspaceSettings`,
`SettingsService`, `ScoringProperties`, `ScoringService`, `SettingsView.svelte`.
**Verify:** `ScoringServiceTest` gains a case; toggling it on the live board
moves rows.

### C2. Show the derived cues before scoring, not after
`LocalRubricEngine.deriveCues` invents the cue list for a cold question and
nothing shows it. Add `GET /api/policies/derive` and render the derived cues as
removable chips in the Studio and in the ask panel, *before* the score runs.
This turns "a juror types a question and gets 379 matches of nothing" into the
best twenty seconds of the demo. Files: `PolicyController`, `LocalRubricEngine`
(public derive), `StudioView.svelte`, `BoardView.svelte`. **Verify:** a cold
question shows its chips and the count changes when one is removed.

### C3. Studio reads its vocabularies from the API
`StudioView` hardcodes `EVENT/HIRING/STRUCTURAL` and a free-text theme field,
while `GET /api/policies/themes` and `/decay-classes` exist and are never
called. Files: `StudioView.svelte`, `api.ts`. **Verify:** the theme control
offers the corpus's real themes.

### C4. An editable counter-cue list
`counterCues` is a real column with a real effect and no editor. Add it to the
Studio with the same chip treatment as cues. Files: `StudioView.svelte`,
`PolicyRequest`, `PolicyService`. **Verify:** adding a counter-cue to a question
lowers that question's signal count on the next re-score.

### C5. Seed the service-line value propositions
`ServiceLine.valueProposition` is empty on the live instance, so
`OutreachService` falls back to a hardcoded map and the writer has nothing
specific to say. Seed all three. Files: `ServiceLineService.ensureSeeded`, a
`ServiceLineLibrary`. **Verify:** an outreach draft quotes the line's own
proposition.

### C6. An industry picker built from the corpus
The Customer profile screen takes industries as free comma text. Offer the
distinct industries actually present, with counts. Files: `SettingsView.svelte`,
`SettingsController`. **Verify:** the picker lists 30+ industries with counts
that match the board.

### C7. A scoring simulator
Settings shows threshold number inputs and band counts from `workspace.meta`.
Make it live: drag priority/nurture/watch and watch the band counts recompute
from the stored scores without saving. Files: `SettingsView.svelte`. **Verify:**
moving priority from 0.55 to 0.40 changes the Priority count on screen.

### C8. Policy import and export as a set
The Studio exports a CSV of questions; it cannot import one. A JSON round-trip
makes "here is a policy set for a new market" a thing a user can actually do.
Files: `PolicyController`, `StudioView.svelte`. **Verify:** export, delete,
import restores the same question count and cue lists.

### C9. Per-question decay preview
Half-life is a select with three options and no consequence visible. Show the
resulting horizon for each choice on the question. Files: `StudioView.svelte`,
`PolicyController`. **Verify:** switching EVENT→STRUCTURAL changes the shown
horizon.

---

## D. Usability & UX — 15%, currently 6/10

### D1. Stop remounting the board on every row click
`routes/+page.svelte` wraps `BoardView` in `{#key selected}` keyed on the `?co=`
URL parameter, and `BoardView.select()` rewrites that parameter on every row
click. So opening a company re-fetches the board, resets pagination, sorting,
filters and scroll. This is the single most expensive interaction on the screen.
Keep selection in the URL — the architecture doc is right about that — but read
it without destroying the component. Files: `routes/+page.svelte`,
`BoardView.svelte`. **Verify:** open five rows in a row; the list scroll and
`fetchedAt` do not change.

### D2. Keyboard navigation on the board
`j`/`k` or arrows to move the selection, Enter to open, Esc to close, `/` to
focus search. The board is a table of 578 rows and it is mouse-only. Files:
`BoardView.svelte`. **Verify:** a headless probe driving the keys.

### D3. The ask panel shows what changed
`RescoreResponse` already returns `Move`s and `changedTop`. Print them: which
rows moved, which leader changed, before/after. Files: `BoardView.svelte`.
**Verify:** asking a question shows a non-empty movement list.

### D4. Quote-first in the capture sheet
The company pane scrolls to prose and hides the quote. Open at the marked span
with the lead-in bounded. Files: `CaptureSheet.svelte`. **Verify:** the marked
span is in the viewport on open at 1024×768.

### D5. The counter-argument on every card
The taxonomy's Rule 8 requires the counter-interpretation next to the score.
`ScoreComputation.counterEvidence()` exists and no screen shows it. Files:
`Scorecard.svelte`. **Verify:** a company with counter rows renders them beside
the fit.

### D6. Empty, loading and error states for every pane
Audit each pane for all three. Files: all views. **Verify:** a checklist run
against a stopped API.

### D7. The 1024×768 projector pass over every new row
Nothing load-bearing below 11px, nothing hover-only. Files: `app.css`, views.
**Verify:** headless screenshots at 1024×768 for board, scorecard, studio,
pipeline, settings.

### D8. Extend the command palette
It searches companies, views, service lines and regions. Add: ask this
question, add a bookmark, jump to a saved company, switch ICP industry filter.
Files: `CommandPalette.svelte`. **Verify:** each new action is reachable and
labelled.

### D9. A coverage marker in the board row
Companies with thin or absent coverage should say so. Files: `BoardRow`,
`BoardService`, `BoardView.svelte`. **Verify:** the 51 empty companies are
visibly marked.

### D10. Export carries the citation ledger
The CSV export has the score and the top quote. Add every contributing row with
its question, span text, source URL and offsets, so the export is auditable
outside the app. Files: `export.ts`, `BoardView.svelte`. **Verify:** open the
CSV and resolve one row's URL + offsets by hand.

### D11. Bookmark count in the rail and a real Saved view
Bookmarking shipped with a filter; it needs a count where the other counts are,
and bulk save. Files: `+layout.svelte`, `BoardView.svelte`. **Verify:** starring
a row moves the rail count.

---

## E. Business impact & scalability — 10%, currently 5/10

### E1. The unit economics, corrected and on screen
The demo script's €61/month and €1.53/signal were corrected to €76.48 and €1.91
and never propagated; the denominators disagree by 3.8×. Rebuild the panel from
the system's own numbers — documents crawled, model calls, cache hits, signals —
with every assumption labelled. Files: new view or Settings panel.
**Verify:** the numbers recompute from `/api/status` and the cost meter.

### E2. The market map
Aggregate the same signals by country, industry and size to show where demand is
moving. This is the closing move that reframes the product from a rep's tool to
a planning instrument. Files: new `MarketService`, `MarketController`, a view.
**Verify:** the map's totals equal the board's.

### E3. A backtest as a business slide
E1's numbers plus B5's replay: what the queue caught, at what cost, against what
a rep costs. **Verify:** the panel's inputs are all links to real endpoints.

### E4. Scale story — a new market is data
Demonstrate that adding a market is a settings change plus a crawl, with the
service-line and region machinery already built. Files: docs + the Settings
flow. **Verify:** add a service line end to end without a code change.

---

## F. Technical execution — 10%, currently 7/10

### F1. A runtime corpus refresh
Ingestion is an offline script and the image bakes the corpus. Add a scheduled
in-app refresh that fetches and reclassifies, gated behind `ADMIN_ENABLED`, so
"real-time processing capability" is demonstrated rather than asserted. Files:
new `CorpusRefreshService`, `RefreshScheduler`, `AdminController`.
**Verify:** the endpoint adds documents and the board changes.

### F2. A richer status contract
Report engine, cache stats, corpus freshness, coverage and last build. Files:
`StatusResponse`, `StatusController`. **Verify:** the payload matches direct
queries.

### F3. Delete the dead surface
`ClassifierProperties.maxCandidatesPerDocument` (declared, referenced nowhere),
`Voice.targetWords()`, `ServiceLineService.normalise()`/`slug()`,
`PolicyService.companyList()`/`signalCounts()`, the unused `--row` and `--sp-6`
tokens, `fetchRegions()` in `api.ts`. Files: as listed. **Verify:** the build
and `pnpm run check` are green and a grep finds no callers.

### F4. Fix the standing defects
`PolicyQuestion` assigns `this.title` twice; `PolicyScore.computedMs` is always
0; `RefreshScheduler.lastCompanies` counts service lines; `ClassificationCacheService.stats()`
hardcodes engine ids. Files: as listed. **Verify:** new assertions cover each.

### F5. Tests for every new pathway
`DocumentEngine`, the calibration shrink, the industry gate, the non-destructive
re-score, the browser fetch parser, the overlay's span relocation. Files:
`api/src/test/...`, `tools/tests/`. **Verify:** `./gradlew test` counts rise and
stay green.

### F6. A CI gate for corpus and policy integrity
The pipeline verifies the app answers; it does not verify the corpus is
coherent. Add a stage: every capture has text, every signal's span resolves,
every question key is unique, no company is a flagged vendor in PRIORITY.
Files: `tools/verify_corpus.py`, `Jenkinsfile`. **Verify:** the stage fails on a
deliberately corrupted corpus and passes on the real one.

### F7. README and status tracking
The README is 14 bytes. Write the real one: what it is, how to run it, how to
rebuild the corpus, how to build the overlay. Keep this file's checkboxes
current. Files: `README.md`, `new_goals.md`. **Verify:** a clean clone followed
by the README's commands produces a working board.

---

## Order of work

Batches, each ending in a push and a verification pass:

1. **Accuracy floor** — A1, A2, A3, A5, A6, A4, A7. The top of the board stops
   recommending competitors, and the brief's own examples get real evidence.
2. **Model overlay** — A8, A9, B3, B1 where it fits. Every row's classification
   comes from a model reading the document.
3. **Configurability** — C1, C2, C3, C4, C5, C6, C7.
4. **AI/ML surface** — B2, B4, B5, B6, A10, A11, A12.
5. **UX** — D1..D11.
6. **Business and execution** — E1..E4, F1..F7.

Anything not finished keeps its checkbox unticked with a one-line note saying
what is missing and why — an absence with a reason, not a silent gap.
