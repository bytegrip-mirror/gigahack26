# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A B2B buying-signal platform for Orange Systems, built for Gigahack 26. A sales
team writes a question in plain language; the platform re-classifies a stored
corpus of public documents against it and re-ranks companies against it. A Spring
Boot API owns the pipeline, PostgreSQL holds the relational record, MongoDB holds
the raw captures, and a static SvelteKit SPA is the board. Jenkins builds both
images and deploys four containers behind Traefik.

The product's identity is **refusing to recommend bad companies, with evidence**.
Every claim on screen resolves to a byte range in a document the crawler stored.
The board is read by a rep, so it carries the reading and not the arithmetic: the
derivation is still computed and still exportable, and **the export is where a
juror checks it** — the screen no longer prints a weight, a log-odds total or a
probability. See "What the board shows" below for what replaced them and why.

## Commands

Local stack, one command, no database setup:

```sh
docker compose up --build          # board on :3000, API on :8080
docker compose down                # keep data
docker compose down -v             # drop the volumes too
```

The first boot seeds the corpus from `api/src/main/resources/corpus/raw.json` and
scores it — expect roughly a minute before the board fills. Set
`CORPUS_FORCE_RESEED=true` to re-seed over an existing database.

API (from `api/`):

```sh
./gradlew test                                 # 28 tests
./gradlew test --tests ScoringServiceTest      # one class
./gradlew test --tests 'ScoringServiceTest.aCompanyWithNoEvidenceScoresExactlyAtTheBaseRate'
./gradlew bootRun                              # needs Postgres and Mongo reachable
```

The build targets Java 25, so `./gradlew` fails on a JDK 21 host. Run it in the
same image CI uses:

```sh
docker run --rm -v "$PWD":/src -v gigahack26-gradle-cache:/root/.gradle -w /src \
  eclipse-temurin:25-jdk-alpine ./gradlew --no-daemon test
```

UI (from `ui/`):

```sh
pnpm run check                       # svelte-check + design lint, must be 0 errors
pnpm run build                       # static site into build/
PUBLIC_API_URL=http://localhost:8080 pnpm run build
pnpm run dev                         # :5173
```

Deploy: push to `main`. The GitHub webhook triggers `Gigahack26/gigahack26` on
`jen.pistonpunk.com`, which runs the root `Jenkinsfile`.

## Running from source

The flake composes one dev shell from three files: the root adds `just`,
`docker-client` and `docker-compose`; `api/nix/devshell.nix` adds JDK 25, Gradle
and `google-java-format`; `ui/nix/devshell.nix` adds Node 22, pnpm and prettier.

```sh
nix develop            # or: direnv allow
docker compose up -d postgres mongo     # stores only, schema created on boot
cd api && ./gradlew bootRun
cd ui  && pnpm install && pnpm run dev  # :5173
```

No environment is needed for this: the API already defaults to `localhost:5432`
and `localhost:27017`, and the UI defaults to `http://localhost:8080`. The UI
runs on `:5173` here, which `docker-compose.yml` already lists in
`CORS_ALLOWED_ORIGINS`.

Without nix, `./gradlew` needs JDK 25 — either run it in the `eclipse-temurin:25-jdk-alpine`
image as above, or install one. pnpm comes from `corepack enable`, since
`ui/package.json` pins `packageManager`; if corepack cannot fetch it, the Docker
path needs neither.

## Architecture

**Two stores, two jobs.** PostgreSQL holds `company`, `evidence`, `signal`,
`policy_question`, `policy_score` and `feedback` — the relational record that
makes a score reproducible from stored rows. MongoDB holds `raw_capture`: the
unmodified document text plus its hash. **This split is the anti-fabrication
mechanism.** A signal stores `span_start`/`span_end`, which index into the capture
in Mongo, so a quote is a slice of bytes the crawler actually fetched rather than
text a model wrote. Deleting or rewriting captures breaks every span on the board.

