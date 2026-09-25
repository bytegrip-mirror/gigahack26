# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A B2B buying-signal platform for Orange Systems, built for Gigahack 26. A sales
team writes a question in plain language; the platform re-classifies a stored
corpus of public documents against it, re-ranks companies, and shows the
arithmetic behind every score. A Spring Boot API owns the pipeline, PostgreSQL
holds the relational record, MongoDB holds the raw captures, and a static
SvelteKit SPA is the board. Jenkins builds both images and deploys four
containers behind Traefik.

The product's identity is **refusing to recommend bad companies, with evidence**.
Every claim on screen resolves to a byte range in a document the crawler stored.

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
npm run check                      # svelte-check, must be 0 errors
npm run build                      # static site into build/
PUBLIC_API_URL=http://localhost:8080 npm run build
npm run dev                        # :5173
```

Deploy: push to `main`. The GitHub webhook triggers `Gigahack26/gigahack26` on
`jen.pistonpunk.com`, which runs the root `Jenkinsfile`.

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
the sum** — a company building in-house, a competitor, or one below the ICP size
floor leaves the queue with a stated reason while its score is still computed and
shown. The card prints the whole derivation, so a juror can check it with a
calculator.

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

**The two engines trade recall for precision, measurably.** On the same thirteen
documents the local rubric engine produced 19 signals and the language model
produced 3. The local engine is the reason the board has 2,015 signals across 227
companies; the model reads a document properly and rejects most of what the cue
lists accept — it disqualified Siemens by reading Siemens' own automation content
and concluding the company builds automation rather than buys it. That is why the
engine is a per-request choice and why the seeded board is local: an LLM-only board
is honest and sparse, and the demo needs both.

**A signal question is the unit of configuration.** `policy_question` carries the
plain-language `text`, an `instructions` rubric, a `weight_tier`, a `polarity`
(`POSITIVE`, `NEGATIVE`, `DISQUALIFYING`), a `theme`, a `decay_class` that fixes its
half-life, and `cues`/`counter_cues` for the local engine. The rubric is what makes
the system work: it lets the classifier make the business inference a senior rep
would instead of matching the word "automation". `LocalRubricEngine.deriveCues`
means a question typed cold in the Studio — with no cues configured — still scores.

**Two classifier engines, chosen per request.** `ClassifierRouter` fronts a
`LocalRubricEngine` (deterministic, offline, ~2s over the whole corpus) and a
`DeepSeekEngine` (OpenAI-compatible HTTP, one document per call, quote verified by
containment). `POST /api/rescore` accepts an `engine` field, so the deployed demo
re-scores on the local engine by default and can escalate to the language model
when the network allows. **The demo must never depend on venue Wi-Fi** — that is
why the default is local and why the UI disables the model option when
`meta.languageModelAvailable` is false.

**The corpus ships with model-extracted signals.** `corpus/raw.json` is real
crawled documents; `corpus/signals.json`, when present, is an overlay of
language-model verdicts with their quotes. `CorpusSeeder` applies the overlay where
it exists and falls back to the local engine everywhere else, verifying every
overlay quote against its capture. Regenerate the overlay with
`POST /api/admin/signals/build` with `ADMIN_ENABLED=true` and a key configured.

**Rebuilding the corpus.** `python3 tools/fast_corpus.py` refreshes
`corpus/raw.json` from the company list in `tools/build_corpus.py`. It discovers
articles through Bing News RSS, which — unlike Google News — returns the real
publisher URL inside the redirect's `url` parameter, so the article body can be
fetched directly. Do not try to resolve Google News article links: the
`batchexecute` endpoint it needs answers 429 from this host. The sweep is
breadth-first and flushes partial output every 20 companies, so a long run never
loses everything. Every document is re-hashable and the script will not write a
document it could not actually fetch.

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

Verify with the gates CI runs, not just the one you touched: `./gradlew test` and
`npm run check`, then `docker compose up --build` for the local path, then a push
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
- Design tokens live in `ui/src/app.css`; stay on the Orange light palette —
  `--orange` `#ff7900` on white and warm neutrals, with the `Inter` / `IBM Plex Mono`
  pairing. There is no dark mode.
- The board must survive a 1024×768 projector: nothing load-bearing below 13px,
  and nothing that only appears on hover.
