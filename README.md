# Signal Desk — Gigahack 26

A B2B buying-signal platform for Orange Systems. A sales team writes a question
in plain language; the platform re-classifies a stored corpus of public documents
against it, re-ranks companies, and shows the arithmetic behind every score.

Its identity is **refusing to recommend bad companies, with evidence**. Every
claim on screen resolves to a byte range in a document the crawler stored, and a
company that sells what Orange Systems sells is removed from the queue with its
reason rather than ranked.

## Run it locally

```sh
docker compose up --build      # board on :3000, API on :8080
docker compose down            # keep the data
docker compose down -v         # drop the volumes too
```

First boot seeds the corpus and scores it; expect roughly a minute before the
board fills.

## Run it from source

```sh
nix develop                    # or: direnv allow
docker compose up -d postgres mongo
cd api && ./gradlew bootRun    # needs JDK 25 — see AGENTS.md
cd ui  && pnpm install && pnpm run dev
```

## Where things are

| Path | What it holds |
| --- | --- |
| `api/` | Spring Boot. The pipeline, the scoring ledger, the classifier engines |
| `ui/` | SvelteKit SPA. Board, Signal Studio, Pipeline, Settings, Market |
| `tools/` | Corpus builders and the language-model overlay |
| `docs/` | The research this was built from, and the audit trail |
| `new_goals.md` | The fifty changes this pass set out to make, and where each stands |

`AGENTS.md` is the architecture document and the one to read first — the
two-store split, why evidence is extractive, how the bands are calibrated, and
what every change has to keep working. `docs/ui-architecture.md` is the same for
the interface.

## Rebuild the corpus

```sh
python3 tools/fast_corpus.py --only=dhl-group,lufthansa-group   # news sweep
python3 tools/newsroom_corpus.py --tier=empty                   # company sites
python3 tools/build_corpus.py --tier=empty --sources=job_posting,news
```

Every sweep is additive: a document is only removed when the same pass produced
a document of the same kind for the same company. A changed `corpus/raw.json` is
picked up automatically on the next boot — the seeder fingerprints the file
rather than waiting for an operator to set a flag.

`tools/newsroom_corpus.py` falls back to a headless browser for hosts that
answer 403 to a library request, behind a proxy that refuses loopback,
link-local and private addresses on every request including redirects.

## Build the language-model overlay

The local rubric engine is the recall layer and keeps the board populated
offline. The model is the precision layer: it reads a document and returns a
quote that must be found in its capture by exact containment, or the row is
struck.

```sh
export DEEPSEEK_API_KEY=...
python3 tools/model_overlay.py --dry-run          # what it would cost
python3 tools/model_overlay.py --sample=0.10      # write corpus/signals.json
python3 tools/grouped_overlay.py                  # one call per service line
```

It spends calls where they change the answer — every document sharing vocabulary
with a question, plus a random sample of the ones that do not, so the cheap
engine's miss rate is measured rather than assumed — and it resumes if
interrupted.

`tools/grouped_overlay.py` does the same work by asking once per service line
instead of once per question: 2,061 calls where question-by-question takes
12,409, covering more pairs because a line is escalated whole. `tools/overlay_report.py`
prints what the artefact cost and what the cue matcher missed, with the sample
size the rate rests on.

## Deploy

Push to `main`. The GitHub webhook triggers `Gigahack26/gigahack26` on
`jen.pistonpunk.com`, which builds both images, deploys four containers behind
Traefik, and then proves the deployment answers: it fetches the board, its
metadata and the settings profile through TLS and fails on an empty array or a
missing field. A green build means reads worked, not merely that the process
started.

Board: <https://hack.pistonpunk.com> · API: <https://hackapi.pistonpunk.com>