**Evidence is extractive by construction.** No component is ever asked to
paraphrase. The local engine returns a sentence window of the stored document; the
language-model engine returns a quote that `SpanLocator` must then find by exact
containment on normalised text, falling back to a contiguous fuzzy match at ≥0.9
token overlap, and otherwise the row is **struck** and dropped. `CorpusSeeder`
applies the same containment check to the committed overlay, so a hand-edited
`signals.json` cannot smuggle in a quote that is not in its capture.

**Scoring is additive log-odds, not a weighted sum.** `ScoringService` builds one
`LedgerRow` per signal with
`w = tier × source_authority × 2^(−Δt/H) × (2·confidence − 1)`, sums them on top of
`logit(base_rate)`, and takes one sigmoid. Three corrections live in that class and
each one has a test: rows answering the same question are rank-discounted at ρ
(`correlated evidence`), each theme is capped so a single loud topic cannot breach
the Priority line (`applyThemeCap`), and disqualifiers are **boolean gates outside
the sum** — a competitor, or a company below the ICP size floor, leaves the queue
with a stated reason while its score is still computed and shown.

**A reading only carries weight when it asserts what the question asks.**
`PolicyQuestion.magnitudeFor` zeroes the weight of a verdict that answers the
question by denying it, and `LedgerRow.asserted()` reads that off the tier weight,
which is why there is no second field to keep in step. Each engine says "the thing
is present" its own way — the cue matcher answers BUY for a positive question,
COUNTER for counter-evidence, BUILD for a disqualifier — so a `BUILD` reading on a
positive question means the company *sells* the capability, and paying it the
positive weight ranked vendors as buyers. That was measured: **238 of the 772 rows
in the shipped overlay were model verdicts of BUILD or COUNTER on positive
questions, and all of them scored positive**; the top company on the live board was
top because a `COUNTER` reading was paid +0.63. Rows that do not assert are still
stored, still shown on the card under "Read, not evidence", and contribute nothing.

**A customer profile is a named book of business, and it is data.** `icp_profile`
holds a name, the countries, the industries, the size floor and an on/off switch;
a company belongs to a profile when its own country, industry and headcount
satisfy that profile's rules, computed in `IcpProfile.covers`. A company can
belong to several, because books of business overlap — that is the point of naming
them. **The scoring thresholds are not per profile**: the base rate, the band cuts
and the rank discount stay on the single `workspace_settings` row, because they
describe how the desk scores rather than who it sells to, and one score has to stay
comparable across profiles or the board's ranking stops meaning anything when a rep
switches. Turning a profile off takes it out of the filter and off the row tags and
changes no score.

**The operator can exclude a company without a deploy.** `workspace_settings.blacklist`
holds names and domains; `Blacklist.match` compares them folded, on the whole name
with the legal suffix dropped, or on the domain, and `ScoringService.gate` runs it
before every other gate with the reason `On the blacklist — "…"`. **The list adds
to the shipped exclusions and cannot remove one of them** — the 92 competitors come
from `tools/vendor_registry.py` and are part of the corpus, so a name taken off the
blacklist is still gated by `is_competitor`. The Customer profile screen lists both,
the editable chips and a disclosure of the shipped names, because an operator who
can see only half the exclusion set will spend an afternoon on the other half.

It is a scoring input, so it travels the documented path: `ScoringProperties`, `WorkspaceSettings`,
`SettingsService.effective()`, the Customer profile screen and the
`ScoringServiceTest` literal. It is deliberately *not* an environment variable —
the settings row is the source of truth, the way it is for the voice settings, and
saving re-scores every stored document. The shipped `VendorRegistry` still gates
what it always gated; the blacklist is the operator's layer on top, not a
replacement for it.

