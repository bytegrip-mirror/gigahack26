# MVP Specification — build order for the implementing agent

**Read first:** `20_synthesis.md`. **Do not read the whole directory before starting** — read `20_`, then `17_`, `18_`, `13_`, and this file. The rest is evidence for claims made here.

---

## 0. Objective

Ship, by the final showcase, a deployed platform that demonstrates one thing beyond doubt: **a sales team writes a buying-signal question in plain language, and the system re-ranks real companies against real public evidence, live, with citations — including refusing companies that look good but are not buyers.**

Non-goals: lead discovery at scale, CRM integration, auth, outreach automation beyond a draft, and any general-purpose web search (it does not exist — see `16_live_signal_experiment.md`).

## 1. What already exists — do not rebuild it

The repository is already deployed and CI'd. This is a running head start, not a fresh start:

| Layer | Already running | Reuse for |
| --- | --- | --- |
| API | Spring Boot 4.1 / Java 21, `api/` | all endpoints below |
| Stores | PostgreSQL (relational) + MongoDB (documents) | structured signals + raw captures |
| Frontend | SvelteKit static SPA, `ui/`, Traefik at `hack.pistonpunk.com` | the four screens |
| CD | Jenkins `Gigahack26/gigahack26`, ~30s warm builds, webhook on push | deploy every hour if needed |

`GET /api/status` already probes both stores; keep that as the health check. The scoring must live in the API, not the browser.

**The one-line story this allows:** *"The infrastructure was already running before we started — this weekend we built the intelligence layer."*

## 2. Data model

**PostgreSQL** — relational integrity matters here because scores must be reproducible from stored rows.

- `company` — id, name, domain, country, size_band, caem_code, source (registry/ats/news), coverage_notes
- `company_alias` — company_id, alias (registry names and trading names differ)
- `evidence` — id, company_id, **source_url**, publisher, published_at, crawled_at, content_hash, mongo_capture_id, kind (job_posting / news / registry / filing)
- `signal` — id, evidence_id, policy_question_id, label, confidence, **extracted_span_start**, **extracted_span_end**, verdict (`buy` / `build` / `none`)
- `policy_question` — id, service_line, text, instructions (the rubric), weight_tier, polarity (positive / disqualifying), active
- `score` — company_id, service_line, value, computed_at, components (jsonb)
- `feedback` — company_id, question_id, rep_verdict, created_at

**MongoDB** — `raw_capture`: the unmodified document plus crawl metadata. **This collection is the anti-fabrication mechanism**: `extracted_span_start/end` index into it, so every claim resolves to bytes the machine actually read.

## 3. API surface

```
GET  /api/companies?service_line=&sort=&filter=      ranked board
GET  /api/companies/{id}                             facts + signals + score breakdown
GET  /api/evidence/{id}/capture                      raw document (byte-range proof)
POST /api/policies                                   create/edit a signal question + rubric
POST /api/rescore                                    {service_line} -> re-score stored evidence
GET  /api/policies/preview?text=                     live "N companies match" for the Studio
POST /api/feedback                                   rep accepts/rejects -> weight adjustment
POST /api/outreach/{companyId}                       generated draft with inline citations
```

**`POST /api/rescore` is the demo.** It classifies stored evidence only — no crawling. Target ≤15s for ~2,000 evidence units, hard cap 25s.

**Two measured constraints on this path (`41_validation.md` V7):**

1. **This path needs high concurrency and a second engine.** Measured on the current primary engine (`deepseek-flash`, one text per request, no batching): 354 ms/item at 4 concurrent → 2,000 units in **707s**; 95 ms/item at 16 → 189s; **16 ms/item at 128 concurrent → 32s, with zero errors**. It scales linearly to at least 128 in flight, but even there it is **over the 25s hard cap**, and 128 concurrent requests from a laptop on venue Wi-Fi is a different proposition from this machine.
   **Recommended: hybrid.** Keep a *batched* engine (the keyless endpoint accepts ~400 texts per request) or a local model for the bulk re-score, and use `deepseek-flash` where per-item judgement matters — extraction, disqualifier reasoning, outreach. Do not plan the centrepiece around a single engine.
