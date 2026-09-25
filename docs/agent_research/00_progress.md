# Agent Research — Progress Tracker

**Challenge:** Orange Systems — AI B2B Platform (Deeptech GigaHack 2026, Tekwill, 25–27 Sept 2026)
**Prize:** €30,000 to the winning team
**Brief:** `docs/MAIN_CHALLENGE.md` · **Hypothesis under test:** `docs/INITIAl_HYPOTHESIS.md`

## Judging weights (the scoreboard everything below optimises for)

| Criterion | Weight |
| --- | --- |
| Signal Relevance & Accuracy | 25% |
| AI/ML Innovation | 20% |
| Configurability | 20% |
| Usability & UX | 15% |
| Business Impact & Scalability | 10% |
| Technical Execution | 10% |

## Log

| Time | Step | State |
| --- | --- | --- |
| 17:10 | Located brief + hypothesis; created `docs/agent_research/` | done |
| 17:11 | Wave 1 research agents dispatched (Jev reality check, data sources, landscape, scoring, golden path) | running |
| 17:12 | Client + event intel gathered from public sources; found the judge is the client, and a prize discrepancy (brief says €30k, event site says 30k MDL) | done (`01_`) |
| 17:12 | 6th agent added: evidence-based signal taxonomy | running |
| 17:13 | **Live experiment 1**: 3 ATS APIs confirmed keyless; 2,646 postings scanned; title-vs-body precision measured at 19:1 | done (`16_`) |
| 17:13 | **Live experiment 2**: slug resolution from careers pages scored 0/8 — JS-rendered, 2 bot-blocked | done (`16_`) |
| 17:14 | Golden path design returned | done (`14_`) |
| 17:13 | **Jev verified independently** (I probed it myself); "40–400×" claim corrected to 5.4×/96%; `api.typesafe.ai` claim in the sub-agent report did NOT reproduce | done (`10_`, `41_`) |
| 17:14 | **Rubric experiment**: 4/6 → 6/6 correct on a controlled case set by adding a plain-language rubric. The KYC case — the highest-value signal — flips from wrong to right | done (`17_`) |
| 17:15 | Landscape returned and **overturned the intended pitch**: configurable signal questions already ship (Clay, HubSpot, Avina, UserGems) | done (`12_`) |
| 17:15 | **Service-scoping experiment**: same evidence, opposite verdicts per service line; insourcing → `build (1.00)` disqualifier. 4/4 | done (`18_`) |
| 17:16 | Scoring design returned: additive log-odds with derived half-life and boolean disqualifier gates | done (`13_`) |
| 17:16 | **Evaluation**: 20 real postings, 10 signals / 10 non-signals → precision 1.00, recall 1.00 — **with the bias stated loudly**: self-authored rubric, n=20, titles only | done (`19_`) |
| 17:17 | Adversarial grading agent dispatched against the rubric | running |
| 17:17 | Creative ideas written: backtest, negative-space demo, InSight KYC symbiosis, decay clock, byte-proof, blind spots, market map, rep-feedback loop | done (`21_`) |
| 17:19 | Signal taxonomy returned: 25 signals, counter-interpretations; **"hiring an RPA developer" is the weakest measured signal**, contradicting our own rubric test's framing | done (`15_`) |
| 17:20 | Data sources returned: ATS APIs keyless with real dates; **Moldova's open register — 302,954 companies, free, replaces Crunchbase and LinkedIn for the home market**; free web search is gone | done (`11_`) |
| 17:20 | **Corrected my own earlier recommendation** after testing: DuckDuckGo 202s, Startpage 303s, GDELT 429s — the search fallback for slug resolution does not exist | done (`16_`) |
| 17:21 | Synthesis written | done (`20_`) |
| 17:22 | **Measured re-score latency**: 24 ms/unit → 2,000 units fails sequentially (48.7s), passes at exactly 4 concurrent (12.2s). Also found the re-score path is **not** offline as the demo design claimed | done (`41_` V7) |
| 17:22 | **Adversarial grading returned: 40/100 now, 70/100 if executed.** Re-ran our experiments independently; found the empty Priority band, the unrunnable rehearsal test, and the non-replicating RPA result | done (`30_`) |
| 17:23 | MVP spec written, then revised with the three blockers (buy the key, reconcile the base rate, give the corpus hours) | done (`40_`) |
| 17:23 | **Corrected our own work in three files** after the adversarial replication contradicted it (`17_`, `41_`, `40_`) | done |
| 17:24 | **Classifier engine switched to DeepSeek `deepseek-flash`** on the client's instruction. Tested on the rubric cases: KYC at 0.98 ✓, 5/6 against our (partly wrong) labels. **Removes the free tier's 20,000 decisions/day cap — the plan's hardest blocker** | done (`22_`) |
| 17:25 | **Propagation gap closed**: the demo script was still carrying €1.53 and "no network calls in this path" after both were retracted elsewhere. Both corrected in `14_` | done |

