# Where the data comes from, and where it goes

The short version: **Python fetches, the Java app reads.** Nothing in the
running application ever makes an HTTP request to the open web.

## The crawlers

Everything under `tools/` is run by hand. They all write the same file.

| Tool | What it fetches |
|---|---|
| `build_corpus.py` | The main builder. News, job boards (Greenhouse, Lever, Ashby, SmartRecruiters), the GLEIF registry, SEC EDGAR filings. The 578-company list is hand-typed in it. |
| `fast_corpus.py` | The breadth pass: Bing News RSS per company, article text extracted from the page. |
| `newsroom_corpus.py` | Company newsrooms, press rooms, investor relations, strategy pages. |
| `browser_fetch.py` | Headless-Chrome fallback for pages that 403 a plain request. |
| `sanitize_corpus.py` | **Drops documents that cannot be about the company they are filed under.** Rewrites both data files. |
| `attribution.py` | The shared rule behind that: is this page about this company? |
| `verify_corpus.py` | The CI gate — duplicate keys, hashes, span resolution. |
| `vendor_registry.py` | Mirrors `VendorRegistry.java`; stamps which companies are competitors. |

### Why attribution is its own step

The single most dangerous failure in this product is filing a document against
the wrong company — a supplier's case study, a competitor's press release, an
industry roundup that mentions everyone. `attribution.py` is the one rule for
"is this page about this company", `sanitize_corpus.py` applies it as a filter,
and `verify_corpus.py` fails the build if anything slipped through. A tool that
reads the web needs a rule for what it is allowed to read.

## The two data files

Both are **committed to the repository** and baked into the API jar.

| File | Holds | As shipped |
|---|---|---|
| `corpus/raw.json` | Every company, and every document fetched for it | **578 companies, 4,184 documents** |
| `corpus/signals.json` | The model's pre-computed pass — see the overlay below | **8,516 rows**, 7,755 of them "no match" |
| `corpus/people.json` | 12 hand-written people | — |

`raw.json` is 20 MB. It is in git on purpose: the demo has to run from a clean
clone with no network.

## What is stored where

Two stores, split by what the thing is for.

### MongoDB — the page, as fetched

One collection, `raw_capture`. One document per fetched page.

| Field | What |
|---|---|
| `evidenceKey` | unique — the join key back to Postgres |
| `sourceUrl`, `publisher` | where it came from |
| `contentHash` | the hash the crawler computed |
| `fetchedAt` | the observation time |
| `text` | **the whole extracted page text** |
| `metadata` | source kind, company key |

This store is the reason the product can be trusted: **a claim is only a claim
if you can open the page it came from.** The capture is never trimmed to the
part that was useful — the whole document is kept, so the sentence a rep is
shown can be read in its own context, and re-found later.

### PostgreSQL — everything with structure

17 tables. The ones that matter:

| Table | Holds |
|---|---|
| `company` | the 578, with country, region, industry, size band, competitor flag |
| `evidence` | a document's *metadata* — URL, publisher, kind, authority, dates, and `char_length`. Its text lives in Mongo. |
| `signal` | **one row per (document × question) that matched** — verdict, confidence, `span_start`, `span_end`, rationale, engine |
| `policy_question` | the questions, their rubrics, weights, themes, decay classes and cues |
| `policy_score` | the computed score per company per service line |
| `classification_cache` | every model answer, so it is never paid for twice |
| `corpus_state` | the fingerprint of the last ingest |
| `lead`, `bookmark`, `feedback` | what the humans did |
| `person`, `person_signal` | the seeded people book |
| `workspace_settings` | the single row of settings the Scoring and Qualification screens edit |

The split is the design: **documents in Mongo, meaning in Postgres.** A capture
is a blob you want to keep whole and never query. A signal is a small structured
claim you want to join, filter, count and cache. Trying to do both in one store
is how you end up either re-parsing a 20 MB blob on every request or losing the
evidence you need to prove a claim.

## What a signal actually stores

The interesting row is `signal`, because it is where the anti-fabrication rule
lives:

```
evidence_id · question_id · verdict · confidence
span_start · span_end ← character offsets into the stored capture
rationale · engine · span_verified
```

`span_start`/`span_end` are **character offsets into the capture text in MongoDB**
— not into a summary, not into a normalised form. Everything the UI shows about
a claim is `capture.text.substring(span_start, span_end)`. If those offsets are
wrong the quotation is wrong, and there is nowhere for a wrong quotation to
hide.

### How the offsets are found

- The **local engine** splits the document into three-sentence windows with one
  sentence of overlap, and a match's span is that window's range.
- The **model** returns the quote it read. `SpanLocator` then finds that quote
  in the stored text: normalise both sides (NFKC, case, whitespace, curly
  quotes), look for exact containment, then trim to a sentence, then a 90%
  token-overlap window. **If it cannot be found, the row is thrown away** rather
  than stored with a guessed position. Those throws are counted, and the count
  is on the Reading screen in Settings.

## Boot: the ingest

`CorpusSeeder` (`@Order(10)`) is the only thing that writes the corpus into the
database.

1. Seed the questions, service lines and regions — these come from Java, not the
   file (see [03-reading.md](03-reading.md)).
2. Fingerprint `raw.json ++ signals.json`. **Same fingerprint → stop.** The
   instance has nothing to do.
3. Otherwise: companies and aliases, then evidence rows and captures, then
   evict whatever dropped out of the corpus, then classify every pair, then
   score every service line, then record the new fingerprint.

Every ID is derived from the source data (`UUID.nameUUIDFromBytes`), so a
re-ingest preserves identity instead of duplicating the world.

## Two things worth knowing

- **The overlay is a file, not a table.** It is read lazily by
  `SignalOverlayStore` and consulted before either engine runs. See
  [03-reading.md](03-reading.md).
- **Re-scoring is not re-crawling.** The 12-hourly scheduled job re-scores over
  the documents already stored and fetches nothing at all. New pages arrive by
  re-running the Python and booting.