2. **The classifier is a remote call, so this path is NOT offline.** `14_golden_path_demo.md` claims the re-score touches no network; that is true of the *crawling* but not of the *classification*. Over conference Wi-Fi, 40 sequential batches is a gamble. **Run a local classifier for this path** — GLiNER2 (Apache-2.0, CPU) is the recommended fallback — or hold it ready behind a flag and switch if the first batch exceeds ~2s.

## 4. Pipeline

1. **Acquire** (offline, before the demo). Sources, in priority order, all verified keyless in `11_data_sources.md` and `16_live_signal_experiment.md`:
   - ATS job APIs — Greenhouse / Lever / Ashby / SmartRecruiters, from a **curated token list** (discovery is only ~45% automatable; do not build a resolver)
   - Moldova's open register (`dataset.gov.md` CKAN) — firmographics for the home market, free and official
   - Google News RSS — the only free per-company news query with real `pubDate` values
   - GDELT — only with ≥5s throttling; it 429s readily
2. **Extract.** Store the raw document in Mongo, then extract **spans**, never summaries. Extraction is extractive: the model selects text from the document, so the span is verifiable by construction.
3. **Classify.** One call per evidence unit per active question, using the label **plus the rubric** from the `policy_question` row. Engine per `22_classifier_engine.md`: **`deepseek-flash`** (one text per request, parallelise across requests — measure showed no throttling to at least 128 in flight) for judgement-bearing work, and a batched engine for bulk passes. Never feed an LLM's self-reported confidence into the log-odds arithmetic as a probability — it is uncalibrated.
4. **Score.** Additive log-odds per `13_scoring_design.md`: `logit P = logit(π₀) + Σ wᵢ`, `wᵢ = tier × source_authority × 2^(−Δt/H) × (2cᵢ−1)`, H≈90d derived from a 3–6 month buying window. Disqualifiers are **boolean gates outside the score** — a match means the company is removed with a stated reason, regardless of how high the positive score is.
5. **Serve.** Persist scores; the board reads rows. Re-score recomputes from stored evidence only.

## 5. Screens — four, plus one modal

Per `14_golden_path_demo.md`. Build in this order:

1. **Pipeline board** — rank, company, score, contribution bar, top signal chips, evidence count, newest-signal age. Filters: All / Hot / **Rejected**.
2. **Evidence card** — signal timeline with the question it answers, verbatim span highlighted, confidence, publisher, dates, and two controls: *Open source* / *Raw capture*.
3. **Signal Studio** — plain-language questions per service line, weight chip, positive/disqualifying toggle, and a **live preview panel** showing match counts as the text is edited. One screen, no wizard.
4. **Re-score diff** — a mode of the board: pinned before-column, rank arrows, the question in quotes, summary strip.
5. **Outreach modal** — draft with inline citation chips, Email/InMail toggle.

**Cut, deliberately:** charts, sparklines, contact discovery, org charts, settings wizards, CRM OAuth, tone sliders, any "AI summary" paragraph.

## 5b. Three blockers to resolve BEFORE writing code

The adversarial review (`30_adversarial_grading.md`) graded the plan **40/100 as it stands** and **70/100 if executed as specified**, with all 30 points sitting in four known items. Three are decisions, not work — make them now:

1. ~~**Buy the $20/month classifier key.**~~ **RESOLVED 17:25** — the primary engine is now DeepSeek `deepseek-flash` (`22_classifier_engine.md`), a paid API at normal production limits. The free tier's 20,000 decisions/IP/day cap no longer applies. **What replaces this task: re-measure the accuracy claims on the new engine** — every number in `17_`–`19_` came from the other one — and decide the hybrid split (batched engine for bulk, DeepSeek for judgement).
2. **Reconcile the base rate, or the Priority band is empty.** `13_scoring_design.md` fixes π₀ = 0.25; `15_signal_taxonomy.md` assumes a ~5% in-market rate. At 5%, a realistically-evidenced company needs 5–6 strong independent signals to reach Priority, and the corpus has ~5.9 evidence units per company — so **the board's top band is empty by construction and there is nothing for the re-score moment to re-rank.** Pick one base rate, re-derive the bands from it, and if the band comes out empty, change the bands rather than the number.
3. **Give the corpus hours in the build plan.** Both the demo design and the synthesis call it the single priority; neither build plan allocates it any time. It is the only work item that cannot be compressed on the last night.