## Final inventory

17 files, ~58,000 words. All requested deliverables present: results (`10_`–`19_`), validation (`41_`), new ideas (`21_`), golden path (`14_` + §5 of `21_`), adversarial grading (`30_`), MVP spec (`40_`), progress (`00_`).

## The three things to do first

1. **Reconcile the base rate** (`13_` says 25%, `15_` says 5%) and re-derive the scoring bands. If Priority comes out empty, change the bands, not the number. This is the flaw the adversarial review calls most dangerous — it sits under both the 25% and 20% criteria at once.
2. ~~Buy the $20/month classifier key~~ — **resolved**: the engine is now DeepSeek `deepseek-flash` (`22_`), a paid API at normal limits, so the 20,000 decisions/day cap no longer applies. **Instead: re-measure the accuracy claims on the new engine**, and do not feed its self-reported confidence into log-odds arithmetic — it is uncalibrated.
3. **Start the corpus crawl today.** Both documents call it the priority and neither build plan gives it an hour. It is the only item that cannot be compressed tomorrow night.

## Final state

**Adversarial score: 42/100 as the research stands, 71/100 assuming full execution.** All the delta sits in four known items — the corpus, the base rate, the quota (now resolved), and the fact that nothing is built yet.

Every number in this folder that we could check, we checked by running something. Three of our own claims were retracted after failing replication, and the retraction was propagated to the files that depended on them.
| — | Wave 3: adversarial grading | pending |
| — | Wave 4: MVP spec for a builder agent | pending |

## Headline findings so far

1. **The judge is the client.** Each challenge provider evaluates its own track and decides what to take forward — so this is a pitch to Orange Systems, not to a neutral panel. Optimise for adoption.
2. **A 19:1 noise ratio** separates title-level signal detection from description-level scanning. This is the measured case for a classifier layer rather than keywords.
3. **The strongest open-web signal for Orange Systems specifically is financial-crime/KYC hiring at fintechs** — it maps directly onto InSight KYC, the product that won them Best Service Innovation and made Moldova the group's RPA Center of Excellence.
4. **Slug resolution is the real bottleneck**, not the AI: 0/8 careers pages yielded a token without a rendered browser.
5. **A plain-language rubric is what converts a classifier from 4/6 to 6/6** on realistic signal cases, and fixes the exact case (KYC hiring) that matters most to this client. That, not model cost, is the defensible innovation.
6. **"Configurable signal questions" is table stakes, not a wedge.** Clay shipped it in May 2025; HubSpot in June 2026. The pitch must move to what nobody ships: service-scoped signal policy where disqualifiers override positives, and a falsifiable evidence ledger.

## Files

| File | Owner | Purpose |
| --- | --- | --- |
| `00_progress.md` | main | this tracker |
| `10_jev_reality_check.md` | agent | does the hypothesis's classifier exist; what really works |
| `11_data_sources.md` | agent | what data is actually reachable in 48h |
| `12_landscape.md` | agent | competitors and where the wedge is |
| `13_scoring_design.md` | agent | scoring, calibration, explainability |
| `14_golden_path_demo.md` | agent | the demo that wins the room |
| `20_synthesis.md` | main | merged findings and the decision |
| `21_new_ideas.md` | main | creative options nobody else will bring |
| `30_adversarial_grading.md` | agent + main | red-team against the rubric |
| `40_mvp_spec.md` | main | build spec for a later agent |
| `41_validation.md` | main | what we verified vs assumed |

## Open risks

- **R1** The hypothesis names a product ("Jev" by TypeSafe AI) that may not exist — if so the architecture story needs replacing before it is presented to a jury.
- **R2** Crunchbase and LinkedIn are named in the brief but both are paid/closed; the plan must not depend on them.
- **R3** 48-hour build window; anything that needs a fine-tuning run or a paid key is out of scope.
- **R4** The brief says the platform must not depend on LinkedIn scraping — a jury will test this.