**No shipped question removes a company on a model reading.** All three `*.insourcing`
questions are `NEGATIVE`: a company that sells automation, security operations or a
data platform of its own counts against calling them, which lowers the score and
shows as counter-evidence, rather than deleting them. Vendor exclusion runs on the
curated name and industry list in `tools/vendor_registry.py` — 92 companies, all of
them right — because a model reading of "is this company a vendor" wrong a third of
the time is worse than no gate at all. Measured on the live board: of the 18
companies removed for "building in-house", ING was removed for having 600 AI staff,
Admiral for creating a GenAI Center of Excellence, KPN for a job ad that
simultaneously fired a positive hiring signal, and VINCI and LPP because the
documents were about different companies entirely. The `DISQUALIFYING` polarity
still exists and the Studio still offers it; the shipped set does not use it.

**The trade is never the same twice.** `local.decay` is per half-life by class,
`source_authority` is per kind, and `confidence` is the engine's own — so a row's
weight is a product of what was read, where it was published, how old it is and how
sure the reader was. `counterEvidence()` now returns both the negative rows and the
rows that assert nothing, because both are reasons not to call.

**What a company is worth is shown as a shape, not a chart.** `BoardService.spark`
bins the year's readings into twelve monthly buckets of absolute contribution, and
`SignalTrace` draws them as bare bars — no axis, no points, no tooltip of its own.
A row of them is read as shape ("this one spiked last week", "this one has gone
quiet"), which is the question a rep actually has; anything more is a chart
pretending to be a row, and the numbers live in the hover card.

**The board opens on the work, not on the book.** The default view is `To call`:
worth calling and untouched. It was `All`, so a company contacted six times still
sat at rank 1 of the calling list. `To call` is the only view that reads the lead
stage; the rest are band filters and the stage lives under `Filters`.

**The board carries the reading and the export carries the arithmetic.** A calling
list is read by someone who will never open `ScoringService`, so no weight, no
log-odds total and no probability is printed on it. `⋯ → Export the evidence
ledger` still writes one row per reading with its byte range, its weight and its
model reason, which is what a juror checks with a calculator; the on-screen Ledger
tab that printed the same rows was removed, because its numbers read as noise
beside the quote they explained. What replaced them, and why each is not merely a
cosmetic swap:

- **Fit is a word and it is the only column.** `Strong` / `Good` / `Fair`,
  `Unproven`, or the `Out of the queue` tag with its reason, cut from the same
  thresholds that place the band; the probability survives in the cell's tooltip
  and in the CSV. A separate band column was redundant — the two agreed on every
  row that was not gated — and the one case where they disagreed, a company pinned
  at the Priority line by the theme cap, now says `capped · one theme` in the cell.
- **Top evidence is the question titles that fired**, strongest first and one per
  question (`reasons()`), and nothing else. The contribution and the strongest
  quote were both dropped: the quote is one click away on the scorecard, and the
  amount was only ever legible to someone already reading the ledger.
- **Themes are not a column.** They live on the scorecard with `corroborated` or
  `one theme only` beside them, which is where a reader asks the question; and the
  meter is gone, because the pool average and the Priority line marked on a bar
  explain the scoring model rather than the company.

**A document is listed by what it did.** Documents that answered a question come
first; the ones that answered none sit under *Read, nothing to report* on a sunken
background. A crawl gap and a finding must not look the same.

**The company mark is scraped, not fetched at render time.** `tools/fetch_logos.py`
walks the company list, takes the best icon the site declares, falls back to
`/favicon.ico` and then to a favicon service, and normalises every one to 32×32
PNG in `ui/static/logos/` with a `domain → file` map in `ui/src/lib/logos.json`.
The board reads that map at build time and draws a monogram where there is no
entry, so nothing is requested from a third party while the demo is on stage. 559
of the 578 companies have a mark; re-run it after adding companies to the list.

**The bands are calibrated to the corpus, and the cap enforces the top one.** The
theme cap is *derived* from the Priority line (`priorityLogit − baseLogit`), not
tuned, so whatever the threshold is, a single saturated theme lands exactly on it
and no further. With Priority at 0.55 a one-theme company therefore stops at
0.550 and sits in Nurture, which is why **every company in Priority has two
independent themes** — a property you can read straight off the board, since the
single-theme leaders are all pinned at exactly 0.550. The thresholds themselves
came from the observed score distribution over the 578-company corpus rather than
from intuition; re-derive them the same way if the corpus changes shape. All four
are environment-overridable (`SCORING_*`), so recalibrating is a restart.

**A re-score keeps every model answer it already paid for.** `SignalOverlayStore`
is read by the seeder *and* by `RescoreService`, so asking one question in the
Studio classifies that question with the configured engine and leaves the rest of
the service line's model verdicts alone. It used to re-classify the whole service
line on the local engine, which meant typing one question silently replaced every
model classification on that line — the same failure the boot path was fixed for,
still live on the demo path. The seeder's `overlayUnapplied()` now compares what
the board holds from the overlay's engine against what the overlay declares, so a
partial loss is noticed rather than only a total one.

**The two engines trade recall for precision, measurably, and both are shipped.**
The local rubric engine is the recall layer; the overlay is the precision layer.
On the shipped corpus the overlay answers 8,516 document-question pairs and returns
761 signals. Recompute both from a clone with `tools/overlay_report.py`; the
figures below come from that run and every one of them describes the two files in
`corpus/`, not a build that has since been overwritten:

- **the cue matcher fires on 1,768 pairs** and the model keeps a signal on 541 of
  them (30.6%) — so the model rejects roughly seven of every ten passages the cue
  lists accept, which is the precision half of the story;
- **the cheap engine's miss rate is 0.41%**: on 6,537 sampled pairs the cue matcher
  said nothing about, the model found a signal on 27. That is the recall half, and
  it is much smaller than the figure the panel used to print, which came from an
  artefact that no longer existed;
- **0 of the 761 quotes fail containment** against their stored capture
  (`quotesChecked` / `quotesStruck` in the overlay header), recomputed offline
  rather than carried over from the build.

Treat any specific count in this file as a measurement from one corpus, not an
invariant. `tools/grouped_overlay.py` reads a document once per service line —
2,688 calls where question-by-question takes 10,883 — and it asks any question that
can remove a company on its own, because asked together with the rest the model
reaches for the disqualifier.

**A theme is folded on the way in, because it is what the cap groups by.**
`PolicyService.normaliseTheme` trims, lowercases and hyphenates whatever is typed
into the Studio's free-text theme field, so `Efficiency`, `efficiency` and
`EFFICIENCY ` are one theme. They were not: the cap groups rows by the theme
string, so two spellings of one topic were two themes and a company could climb
past the Priority line on a single loud subject — the one thing the cap exists to
stop. Nothing in the shipped question set is spelled twice today, which is exactly
why it was worth closing before a rep typed one.

**A signal question is the unit of configuration.** `policy_question` carries the
plain-language `text`, an `instructions` rubric, a `weight_tier`, a `polarity`
(`POSITIVE`, `NEGATIVE`, `DISQUALIFYING`), a `theme`, a `decay_class` that fixes its
half-life, and `cues`/`counter_cues` for the local engine. The rubric is what makes
the system work: it lets the classifier make the business inference a senior rep
would instead of matching the word "automation". `LocalRubricEngine.deriveCues`
means a question typed cold in the Studio — with no cues configured — still scores,
but only through a phrase that says something: it refuses a phrase that opens on the
subject of the sentence ("companies hiring", "banks migrating", "firms with")
because those describe the audience rather than the signal, and it refuses a single
word that is not a term this domain uses. It used to accept any two adjacent words
whose letters added to eight, so "companies hiring RPA developers" derived the cues
"companies" and "hiring" — and one two-word cue clears the confidence floor on its
own, so that question matched 1,913 of 4,466 documents and put the whole board in
Priority. The Studio shows the derived cues before the run and marks the ones that
are too general to be a signal.

**Two classifier engines, chosen per request.** `ClassifierRouter` fronts a
`LocalRubricEngine` (deterministic, offline, ~2s over the whole corpus) and a
`DeepSeekEngine` (OpenAI-compatible HTTP, one document per call, quote verified by
containment). `POST /api/rescore` accepts an `engine` field, so the deployed demo
re-scores on the local engine by default and can escalate to the language model
when the network allows. **The demo must never depend on venue Wi-Fi** — that is
why the default is local and why the UI disables the model option when
`meta.languageModelAvailable` is false.

**The MCP server is the controllers, translated.** Spring AI's stateless
streamable-HTTP transport serves `POST /api/mcp`, inside the existing `/api/**`
CORS mapping. `ControllerToolScanner` reflects over every `@RestController` bean
and turns each public request-mapped method into one tool named
`<controller>_<method>` in snake case (`lead_push_to_hubspot`), with one
`McpToolParameter` per `@PathVariable`, `@RequestParam` or `@RequestBody` — the
body is one argument called `body`. `ControllerToolInvoker` converts the
arguments with the app's Jackson mapper, runs bean validation where the controller
declares `@Valid`, and calls the controller bean itself, so `@CacheEvict` still
fires; an error `ResponseEntity` becomes an MCP error result. A method with any
other parameter type is not exposed, and `/api/admin` is never exposed, because
those routes spend money on model calls. A new endpoint is a new tool with no
extra code, and two methods that fold to the same name fail the boot.

**The corpus ships with model-extracted signals.** `corpus/raw.json` is real
crawled documents; `corpus/signals.json`, when present, is an overlay of
language-model verdicts with their quotes. `CorpusSeeder` applies the overlay where
it exists and falls back to the local engine everywhere else, verifying every
overlay quote against its capture. Regenerate the overlay with
`POST /api/admin/signals/build` with `ADMIN_ENABLED=true` and a key configured,
or offline with `tools/model_overlay.py`. The published accuracy figure is folded
in by `tools/overlay_report.py --write`, which reads the question set from the
committed `tools/questions.json` snapshot rather than from a running service, so a
juror can recompute it from a clone. Refresh that snapshot with
`tools/model_overlay.py --write-questions` after a policy change.

**A document has to be about the company it is filed under, and that is checked.**
`tools/attribution.py` holds the rules the crawlers, the sanitiser and the CI gate
all share: a whole-token match on the name or an alias (so `nexi` no longer matches
"NexImmune"), folded so a diacritic does not hide a real document (so "Komerční
banka" is "Komercni banka"), case-sensitive for the short names that are also
given names (SEB, KONE), and an ATS board accepted only when its slug is part of
the company's own domain or the posting names the company. Attribution used to be
`if variant.lower() in text.lower()`, and the shipped corpus carried a New Zealand
yachting report under SEB, a Czech cruise-missile story and a Nigerian election
story under LPP, "Best Orange Cake Ever" under Orange, and a physics-AI startup's
job ads under VINCI. `tools/sanitize_corpus.py` applies the rules to the committed
corpus and prunes the overlay rows that pointed at what it drops;
`tools/verify_corpus.py` fails the build on any document that would be rejected, so
the shape cannot come back. It is deliberately conservative and prints what it
drops and why.

**A changed corpus reloads itself, and a boot cannot throw the overlay away.**
Both files are fingerprinted, so a deployment that ships a new `signals.json`
re-reads it — fingerprinting only the raw corpus would have kept serving the
classifications the overlay was built to replace while looking like it had
applied them. The skip path re-applies the overlay whenever the board carries
materially fewer rows from the engine the overlay names than the overlay declares,
and `POST /api/admin/corpus/reload` does the same on a running instance without a
restart.

**What a source is worth is real, not documented.** `SourceKind` carries the
authority each kind is weighed at, and the two kinds the docs used to claim and the
enum did not have — `NEWSROOM(0.9)` and `STRATEGY(0.95)` — now exist, so the 292
documents from a company's own site are weighed above a wire's rewrite of them
rather than being parsed as `NEWS`. `CorpusSeeder.parseKind` and
`authorityFor` are the two places a new kind has to be added.

**Rebuilding the corpus.** `python3 tools/fast_corpus.py` refreshes
`corpus/raw.json` from the company list in `tools/build_corpus.py`. It discovers
articles through Bing News RSS, which — unlike Google News — returns the real
publisher URL inside the redirect's `url` parameter, so the article body can be
fetched directly. Do not try to resolve Google News article links: the
`batchexecute` endpoint it needs answers 429 from this host. The sweep is
breadth-first and flushes partial output every 20 companies, so a long run never
loses everything. Every document is re-hashable and the script will not write a
document it could not actually fetch. `python3 tools/fetch_logos.py` walks the
same list for the board's company marks and is a separate step because it is
network-heavy and an icon does not change when a document does; adding a company
to the list is what makes it worth re-running.

**Regions are a two-level taxonomy, and the board filters by them.** `Region` holds
continents and the commercial regions beneath them, each with the country codes it
covers. `RegionService.countriesIn` expands either level to its countries, so
selecting "Europe" and selecting "DACH" are the same code path. Companies are
placed once, at seed time, from their country code. The selector lives in the board
toolbar and the choice travels in the session, because it is a workspace-wide
filter rather than a per-view one.

**Classifications are cached before they are made, not after.** `classification_cache`
is keyed by the document's fingerprint, the question key and the engine, and
`ClassifierRouter` consults it before any model call and writes the answer —
including "nothing here" — straight after one. A corpus pass therefore only pays
for pairs it has never seen, and a re-run over unchanged documents costs nothing.
`GET /api/admin/signals/build` reports `reusedFromCache` separately from
`modelCalls` so the saving is visible rather than assumed.

**Redis caches the expensive reads and nothing else.** The board is 578 ledgers
recomputed per request; `@Cacheable("board")` on `BoardService.board` takes it from
about 500 ms to about 20 ms. Every write that could change the answer — re-score,
settings, leads, policies, service lines, the scheduled refresh — evicts the whole
cache, and the TTL is 60 seconds, so an eviction that is ever missed expires on its
own. `CacheConfig` installs an error handler: if Redis is unreachable the request is
served uncached and logged, never failed.

**The ideal customer profile is data, not deployment config.** Geography,
industry, the size floor, each gate, the base rate and the band cuts live in a
single `workspace_settings` row and are edited on the Customer profile screen.
Saving re-scores every stored document, so the queue answers the new question
immediately. `application.yml` still supplies the defaults for a fresh database.
`ScoringService` reads the profile through `ScoringConfig` rather than the
properties bean, which is what lets the arithmetic be tested against a literal
profile.

**A lead travels with its company.** `lead` holds the stage — new, contacted,
replied, qualified, rejected — plus how many times the company has been worked,
when, through which channel, and the subject of the last draft. The board filters
by stage; the Pipeline view groups everything worked by stage and advances it
inline. The contact count moves only on a real transition, never on a re-save.
The HubSpot push records the intent and marks the lead when `HUBSPOT_TOKEN` is
absent rather than pretending to have synced.

**The outreach draft can be steered, and the sheet that steers it is the whole
window.** `DraftWriter.Brief` carries an optional `instruction` that
`OutreachController` takes from the request body (POST) or the query string
(GET), so the six preprompts on the sheet and anything typed into the box go to
the same place: `ModelDraftWriter` appends them to the user message under a line
that ranks them above its own judgement on tone, length and emphasis, without
licensing a fact the findings do not support. The template writer cannot honour
one, so the router's provenance string still ends in `no model configured` and the
sheet says the wording could not be changed rather than returning the same draft
as though it had been. The sheet is full-window and mounts through `use:portal`:
the detail pane enters with a filled `transform` animation, and a filled animation
makes its element a containing block, which sized every `position: fixed`
descendant to the pane instead of the viewport.

**Every screen is a pane, not a page.** Board, Studio, Pipeline and the customer
profile all render a topbar plus independently scrolling panes inside the fixed
shell. None of them scrolls the document.

**Config is environment-first.** Every value in `application.yml` is a
`${ENV:default}` placeholder. The UI has no runtime config: Vite inlines
`PUBLIC_API_URL` at build time (`__API_URL__` in `vite.config.ts`), which is why the
Jenkinsfile passes it as a build arg and why changing the API host needs a rebuild.

## Every change has to hold the infrastructure together

A change is not finished when the feature works — it is finished when both the
deployed path and the local path still work. The two are wired separately, so
touching one and not the other is the default failure mode.

| Change | Update |
| --- | --- |
| New environment variable | `application.yml` placeholder, `Jenkinsfile` `docker run -e`, `docker-compose.yml`, `.env.example` |
| New backing service | `docker-compose.yml` (with a healthcheck), a `Jenkinsfile` stage that waits for readiness, the `web` network |
| New API route or origin | `CorsConfig` mapping if outside `/api/**`, `CorsConfigTest`, the UI client in `src/lib/api.ts` |
| New build-time value for the SPA | `vite.config.ts` define, `ui/Dockerfile` build arg, `Jenkinsfile` `--build-arg` |
| New port or domain | Traefik labels in the `Jenkinsfile`, `docker-compose.yml` |
| New API dependency | `api/build.gradle.kts`, and check the Dockerfile's build stage still resolves it |
| New corpus field | `CorpusFiles`, `CorpusSeeder`, `tools/build_corpus.py`, and re-seed |
| New column on a settings or domain table | a plain `VARCHAR`/number column is added by `ddl-auto: update` on the live database too; an enum-backed one is not — see the row below. **On a table that already has rows the column arrives empty, so a primitive `int`/`boolean` field cannot hold it.** Boxing `contact_count` on `person` was the difference between a working board and a 500 on every read — the app boots, the tests pass, and only the deployed data has the null |
| New value on an enum a column stores | a migration on every database that already exists. Hibernate `ddl-auto: update` creates the check constraint on a fresh database and never widens it on an old one, so adding `NEWSROOM` to `SourceKind` crash-looped the deployed API with `violates check constraint "evidence_source_kind_check"` until the constraint was rewritten by hand. Fresh databases — local compose, CI — are fine; the live one is not |
| New scoring input | `ScoringProperties`, `WorkspaceSettings`, `SettingsService.effective()`, the Customer profile screen, and the `ScoringServiceTest` literal |

Verify with the gates CI runs, not just the one you touched: `./gradlew test` and
`pnpm run check`, then `docker compose up --build` for the local path, then a push
and a real request against the live host. The pipeline verifies the deployment
itself — it waits for `/api/status` to report both stores `up` and fetches the
board and the API through Traefik over TLS — so **a green build now means the app
answered**, not merely that the images built.

## Security posture, deliberately

There is **no authentication**, and that is a scoped decision rather than an
oversight: the corpus is public web pages, there are no user accounts, no tenants
and no personal data, and the jury is meant to type a question into the Studio
themselves. What follows from it, and must be respected:

- `ADMIN_ENABLED` stays **false** in the deployed container. `POST /api/admin/signals/build`
  spends real money on model calls and must never be reachable from the internet.
- `DEEPSEEK_API_KEY` is a Jenkins-side secret. The pipeline reads it from the
  environment and passes it empty when it is not configured; the app degrades to
  the local engine rather than failing.
- Anyone who can reach the API can rewrite policies and re-score. That is the
  demo. Do not put real customer data in this deployment.

## Conventions

- No comments in code. Names and structure carry the meaning.
- Commits: imperative subject, a body that says what broke or why, no
  `Co-Authored-By` trailer.
- The interface has an architecture: the shell, the spacing and type scale, the
  shared components and the action hierarchy on every screen are written down in
  `docs/ui-architecture.md`. Read it before adding a screen, a column or an
  action.
- The UI is Tailwind v4 plus bits-ui. Every control and shell piece is a
  primitive in `ui/src/lib/ui/`, the only place that imports `bits-ui` or holds a
  `<style>` block. Views compose primitives and arrange them with layout
  utilities; they never restyle one. A new look is a new `variant` on the
  primitive, not a class at the call site.
- `ui/scripts/design-lint.mjs` runs inside `pnpm run check` and fails on
  arbitrary values (`p-[3px]`), classes the theme does not define, `style="…"`,
  raw colours, run-time class strings, non-layout classes on a component, and
  `bits-ui` imports outside `lib/ui`. Do not work around it; add the token to
  `@theme` or the variant to the primitive.
- The design system is Orange's Boosted 4.4, and it lives in the component
  library at `ui/src/lib/components/orange/`: `tokens.css` holds the `@theme`
  block, `base.css` the element rules, and `components.css` the shared classes
  (`btn`, `tag`, `input`, `seg`, `menu`, `tbl` and the rest). `ui/src/app.css`
  only imports Tailwind and those three files. The `@theme` block starts with
  `--*: initial`, so the default Tailwind scale does not exist. The token values
  come from Boosted's `_variables.scss`: Orange 1 `#ff7900` for fills, Orange 2
  `#f16e00` for orange on white and the primary button, black ink, Boosted greys,
  success `#32c832` for the corroboration mark (`accent`) and danger `#cd3c14`
  for rejection and negative weights (`oxblood`). The role names did not change,
  so a view restyles by token. Orange is spent on one action per screen and on
  the score. Controls take Boosted's 2px border (`--border-width-ctl`), bold
  labels and inversion to black on hover. Nothing is rounded (`--radius: 0`), and
  there are no gradients and no shadows. Control heights stay at the desk's
  28px and 22px, below Boosted's 40px, so a board row fits a 1024×768 projector.
- A project class name must not be a Tailwind utility name. Tailwind generates its
  static utilities whatever the theme says, so a class called `grid`, `block`,
  `flex`, `border`, `hidden`, `static`, `transform` or `transition` silently
  acquires Tailwind's declarations on top of the project's — `class="grid"` on a
  `<table>` sets `display: grid` and collapses every column. The board's table is
  `tbl`, and the two modifiers that collided are `stretch` and `panel-block`.
- The cascade order is `@theme`, `@layer base`, a view's own scoped styles, then
  `@layer utilities` where Tailwind's utilities and this project's share a layer.
  A design-system class therefore beats a view's rule of equal specificity, which
  is what the interface was built on: the old `app.css` was unlayered and loaded
  last, so it won every tie. Moving a rule into a lower layer changes rendering,
  and a screenshot diff is how that is caught — reading the CSS is not enough.
- The conversion to Tailwind was verified by screenshot, not by inspection: every
  view at 1440×900 and 1024×768, before and after, pixel-diffed. Twelve of the
  twelve were byte-identical apart from seven pixels of ±1 on a rounded border.
  Hold that bar for the next restyle.
- Type is Helvetica Neue only, in two weights, 400 and 700. `font-display` and
  `font-ui` are the same Boosted stack (`HelvNeueOrange`, Helvetica Neue,
  Helvetica, Arial). The licensed Orange font is not shipped: it resolves only
  where it is installed. Labels are bold sentence case, never tracked capitals.
  There is no monospace face; numbers use tabular figures.
- The shell is fixed-height with independently scrolling panes: a rail, then a
  view that splits into list and detail. Nothing scrolls the document itself, and
  selection lives in the URL so a company stays linkable.
- Every row of the board must survive a 1024×768 projector: nothing load-bearing
  below 11px, and nothing that only appears on hover.