Also note the coupling in §4: the taxonomy's rule that no company reaches the top on one signal class alone, with at least one non-hiring class, **cannot be satisfied by a corpus that is ~90% job postings** (the realistic composition given that Crunchbase is paywalled and general web search no longer exists). Either the corpus acquires news, registry and regulatory evidence in volume, or that rule has to be relaxed deliberately and defended.

## 6. Build order — strict

Anything not on this list is a distraction until the list is done.

| # | Task | Done when |
| --- | --- | --- |
| 1 | **Corpus: 300+ companies, 1,500+ evidence units, raw captures stored** | `SELECT count(*) FROM evidence` passes; each row has a live URL and a capture |
| 2 | **Rescore path on stored evidence** | A new question changes the board in ≤15s, zero network calls |
| 3 | **Diff overlay** | The moment is invisible without it |
| 4 | **Two live source URLs verified the morning of the demo** | Opened on the venue network, phrase findable on the page |
| 5 | **7-of-10 test** | Ten plausible juror questions, ≥7 move ≥3 rows AND change #1; run on three corpus snapshots |
| 6 | **Disqualifier path** | A company that looks perfect is rejected with a reason and does not appear in Hot |
| 7 | Scoring + confidence gate | Confidence <0.7 routes to review, never to Hot |
| 8 | Everything else | — |

## 7. Acceptance criteria

- A question typed cold into the Studio moves the board and changes the #1 company.
- Every signal on screen resolves to a URL and a byte range that highlights in the raw capture.
- The Rejected list is non-empty and every rejection states a reason.
- Re-score completes in ≤15s for the built corpus, measured three times.
- The board survives a 1024×768 projector: nothing load-bearing below 16px, nothing only on hover.
- `hack.pistonpunk.com` serves the board and `hackapi.pistonpunk.com/api/status` reports both stores up.

## 8. Fixtures and demo data

- **Curated ATS token list** checked into the repo — never resolve at demo time.
- **Pre-computed scores** for the demo service lines so the board renders instantly on a cold start.
- **A planted near-miss company** for the negative-space beat: high positive score, disqualified by an insourcing announcement, with the announcement dated and openable.
- **Two pre-opened source tabs** in the presenter's browser.

## 9. Risks and pre-agreed fallbacks

| Risk | Fallback |
| --- | --- |
| Corpus too thin → juror's question moves <3 rows | Scripted honesty beat: show the zero, show the three closest. Pre-committed in `14_` |
| Rate limiting mid-demo | Measured: no throttling to 128 concurrent on DeepSeek. The re-score path **does** hit the network (classification is a remote call — the corpus being local does not make the path offline), so hold a local model ready for the demo |
| Venue Wi-Fi dies | Everything except opening a source URL is local; the raw capture proves the claim offline |
| A classifier call is slow | The counter is real and narrated; never fake progress |

## 10. What NOT to build

LinkedIn anything (the brief forbids depending on it, and saying so scores points). Crunchbase. A slug resolver. Live crawling during the demo. Auth, users, roles. Any slide explaining the architecture — one memorised sentence instead.

## 11. Open items for the implementing agent

- `30_adversarial_grading.md` is the hostile review of this plan — **read it before writing code** and fold its findings into the build order. It may reorder priorities.
- The **Annex 1 participant reference pack** is absent; if it arrives, mirror its two worked examples exactly in the demo.
- Confirm the prize figure: the event site says 30,000 MDL for this track, the brief says €30,000.
- Do not quote the 1.00 precision figure or the 40–400× cost claim (see `41_validation.md`).
