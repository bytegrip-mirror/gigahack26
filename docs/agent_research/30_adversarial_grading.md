# 30 — Adversarial Grading

**Role:** hostile juror, technical. Objective: destroy the plan before the real jury does.
**Graded against:** `MAIN_CHALLENGE.md` rubric, exact weights.
**Date:** 2026-09-25. Showcase 27 Sept.

**State graded.** The folder mutated throughout this review, and two of its documents corrected themselves while I was writing. The final revision I graded, by mtime:

| File | Lines | mtime | Note |
|---|---|---|---|
| `14_golden_path_demo.md` | 284 | **17:12** | **the demo script; oldest design file; never updated after any correction** |
| `10_jev_reality_check.md` | 162 | 17:13 | |
| `12_landscape.md` | 191 | 17:15 | |
| `13_scoring_design.md` | 658 | 17:15 | still `π₀ = 0.25` |
| `19_evaluation.md` | 53 | 17:16 | |
| `15_signal_taxonomy.md` | 517 | 17:19 | still ~5% base rate |
| `16_live_signal_experiment.md` | 116 | 17:20 | |
| `17_rubric_experiment.md` | 75 | 17:21 | **retracts its own headline** |
| `41_validation.md` | 131 | 17:22 | **V7/V8 accept my measurements** |
| `20_synthesis.md` | 86 | 17:22 | **updated after the retraction, kept the retracted claim** |
| `11_data_sources.md` | 457 | 17:20 | §2/§4/§6/§7 now written and strong; §4 delivers 302,954 companies |
| `40_mvp_spec.md` | — | — | never created |

Line numbers are as of these revisions and will drift again. Each finding below states where the *contradiction* lives, which is the part that matters: the problem in this folder is not that the plan is wrong, it is that the plan's corrections land in files the pitch does not read. Everything I cite, I read at the revision above.

**Independent verification I ran** (not from the files): live probes of `classifier.dev` — batch ceiling, per-minute quota, payload cap — and a 10-trial replication of the plan's headline accuracy experiment. Everything marked **[measured]** is mine and reproducible. Two of those measurements (`17` §Replication, `41` V7/V8) have since been accepted into the plan with this document credited; where the plan now agrees, I say so and narrow the finding to what remains.

---

## 1. Fatal flaws

Ordered by how likely they are to end the submission, not by how interesting they are.

### F1 — The base rate is chosen, not measured, and the plan's own newest document makes the demo board empty

`13_scoring_design.md` fixes `π₀ = 0.25` and every score, band and worked example depends on it: `logit(0.25) = −1.099`, theme cap `W_theme_max = 2.39` nats, the Priority invariant, the staleness horizon (13 §1.2–1.3, §4). `15_signal_taxonomy.md` line 7 states the operating assumption as **"a ~5% base rate of in-market buyers."**

At π₀ = 0.05 the plan's showcase arithmetic collapses:

- `logit(0.05) = −2.944`
- Company B — the plan's Priority exemplar, "calibrated fit 0.76" (13 §1.3) — becomes `−2.944 + 2.444 = −0.500` → **0.38**. Not Priority. Not even Nurture.
- Company A becomes 0.22.
- The theme cap becomes `1.293 + 2.944 = 4.24` nats, so "one theme can reach but never exceed the Priority line" survives only in the sense that one theme now needs 4.24 nats, which the configured tiers (+1.5 for high) cannot produce from any realistic evidence set.

Consequence: a realistically-evidenced company needs roughly 5–6 strong independent signals to reach the Priority band. The demo corpus has 1,847 units over 312 companies — **5.9 units per company in total.** The Priority band is empty by construction. The board the demo re-ranks has nothing at the top to re-rank, and 14 §S1's `312 companies · 1,847 evidence units` header becomes `0 hot`.

`13` §11 anticipates the attack and answers that `π₀` is "the prevalence within our crawled pool, which we set empirically from the labelled set." That defence fails: the labelled set is 30 companies the team chose to label, not a random sample of the pool, so `π₀` is an artifact of the sampling design. If they label 15 positives and 15 negatives to fit Platt, `π₀` is 0.5; if they sample naturally, it is whatever the pool happens to be, and nothing in the plan measures it. `20_synthesis.md` §5 endorses the architecture wholesale and never mentions the conflict. `15` Rule 8 goes further and forbids reporting a probability at all ("Never report 'accuracy'… Report precision at a fixed recall") — which is a different product from the one `13` designed.

**And the 5% is no more measured than the 25%.** `15` §1.1 calls it "the single most important number" and sources it to the Ehrenberg-Bass 95-5 rule — whose own author states in the cited text that "**The 95% figure is not meant to be a precise rule**", with **no sample size published**, and whose derivation is a hand-wave ("20%/year → something like 5% in a quarter"). So the two numbers that decide every score in the product are both assumptions, five times apart, in the same folder, with no document reconciling them. A juror does not need to know which is right to score this badly; they only need to notice that the plan cannot say. This is the deepest problem in the submission because it sits under both the 25% criterion (what counts as a signal) and the 20% criterion (configurable scoring logic) at once.

**This is a reasoning failure at the centre of the 25% criterion, and a juror with a calculator finds it in ninety seconds.**

### F2 — The corpus is the acknowledged #1 risk and gets zero hours in the build plan

`14_golden_path_demo.md` line 279: the corpus is "**this is the demo; everything else is presentation.**" `20_synthesis.md` §6.3: "**A thin corpus.** The demo dies if a juror's question moves fewer than three rows. Corpus breadth is the actual build priority — not the model."

`13_scoring_design.md` §10's 32-hour build plan allocates: data model 3h, segmentation 4h, scorer 4h, containment 2h, evidence card 5h, labelling 5h, calibration 4h, config screen 4h, controls 1h. **Corpus acquisition: 0 hours.** It then names the ordered risk: "if only one thing ships, ship the **ledger card plus the 60-snippet extractor evaluation**." So the plan holds three positions at once — `20_synthesis.md` §6.3 says "**Corpus breadth is the actual build priority — not the model**"; `14` line 279 says the corpus "**is the demo**"; and the only document with hours in it allocates the corpus none and nominates the card plus evaluation as the thing to protect. `20` and `41` both carry **AS2** ("300–400 companies and ≥1,500 evidence units can be crawled before the demo") as an unverified assumption, and `41` line 121 lists its verification as a "time-boxed crawl spike" — which is not in the build plan either. Meanwhile the one build instruction the team is actually holding, `00_progress.md`'s next-wave line, is "MVP spec for a builder agent", which still does not exist.

Worse, the corpus has no verified composition:

- `16_live_signal_experiment.md` line 92 recommends the opposite of 312 companies: "**Seed a curated token list for the demo — ten to twenty companies**." A 20-company board makes the 7-of-10 test vacuous — with 20 rows, "≥3 rows move AND #1 changes" is satisfied by almost any question, and the demo looks like a 20-row spreadsheet flipping. `16` line 110 escalates this further: the curated list is "now the **only** reliable route to a company's job board, which raises its importance from convenience to **critical path**." A demo-critical artefact of ten to twenty hand-verified tokens is not a 312-company corpus, and it is a single point of failure that no plan line budgets for.
- 8 of the 10 rehearsal archetypes in `14` line 46 (expansion, cost programme, regulation, incident, M&A, partnership, funding, sustainability — all but hiring and leadership change) are news, registry or regulatory events. `11_data_sources.md` has since filled its §2 (News), §4 (registries) and §7 (traps), and §4 is genuinely strong. The news layer resolves to **one** working source — Google News RSS, free, keyless, `pubDate` to the minute — and §2 records the catch: "**The `<link>` does not resolve with curl**… A real browser resolves it — **Playwright reached the publisher URL**." So the entire news archetype set (6 of the 10) is gated on Playwright, which is **not built** (`16` §Experiment 2 calls it "a requirement"), and `15` §5 records FCA Final Notices returning 403 to plain requests for the same reason. §6 then concludes that "**the free web-search layer does not exist any more**", closing the fallback route. Of the five ingestion sources the brief names: one cut (Crunchbase), one working but Playwright-gated (Google News RSS; NewsAPI closed), one verified but throttled to 1 request/5s (GDELT), one requiring two crawls separated in time before it yields a single signal (newsroom sitemap diff, `11` §3), and one that works unassisted (ATS APIs).
- So the corpus is realistically ~90% job postings. Which F4 says may not drive a verdict. Note what the 302,954-company register does and does not solve: it is a **filter**, not a trigger. It tells you which companies fit an ICP (market, industry, geography, 44% disqualified outright) and it produces no buying signal at all. Every actual trigger in `15`'s taxonomy comes from either ATS postings (works, but hiring, and Rule-5-capped) or the news/regulatory layer (Playwright-gated). The plan's best data asset and its signal pipeline do not touch.

### F3 — The centrepiece's latency budget is off by roughly an order of magnitude, and the plan's own rehearsal protocol cannot run on the tier it priced

`14` line 45: "Target ≤15s for 1,847 units; hard cap 25s. **No network calls to third-party sources in this path — the corpus is local.**"

That sentence is false. Re-scoring 1,847 stored windows against a new label requires a network call to the classifier for every window. There is no local model in the plan. And `14` §8's Wi-Fi fallback depends on that same sentence: "**The only network-dependent step is opening the source URL.**" Both cannot be true. On the fallback path the centrepiece is dead.

Measured on the actual endpoint **[measured]** with the plan's own 200–400-token windows (288 tokens used):

| Items per request | Result |
|---|---|
| 200 (59.5k tokens) | 200 OK, 1.12 s |
| 400 (119.3k tokens) | 200 OK, 1.58 s |
| **500** | **402 `request_spending_limit`, `retryable:false`, "Split the batch"** |
| **1,200** | **413 `payload_too_large`** (1 MB body cap) |
| 700 | 429 — "3,000 fast classifications/minute per IP" |

So the free tier's batch ceiling is **~400 windows, not the 1,000 that `41_validation.md` V5 quotes as a verified fact** ("Up to 1,000 texts per request, back in about a second"). The ceiling is a *provider-spend* limit, so it scales inversely with window length — at the top of the plan's own 200–400-token range it roughly halves. A client hard-coded to the documented figure gets 402.

The arithmetic for the demo:

- New question only: 1,847 ÷ 400 = 5 requests, but the limit is 4 concurrent per IP → 2 waves → roughly 4–20 s. Survivable, just.
- The diff overlay that makes the moment legible requires recomputing the whole board — the six configured questions plus the juror's new one: 7 × 1,847 = **12,929 decisions, ≥4.3 minutes at the 3,000/min cap**, versus a 25 s hard cap and a claimed 15 s.
- The daily cap is **20,000 decisions/IP/day**. The corpus build alone (1,847 × 6 questions) is 11,082 — 55% of a day. The 7-of-10 test (`14` line 46) is 10 questions × 1,847 = 18,470 in a single run, and `14` line 283 requires it **on three corpus snapshots**: 55,410 decisions, **2.8× the daily cap** (2.8 days of quota, not 2.8× one day). The plan's own go/no-go test cannot be run once, let alone three times, on the tier `10_jev_reality_check.md` ranks #1 precisely because it is "$0 — no key, no account."

**The plan has since caught most of this, and the credit is real.** `41_validation.md` V7 (17:22) measured 24 ms/unit on a 50-unit batch, projected 4-way concurrency at **12.2 s** against 48.7 s sequential, declared concurrency 4 a "demo-critical constant, not a tuning parameter", and stated plainly that **"the 'no network calls' claim in `14_golden_path_demo.md` is wrong as written."** V8 adds the daily-cap table, concludes "**buy the $20/month Pro key before the corpus build**", and observes that the plan never pricing its own demo "is a worse signal to a jury than the limit itself." Those were my findings and they are now the plan's own. That is how a team should respond to an adversarial pass, and it moves the score (see §7).

**What is still wrong is that the corrections live in the wrong files.** `14_golden_path_demo.md` — the document the presenter rehearses from, and the oldest design file at mtime **17:12** — was never updated. It still contains:

- line 45: "**No network calls to third-party sources in this path — the corpus is local**" (V7 says this is wrong as written);
- line 248: the Wi-Fi fallback whose validity rests on that same sentence — "the **only** network-dependent step is opening the source URL";
- line 53: the scripted counter `scoring 1,847 evidence units…`;
- line 134: a scripted `New question: "…" — 11.4s · 1,847 units scored` — a point estimate for a path whose feasibility rests on hitting exactly four concurrent batches against a ceiling of four;
- line 66: the failure branch `0 of 312 companies match`.

And V7's 12.2 s projection is an extrapolation from **one** 50-unit batch to 2,000 units across four concurrent streams on conference Wi-Fi, assuming perfect linear scaling and zero retries — on an endpoint whose 429s the plan has already hit at 8 of 12 concurrent calls (`10` §3). The measurement is honest; the extrapolation is the same species of single-call reasoning that V7 and `17` line 58 were written to stop. The required local fallback (GLiNER2) is mandated by V7 and **has no benchmark attached** — a CPU-first 205M-parameter model over 1,847 windows against a 25 s cap is a design change, not a measurement, and the 12.2 s figure presumes it is never needed. Until `14` is edited, the person on stage is reading the version V7 says is wrong.

### F4 — The taxonomy's Rule 5 removes the only evidence the corpus can hold

`15_signal_taxonomy.md` Rule 5 (line 413): "**No account reaches the top of the queue on one signal class.** Require ≥2 independent signal classes, and at least one must be a **non-hiring** class." Justified, correctly, on measured grounds: job-posting growth is the weakest of eight signals at **+7%** in Bloomberry's million-purchase study (line 64), and PwC found ~70% of firms intend to run RPA internally (line 70).

But hiring is the only signal class with a verified keyless path at scale (F2), and the entire empirical base — 2,646 postings, the 19:1 ratio, the KYC finding, the n=20 evaluation — is hiring. So either:

- the corpus is job postings and almost nothing reaches the top of the queue (every row is a single-class hiring row, capped below the outreach threshold by Rule 5), or
- the corpus is strong signals and is far too small — my own probes aside, `15` §5 records **8 material cyber-incident 8-Ks since 2026-08-01** and TED tenders that are "often award notices, a competitor has already won" — nowhere near 312 companies for the demo's own stated markets (`Romania · Financial services`, `Moldova · Retail`, `14` line 100).

And `13`'s worked examples directly violate Rule 5: Company A's largest row is "Hiring RPA developer" at +1.232 nats and it is the row that carries the score. The two biggest design documents are not the same system.

### F5 — Nothing is built, and the plan does not say what to build

`40_mvp_spec.md` is listed in the tracker and has never existed. `git log` shows the last commit is the starter scaffold. Grepping every `.java`, `.ts`, `.svelte`, `.py`, `.sql` file for `classifier|rubric|evidence|signal|score` returns **zero hits** — there is no signal code, no crawler, no evidence store, no snapshot table, no re-score path, nothing. `docs/` is untracked (`?? docs/`).

The repo is a two-store pitch board: `Submission`/`SubmissionDetail` CRUD over Postgres + Mongo, a single SvelteKit page. Three consequences the plan never addresses:

1. **`CLAUDE.md` is explicit that a pitch is complete only when both the JPA row and the Mongo document land, with no transaction and no reconciliation.** Storing 1,847 evidence units with snapshot hashes, byte offsets and extractor versions on a non-transactional dual write — with no reconciliation — is the wrong substrate, and no document notices.
2. **The brief specifies React for the dashboard; the repo is SvelteKit.** Nobody has reconciled this.
3. Build hours remaining after Day 1: roughly 32 of the 48, shared with rehearsing a 5:20 script three times, on the plan's own estimate. `13` §10's 32-hour plan is already fully allocated to features with zero corpus hours (F2).

### F6 — The headline accuracy claim was retracted in one file and never removed from the three that drive the pitch

`17_rubric_experiment.md` line 18 records the RPA case at "signal 0.89 ✓ | signal 0.53 ✓" and line 24 reports **4/6 → 6/6**. I re-ran the experiment ten times **[measured]**, five per condition, on the same live endpoint:

- **Run A (labels only), 5/5 trials:** RPA dev = *not a signal* (0.03–0.22). KYC lead = *not a signal* (0.90–0.93).
- **Run B (labels + rubric), 5/5 trials:** RPA dev = *not a signal* (0.52–0.68). KYC lead = *signal* (0.97–0.98).

**`17` has since accepted this and retracted its own headline.** Its new §"Replication attempt" (added 17:22) records the RPA case as "**`not signal` in 10/10** … **Does not reproduce — and the original was probably wrong**", states that "**the '4/6 → 6/6' headline is not robust and should not be quoted**", rules that "**any single-call number in this file**" does not survive, and concludes the RPA case was **mislabelled by this file anyway** because the taxonomy independently found hiring an RPA developer is the weakest measured signal. That last point is correct and it is a better argument than mine: the classifier was right and the experiment's label was wrong. The KYC flip — the case that maps onto InSight KYC — reproduces in 5/5.

**So the research is sound and the documentation control is not.** The retraction is confined to `17`. Every document the team and the jury will actually read still carries the retracted claim:

| Document | State | What it says |
|---|---|---|
| `20_synthesis.md` line 22 | mtime **17:22** — updated in the same minute as the retraction | "correct decisions go **4/6 → 6/6**… an original, testable claim" |
| `20_synthesis.md` line 42 | same file, evidence ladder | "**Verified first-hand** \| Rubric flips 4/6 → 6/6 on realistic cases" |
| `41_validation.md` line 44 (V6) | mtime 17:22 | "demonstrated **4/6 → 6/6** on a controlled case set" — in the *Verified* section |
| `00_progress.md` line 30, line 52 | tracker | log entry and headline finding #5 both carry 4/6 → 6/6 unqualified |

`00_progress.md` line 3 calls `20_synthesis.md` "the single document to read." The single document to read promotes a retracted number to its **highest evidentiary tier** — the same ladder that demotes the n=20 precision result to "Measured, but biased." An n=6 single-call result is not a stronger evidentiary class than an n=20 one; the ladder is inverted, and it is inverted in the direction that flatters the pitch. `17` also leaves internal residue: line 40 still lists "Genuine signal → 1.00, 0.53" in the calibration section, four lines above the section proving 0.53 is not a signal.

The consequence is concrete. The pitch's stated innovation is "accuracy comes from the rubric, not the model — here is the controlled comparison." After the retraction the controlled comparison is **one** case (KYC) plus four boilerplate rejections, i.e. the mechanism is demonstrated and the *effect size* is not. That is still a defensible claim. "4/6 → 6/6" is not, and a juror who asks for the raw data gets a different answer depending on which file they are handed.

The residual scoring point: the "✓" was scored on `argmax`, not on the shipped decision rule. Under the plan's own 0.7 gate, Run B's 0.53 is not a hot lead, and in Run A the gate did **not** catch the motivating error — KYC was confidently wrong at 0.90–0.93 in 5/5 trials, above the gate. Whatever replaces the headline must state the gate's measured cost, not just the flip.

### F7 — The "undeniable moment" is deniable, and its failure branch is a public admission

`14` line 35: the moment "**cannot be faked by a team that did not build the architecture.**" But the same document's fallback ladder (§1) offers: "(1) the presenter offers two options drawn from the ten rehearsed archetypes; (2) a colleague in the second row asks one, pre-agreed; (3) the presenter types their own. **All three are indistinguishable on stage from a juror's question.**"

So the plan's own contingency is a rehearsed question, and the plan says the audience cannot tell. A team that pre-computed answers for ten archetypes produces an identical visual — and the plan has just told us so.

The failure branch is worse. If the juror's real question moves nothing, the script says the board shows `0 of 312 companies match` — an on-stage confession that coverage is thin, which is precisely what the 25% criterion punishes. The 7-of-10 test de-risks *rehearsed* archetypes only; it says nothing about a juror's actual phrasing, and the plan rehearses no latency budget for an unseen question either. **The centrepiece's outcome is decided by an uncontrolled input, and one of its two branches concedes the rubric's heaviest criterion.**

### F8 — The business case's two headline numbers come from inconsistent denominators, and the demo script still carries a number the validation file corrected

`14` lines 198–204 still say **"≈€61/month"** and **"€1.53 per verified signal."** `41_validation.md` §A1 audited exactly this, showed the components sum to **€76.48**, and instructed: "**Action:** replace €61 with €76.48 and €1.53 with €1.91." The correction was written and never propagated into the document the presenter rehearses from. The two files disagree and the wrong one is the demo script.

The deeper problem is the denominators. The slide states 4 reps × 5 h/week × 46 weeks = 920 h/year of manual research per market, and separately a manual baseline of "30 minutes of a €25/h rep" = **€12.50 per qualified lead** (`14` line 200). At 30 min per lead, 920 hours yields 1,840 qualified leads/year = 153/month. The platform's stated output is **40 qualified signals/month** (`14` line 208). **The platform is 3.8× less productive than the process it replaces, and the "€18,400 saved" is closer to €23,000 of work not done.** The two numbers cannot both be true. A juror with a calculator asks why they should pay to cut output by 74%.

### F9 — The objective function itself is in doubt

`01_client_and_event_intel.md` line 14: the event site lists this track at **30,000 MDL (~€1,500)**; the brief says **€30,000**. A 20× discrepancy, flagged on Day 1, still unconfirmed per `20_synthesis.md` §7. Either the brief is wrong and the plan has been optimising a number twenty times too large, or it is right and the point is moot — but nobody has asked, and it is one email.

### F10 — The plan violates its own kill list

`12_landscape.md` §5 entry: "**'Two-tier fast classifier + generative LLM to cut cost.'** This is an engineering optimisation, not a customer outcome… Cost per row is a Clay-credit problem; the jury does not have it." `14` §7: "**Any slide explaining the two-tier architecture**" — cut, "slides about architecture are where hackathon demos lose the room."

The golden path's one memorised on-stage sentence (line 62) is the two-tier cost story: *"That's why a new question is free, and why we can afford to crawl every hour instead of every night."* The plan wrote the rule and then wrote the script that breaks it. And the "crawl every hour" claim is itself unaffordable: hourly full classification of a 1,847-unit corpus is 44,328 decisions/day, over 2× the free daily cap and 22% of a Pro cap, for a corpus whose entire reason to exist is being re-scored on demand.

---

## 2. The eight hardest questions a juror will ask

In the voice they will be asked, with the plan's answer and whether it survives.

**Q1 — "Your scoring design uses a 25% base rate. Your taxonomy says the base rate is 5%. Which one is on the slide, and at 5% show me one company on this board that reaches your Priority band."**
*Plan's answer:* `13` §11 — `π₀` is the prevalence "within our crawled pool," set empirically from the labelled set.
*Survives?* **No.** The labelled set is a convenience sample, so `π₀` is chosen. At the taxonomy's own 5%, the plan's showcase Priority company scores 0.38 and the band is empty (F1). The two documents are both in the repo and the synthesis never reconciles them.

**Q2 — "Your accuracy claim is six cases, one call each, on an endpoint whose docs say answers are not guaranteed identical across calls. I have the results of ten runs on my laptop. Which number do you want to stand behind?"**
*Plan's answer:* `17` now has the right answer, in writing: "the '4/6 → 6/6' headline is not robust and should not be quoted", the RPA case was "probably wrong", the endpoint is non-deterministic, and the surviving claim is the KYC flip plus the boilerplate rejections. It also credits the replication and states the lesson — "we labelled a case before doing the research."
*Survives?* **Yes, if the retraction is actually carried into the deck — and right now it is not.** `20_synthesis.md` (updated 17:22, after the retraction) still headlines 4/6 → 6/6 twice, once in the evidence ladder's **Verified first-hand** tier; `41` V6 repeats it in its *Verified* section; `00_progress.md` carries it in the log and in headline finding #5. The honest answer survives the question. The documents the presenter will read do not give it. This is a ten-minute find-and-replace and it is the highest-value ten minutes available.

**Q3 — "Name one thing in this system that is not a prompt. You send a string to a classifier and sum the output."**
*Plan's answer:* the log-odds accumulation, the derived half-life, the theme cap, byte-range containment, boolean gates (`13`).
*Survives?* **Badly.** `13` line 151 concedes equation (1) "**is exactly a logistic regression**" with hand-set coefficients, and weight-of-evidence is Good (1950). The two-tier cost story is a vendor's batch endpoint. The genuinely original engineering is extractive byte-range evidence — which is an *ingestion* property, not a scoring one — and the plan buries it under the algebra. See §6.

**Q4 — "I type a signal question. You said fifteen seconds. What is your batch ceiling, what is your per-minute quota, and what does your UI do when the API returns 402 `request_spending_limit`?"**
*Plan's answer:* `41_validation.md` V7/V8 now answer this properly — 24 ms/unit, concurrency 4 as a demo-critical constant, the sequential path a 48.7 s failure, the $20 Pro key mandated before the corpus build, and the explicit admission that "no network calls" is wrong as written. `14` still says "Target ≤15s for 1,847 units; hard cap 25s. No network calls… the corpus is local", still scripts an `11.4s` result, and still files Wi-Fi under "already mitigated" on a path whose validity depends on that wrong sentence.
*Survives?* **The plan survives; the script on stage does not.** V7/V8 is genuinely good engineering measurement and it is the second thing that moves the score. What remains unanswered is the diff overlay: recomputing all seven questions to show what moved is 12,929 decisions against ~1,250 affordable in 25 s, which V8's own table shows and no screen design resolves. A juror who asks "re-score it against my question *and* show me what changed versus your six" is asking for something the quota cannot produce. 402 handling is also still unspecified anywhere, which matters because it is the one error the plan has already provoked three times.

**Q5 — "Every one of your six strongest signals needs a source your data-sources document left as an unwritten placeholder. What is in the corpus, and how much of it is job postings?"**
*Plan's answer:* a fallback to a "curated token list" of 10–20 companies (`16` line 92), and the taxonomy's TED/EDGAR screening.
*Survives?* **No.** The answer is F2 and F4: either a 20-row board (which makes the 7-of-10 test vacuous and the demo look thin) or a 312-row board that is overwhelmingly job postings, which Rule 5 forbids from driving a verdict.

**Q6 — "Your own Rule 5 says hiring may never carry a verdict. Your corpus, your only verified keyless source, and your only reproducible measurement are hiring. Which rule wins?"**
*Plan's answer:* hiring is the corroborator, not the verdict; the six top-ranked signals are non-hiring (`15` §7 objection 2).
*Survives?* **No, on the demo's own evidence.** `15` §4's top six (R1, O1, F1, X2, R3, P1) are enforcement actions, quantified programme announcements, cost+hiring joins, market entries, breach filings and tenders. None of these yielded a corpus of 312 companies in the research window, and `13`'s worked examples break the rule anyway (F4).

**Q7 — "You cut the ICP builder — the brief's first listed configurable — and your demo shows exactly one disqualifier rule. Why should I score you 20% for configurability?"**
*Plan's answer:* "Keep exactly **one** disqualifier rule so the concept is demonstrably present" (`14` §S3); the ICP is "one ICP line inside S3's preview panel" (§7).
*Survives?* **Weakly.** The single-viewport Studio and the live preview are genuinely good design, and typing a juror's sentence is the strongest possible configurability demo. But the brief enumerates four configurables under "Ideal Customer Profile criteria by market, industry, company size, geography" — and `11` §4 confirms the one firmographic source the plan found (Moldova's `dataset.gov.md`, **302,954** companies with named directors, ownership percentages, CAEM activity codes and a `Data lichidării` liquidation date; 10,159 of them live IT companies) has **no employee count and no revenue**, so size criteria cannot be met from it at all. The plan found a genuinely good source for market, industry and geography — and it still cannot satisfy the fourth ICP dimension the brief names. Cutting the ICP editor while holding a 10,159-company ICP-sizing number it never puts on a slide is the worse half of that decision.

**Q8 — "I name a company. It uses Workday. What does your platform say?"**
*Plan's answer:* `21_new_ideas.md` idea F — publish the blind spots, per-company coverage with a confidence penalty.
*Survives?* **Yes, if it ships** — this is the single best de-risking move in the folder, and it is not in the demo script. `14` §8's fallback says "we don't crawl that source. Here's what it would take — one more source adapter" — a verbal answer, not a screen. Measured reality: 0/8 careers pages resolved, 45% slug guess rate, 2 sites 403 outright (`16`). The blind-spot screen converts that from an exposure into evidence and it is not on the run-of-show.

---

## 3. Overclaims

Every one of these is a place a juror discounts the whole deck.

1. **"Verified first-hand: Rubric flips 4/6 → 6/6"** (`20` §4 line 42) while the same table demotes the n=20 result to "Measured, but biased." An n=6, single-call result on a non-deterministic endpoint is in a *weaker* evidentiary class, not a stronger one — and `17` itself retracted it at 17:22, after which `20` (also 17:22) and `41` V6 kept it. **This is now a documentation-control failure rather than a research failure, and it is the single cheapest thing in the folder to fix** (F6).
2. **The 0.89 → 0.53 cell marked "✓"** (`17` line 18). 0.53 is below the plan's own 0.7 gate, so it is a review item, not a correct positive; `17`'s own retraction section now calls it "**`not signal` in 10/10**"; and `17` line 40 still lists 0.53 under "Genuine signal" four lines above that section. `10` line 104 had it right all along.
3. **"4/6 → 6/6" as a headline** (`00` line 30 and headline finding #5, `20` lines 22 and 42, `41` V6). See 1. Note the shape of the surviving claim: after retraction the controlled comparison is **one** flipping case plus four boilerplate rejections — the mechanism is demonstrated, the effect size is not. That is still a good pitch; it is a different, smaller one.
4. **The base rate's own source disclaims it, and no sample size exists.** `15_signal_taxonomy.md` §1.1 is titled "**The base rate is brutal, and this is the single most important number**" and builds Rules 5, 6 and 8 on ~5%. Line 17 then records that the source is the Ehrenberg-Bass 95-5 rule derived as "20%/year → something like 5% in a quarter", that the author **explicitly states "The 95% figure is not meant to be a precise rule"**, and that **no sample size is published**. So the document that attacks `19` for self-authored labels at n=20 anchors its whole design on a blog figure whose author disclaims its precision. Meanwhile `13` builds every score on 0.25 (F1). Both numbers cannot be "the single most important number", and the one labelled most important is the one with no sample size.
5. **"1.00 precision / recall (n = 20)"** (`19`). The plan states the bias loudly — genuine credit — but a **balanced** 10/10 construction means prevalence is 50% in the test and ~5% in deployment. Precision computed at 50% prevalence does not transfer; at 5% prevalence with 100% recall on a 5% base rate, PPV could be anywhere. `19` says "do not quote as accuracy," but `00`'s headline finding #3 keeps the number, and `21` and `14` are built on top of it.
6. **"A 19:1 noise ratio separates title-level signal detection from description-level scanning"** (`00` line 44). It is a *hit-count* ratio (418 body-only vs 22 title), not a precision ratio — and the same document shows 10 of the 22 title hits are noise ("Operations Analyst" matched the word *operations*). Title precision is therefore 45–55%, not the ~95% the phrase implies. The correct claim — "keyword scanning over job bodies produces 19 false matches per title match; even titles are only half precise" — is *stronger* and is the one they should make.
7. **`312 companies · 1,847 evidence units · 96 sources`** (`14` lines 100, 122) is hard-coded UI copy for a corpus that does not exist and that `41_validation.md` lists as unverified (AS2). If 312 becomes 40, the header reads as a lie at the moment the jury is looking at it.
8. **"Up to 1,000 texts per request, back in about a second"** — quoted as the operating ceiling when `41` V5 was first written. **Now corrected in place**: V5 carries a correction block (17:22) recording the measured ~400-item ceiling, the 402 at 500 items, the 1 MB body cap and the per-minute and per-day quotas, and distinguishing the documented claim from the real one. Only the live probe was ever the evidence; the correction is the right one.
9. **"confidence is a usable gate" / "the low false-positive rate requirement answered with a mechanism"** (`17` line 44, and the same claim in `13`). `17` quotes the vendor's calibration as "answers below 0.5 were right 29% of the time" and omits the other half of the same sentence: **"answers at >= 0.9 were right 82% of the time."** A 0.7+ gate therefore admits ~18% errors at the top of its own published range. That is not a hypothetical: in my replication the motivating KYC error sat at **0.90–0.93**, confidently above the gate, in 5/5 trials. So the gate's published false-positive floor and its measured failure on the case the pitch is built around point the same way. The plan quotes the half of the source that flatters its low-confidence story and drops the half that undermines its high-confidence story — in a document whose thesis is that overclaiming is fatal. The endpoint also ships a `smart` tier that re-asks sub-0.7 answers of a reasoning model, i.e. an off-the-shelf solution to precisely this problem; the plan never mentions it.
10. **"Four-for-four correct"** service-scoping (`18`). n=4, single call, rubrics authored by the test author who knew the answers, and two of the four rows are the *same text* scored against two rubrics. The plan states the limitation; the headline is still "4/4."
11. **"That which cannot be faked by a team that did not build the architecture"** (`14` line 35) — refuted by the same document's fallback ladder (F7).
12. **"€1.53 per verified signal"** (`14` line 204) — corrected to €1.91 by the validation file and never propagated (F8).
13. **"we can afford to crawl every hour instead of every night"** (`14` line 62) — 44,328 decisions/day against a 20,000/day cap (F3).

Correctly killed and worth noting for balance of treatment only because it was killed: "40–400×" (`10` §1, `41` V6) and, within the session, the "1,000 texts per request" ceiling and the "no network calls" claim. Those three corrections are exactly the standard the remaining items above should have met.

---

## 4. Unfalsifiable or hand-waving content

The plan's stated standard is `14` §S2's cut list: "**'AI summary' paragraphs (a paragraph the jury can't check is a liability, not a feature)**" and `13` §6's "the explanation is a deterministic render of the ledger, not generated text." Hold the plan to it.

1. **The half-life is claimed as derived but is assumed.** `13` §4.1: "Derive the half-life; do not pick it… **Exponential decay is not assumed, it is the shape implied by a persistent state**," justified by a day-to-day persistence probability `q`. A latent two-state process with mean duration `D` does *not* generally yield exponential decay; the geometric-persistence assumption is what produces it, and it is an assumption. The formula is then used to answer the jury question "why 90 days and not 30 or 180" (`13` §11). The answer is dressed as a derivation.
2. **`W_theme_max` is "derived, not guessed"** (`13` §1.2, §9). It is derived from `p*`, `A`, `B` and `π₀` — three of which are themselves chosen placeholders (`13` §9 admits `A`/`B` are illustrative, `π₀` is unchosen). Chosen values laundered through a formula is not a derivation, and the "one theme can never breach Priority" invariant that rests on it **breaks at the taxonomy's own base rate** (F1).
3. **The precision target is redefined until it is self-referential.** `13` §3.1 defines the claim as "the probability that a company with this evidence profile is **accepted as a qualified lead by the sales team**, under our labelling protocol." There is no sales team. The labellers are the team that wrote the rubric, the questions and the code. So "the sales team accepted it" means "we accepted it," and the reliability diagram measures agreement with the authors. This is the same unfalsifiability the plan attacks in LLM explanations, relocated from prose into a probability.
4. **"Maria (Sales)" is a person who does not exist.** `14` §S3 specifies a header reading `Edited by Maria (Sales) · 12 Sep` and calls it "**a real edited-by record, not decoration**." `21` §H's calibration loop is "when Maria rejects a lead." A fabricated attribution rendered on screen, described as real. If a juror asks to meet Maria, the demo's credibility is gone. Build the record; do not pre-populate it with an invented user and call it real.
5. **The 7-of-10 test cannot fail the hypothesis.** `14` line 46: "If a question doesn't move anything, the corpus is too thin in that archetype — **fix the corpus, not the question list.**" Every failure is assigned to an external cause. A test with no failure branch that implicates the design is not a test.
6. **Every demo outcome is scripted as a win.** Board moves: the peak. Board doesn't move: "arguably as persuasive as the success case." Network dies: "this is the raw capture we crawled at 06:14." Page edited: "Expected and good." Juror's question out of scope: "having a rehearsed honest 'no' is what makes every 'yes' believable." Four branches, four wins, zero falsifiable outcomes.
7. **"The judge is the client"** (`01` §1) — inferred from an event-site description of the provider-evaluates model, not confirmed for this track, and the entire pitch is optimised for it ("optimise for adoption, not applause"). If a neutral panel with no Orange Systems stake is scoring, the calibration of the whole script is wrong.
8. **The two differentiators are asserted absences.** `12` §2.3's "nobody ships this" for authorable disqualifiers and per-service scoping rests on a documentation search. `12` itself records a counter-example to the method — HubSpot's custom-signal flow is "**third-party-reported**" and its own KB shows only fixed signals you can pause. One of the two absences the method produced already looks like a documentation gap, which is the failure mode the method cannot distinguish from a real absence.
9. **"We do not claim zero hallucinations; we claim a measured rate"** (`13` §7.2) — with rotation: 60 snippet labels, 30 company labels, five to six person-hours, and no label set in existence. The rate is currently undefined, which makes the sentence aspirational rather than measurable.
10. **Precision dressed as a funnel.** "25% start a conversation → 25% become opportunities → 20% win" (`14` §6) presented with the formula visible is the right instinct, but all three are unsourced assumptions stacked multiplicatively, and the plan's own input table marks none of them `[verify]` except the manual-hours figure.
11. **`π₀` is called empirical when it is authorial.** `13` §11 defends the 25% base rate as "set empirically from the labelled set." A quantity can only be estimated empirically from a random sample of the population it describes; the labelled set is 30 companies the team chose to label, and if the negatives are drawn to fit a calibration curve then `π₀` is an artefact of the labelling design (0.5 by construction for a balanced set). Nothing in the plan measures the prevalence of qualified leads in the crawled pool. So the constant that sets every score, every band and the staleness horizon is a choice, presented as a measurement — while `15` line 17 supplies a different number, from a source that disclaims precision and publishes no sample size (F1, §3 item 4).

---

## 5. What is missing entirely

Brief line by line against the plan.

**Solution scope — configurability**
- ✅ Custom signal questions per service.
- ✅ Signal weights (high/medium/low) as the configuration vocabulary (`13` line 145).
- ⚠️ Negative signals / disqualification rules — **one rule in the demo**, by explicit design decision (`14` §S3). A policy engine demonstrated with a single boolean is a checkbox.
- ⚠️ Scoring logic for prioritisation — designed in detail; contradicting numbers between `13` and `15`; nothing built.
- ❌ **ICP criteria by market, industry, size, geography** — the ICP builder is on the cut list. And `11` §4 records that the one firmographic source found (Moldova's `dataset.gov.md`, 302,954 companies; 133,506 — **44%** — carrying a `Data lichidării` date) has **no employee count and no revenue**, so size-band ICP criteria are unmeetable from any source the plan verified. The register does supply the other three dimensions, and the plan never uses its strongest ICP fact on stage: **10,159 live companies with IT/software CAEM codes in the home market.**

**Data ingestion**
- ❌ Crunchbase — cut (justified).
- ⚠️ Company websites / newsrooms / annual reports — newsroom path needs two crawls separated in time before it yields one signal (`11` §3); annual reports "deprioritise"; IR probes returned connection failures.
- ⚠️ **Google News / NewsAPI / GDELT** — `11` §2 is now written and the verdict is thin: Google News RSS is "the best free news source here" (free, keyless, `pubDate` to the minute) but its `<link>` does not resolve without a real browser, so this path is **gated on Playwright, which is not built**. GDELT is verified but throttled to 1 request / 5 s. `11` §6 states flatly that "the free web-search layer does not exist any more." Six of `14`'s ten rehearsal archetypes depend on this layer.
- ✅ Career pages / job boards — verified, strongly.
- ✅ LinkedIn — correctly excluded, with the honesty beat planned.

**AI processing & scoring**
- ❌ **"Large-scale data extraction and normalization from heterogeneous web sources."** There is no normalization design anywhere: no canonical schema, no entity resolution, no same-name-entity resolver. `13` §8.2 lists "article was about a same-named entity" as a false-positive root cause and `15` Rule 7.7 flags subsidiary-vs-parent, but neither document designs a mechanism. This is a named brief requirement and the biggest unaddressed engineering task in the plan.
- ✅ Dynamic prospect scoring model.
- ✅ Signal detection: incidents, leadership changes, compliance events, tech stack.

**Technical stack**
- ❌ Playwright — identified as "a requirement" (`16` §Experiment 2) and not built; and `15` §5 records FCA Final Notices returning 403 to plain requests, which drops R1's detectability from 4 to 3 without it.
- ⚠️ Storage — the starter's Postgres + Mongo dual write, no transaction, no reconciliation (`CLAUDE.md`), against a plan that needs snapshot hashes, byte offsets and extractor versions per row.
- ❌ React — the brief specifies it; the repo is SvelteKit. Unreconciled.
- ⚠️ Outreach module (optional) — designed as a modal, demoted to "no rubric weight" (`12` §5), yet 40 drafts × €0.40 = **€16.00 of the €76.48 cost model, 21% of the unit economics** for a feature declared valueless.
- ⚠️ HubSpot hooks — a stub, correctly never clicked.

**Evaluation** — `13` §8.1's protocol (60 snippets, 30 companies, Wilson intervals, PR curve, reliability diagram, ablation, negative controls, null-company test) is genuinely excellent design. **None of it has been run.** Zero labels exist. The 20-title set was built by the rubric's author from cases they chose.

**Screens** — `14` names five surfaces (S1–S5). All five are specified in useful detail and none is built. Missing from the run-of-show: `21` idea F's blind-spot/coverage screen, which `14` §8 answers only verbally; and no screen shows the taxonomy's headline Rule 8 requirement — "**Display the counter-interpretation on every prospect card, next to the score**" (`15` line 440). `14` §S2's right rail is score breakdown plus the disqualifier check. The newest and best-evidenced document's single highest-leverage UI decision is absent from the demo design.

**Process**
- ❌ Annex 1 Participant Reference Pack — referenced by the brief, absent from the repo, flagged on Day 1 (`01` §4), still "worth requesting" on Day 1 (`20` §7). It contains the client's actual manual process and two worked examples. The team has not asked.
- ❌ `40_mvp_spec.md` — never written. There is no build spec, which is consistent with F2's zero corpus hours.
- ❌ No file records the actual build stack (language, framework, hosting) for the new product, and `docs/` is not committed.
- ⚠️ `15_signal_taxonomy.md` is not in `00_progress.md`'s file table, so the folder's own index no longer describes its contents.

---

## 6. The strongest attack on the recommended architecture

**Thesis: the recommended architecture is prompt engineering with a probability-shaped number on top, and every "differentiator" reduces to a string in the same JSON payload.**

**The attack, in the order a technical juror builds it.**

**1. The differentiators are payload fields.** `18`'s headline results come from one call with `dimensions: {automation: {...}, cybersecurity: {...}}`. Per-service polarity is a second key. The plain-language rubric is the `instructions` field — documented for the `dimensions` form, which `17` §"Honest limitations" admits. Zero-shot taxonomy is the `labels` array. The insourcing disqualifier is a third label, `build`. So the thing `12` §2.3 identifies as what "**nobody ships**" is, mechanically, **one more string in a request body to a third-party API that launched ten days before this hackathon and whose documentation already supports it.** A Clay or HubSpot engineer ships it in a sprint. "Nobody ships it" is not the same claim as "it is hard," and the plan never distinguishes them.

**2. The evidence for the absence is the weakest available kind.** `12` §2.3's method is a documentation search across vendor docs and review sites, and its own text records that the method already produced one probable false absence (HubSpot's custom signals, "third-party-reported"). Its central absence claim — no product lets a sales team author a disqualifier that overrides a positive score — is therefore an argument from unread documentation. If a juror works in GTM tooling, this collapses in one sentence, and it is the load-bearing claim under 45% of the rubric.

**3. The scoring model is a rule engine wearing a probability.** `13` line 151 concedes it: "Equation (1) **is exactly a logistic regression** on sparse evidence features, with the coefficients set by the sales team instead of by a fitter." The tier weights are "**placeholders from the sales team's priors**" (line 493) — and there is no sales team, so they are the authors' priors. The source-authority table, the half-life table and `ρ` are all hand-set. Weight-of-evidence accumulation is Good, *Probability and the Weighting of Evidence*, **1950**. Calling a 76-year-old method with hand-tuned constants "AI/ML Innovation (20%)" is precisely the move a technical panel exists to catch. The plan's own §5 kill list contains the sentence "**'We use AI to score leads.'** … It is a category entry requirement, worth zero points" — and then designs exactly that.

**4. The calibration proves nothing at this n and the plan says so.** Platt scaling on 20 companies with ~10 positive events fits two parameters — an intercept and a slope. `13` §11's own "single weakest part" answer concedes the company-level interval is roughly [0.49, 0.94], "too wide to distinguish a good system from a mediocre one." So the statistical machinery's only honest output is that it cannot distinguish anything. The rebuttal — "the 60-snippet extractor evaluation is the load-bearing evidence" — is an evaluation of a *vendor's classification endpoint*, not of the architecture.

**5. The architecture's most impressive demonstration is a vendor's batch endpoint.** The peak moment's physics are entirely `classifier.dev`: 1,847 stored windows classified against a new label in seconds, free, keyless. That is not a property of anything the team built; it is the third-party service's `inputs` array. If the service is down, rate-limited, or changes its free tier during the hackathon weekend — a six-week-old, effectively solo-maintained product with no SLA, which `10` §4 admits — the innovation story has **no evidence at all**. `41` V7 now mandates a local fallback (GLiNER2, `pip install gliner2`) as a "required design change", which is the right call; but it is a mandate with **no benchmark attached**, and V7's headline 12.2 s figure presumes the local model is never needed. A CPU-first 205M-parameter model over 1,847 windows against a 25 s cap is a design change, not a measurement, and until someone times it the fallback is a belief — on the one path the entire demo depends on.

**6. The plan's own research says this pitch loses, and the pitch says it anyway.** `12` §5: the two-tier cost story "is an engineering optimisation, not a customer outcome… the jury does not have it." `14` §7 cuts "any slide explaining the two-tier architecture." `14` line 62 is then a memorised on-stage sentence about the two-tier architecture and the hourly crawl. The team researched its own failure mode and scripted it.

**7. What survives the attack.** Two things, and the plan under-sells both:

- **Extractive evidence by construction.** Segmenting a stored document deterministically and returning a byte range rather than generated prose makes fabricated citation structurally impossible, and printing character offsets lets a juror Ctrl-F the live page. That is a real engineering property, not a prompt. It is worth more than the log-odds algebra and it is buried under it.
- **Disqualifiers as boolean gates outside the score.** "We may not sell here" is not a number, and modelling it as one lets positive evidence override a hard rule. The reasoning in `13` §5.1 is correct and the distinction is genuinely under-shipped. It is currently demonstrated with **one** rule.

**The one-sentence version a juror will use:** *"You've built a configuration UI over a free classification API, summed the labels with weights you chose, and called the sum a probability. The one genuinely novel thing here is that your citations are byte ranges instead of sentences — and you spent the pitch explaining the sum."*

---

## 7. Score

### As it stands today (research dossier, no product, ~48 hours left)

| Criterion | Weight | Score | One-sentence justification |
| --- | --- | --- | --- |
| Signal Relevance & Accuracy | 25% | **13** | The 19:1 measurement, the KYC/financial-crime finding, the counter-interpretation discipline in `15` and the retraction of a flattering headline under replication are better research than this track will see — but the base rate is chosen rather than measured and empties the Priority band at the plan's own 5%, the retraction has not reached the three documents the jury will read, and not one label exists against the corpus that will be demoed. |
| AI/ML Innovation | 20% | **8** | The extractive byte-range design and the gate-outside-the-score semantics are real, but the shipped differentiators reduce to `instructions` and `labels` fields on a third-party API, and the scoring model is a hand-coefficient logistic regression the plan itself calls exactly that. |
| Configurability | 20% | **8** | The single-viewport Studio with a live preview and the re-score-under-this-config measurement are well designed; nothing is built, the ICP editor is cut, and the disqualifier policy — the claimed wedge — is demonstrated with exactly one rule. |
| Usability & UX | 15% | **6** | Four screens with real cut lists, projector constraints and no hover-only information is the dossier's strongest section relative to its weight, but no screen exists and the newest document's counter-interpretation-on-every-card requirement is not in the demo design. |
| Business Impact & Scalability | 10% | **3** | The formula-visible sensitivity table is the right instrument, but the two headline numbers come from denominators that differ by 3.8×, the demo script still carries €1.53 after the validation file corrected it to €1.91, and every input beyond the manual-hours figure is unsourced. |
| Technical Execution | 10% | **4** | A deployed starter exists (CI, two stores, live host, 13 tests), and V7/V8's concurrency-4 measurement and mandated local fallback are real engineering work — but there is no signal code, no crawler, no evidence store, no snapshot storage, no MVP spec, a non-transactional dual write as the only persistence, and a framework mismatch against the brief. |
| **Total** | 100% | **42 / 100** | Strong research that corrects itself, no product, and a set of contradictions that survive only because the corrections were written into the files nobody will read on stage. |

### Assuming the team executes everything already specified

| Criterion | Weight | Score | One-sentence justification |
| --- | --- | --- | --- |
| Signal Relevance & Accuracy | 25% | **18** | If the 60-snippet and 30-company label sets are actually collected and the byte-range card ships, this is the best-evidenced precision story in the room; it loses the top marks to the unreconciled base rate, a surviving claim that is one flipping case rather than six, and a 0.7 gate whose measured cost is 20–50% recall against an unmeasured precision benefit. |
| AI/ML Innovation | 20% | **13** | Above hackathon average for the extractive-ledger-plus-gate combination, capped by the fact that a technical jury will correctly identify the core as prompt engineering on a vendor endpoint with hand-set coefficients. |
| Configurability | 20% | **15** | A juror's own sentence typed live, per-service policy over shared evidence, visible config provenance and a config screen that re-scores the labelled set before saving is a genuinely strong answer to this criterion — held back by a cut ICP editor and a single disqualifier rule. |
| Usability & UX | 15% | **12** | The five surfaces are designed for a sales team rather than for a jury, the anti-fake moves (open the real page, Cmd-F the phrase, leave the URL up) are the best UX thinking in the folder, and the only real deduction is that the blind-spot screen from `21` is not in the show. |
| Business Impact & Scalability | 10% | **6** | With the arithmetic corrected, the denominators reconciled and the sensitivity table presented as designed, this is a solid assumption-labelled case — not a proven one, and the scale story rests on a corpus that does not yet exist. |
| Technical Execution | 10% | **7** | Shipping the corpus, snapshot storage, containment verification and a re-score inside the latency budget, with concurrency pinned at 4 and a funded key, would be real engineering; realistically the corpus and the snapshot layer are the two that slip. |
| **Total** | 100% | **71 / 100** | A contender for the track — contingent on fixing the base-rate conflict, propagating the retractions into `14`/`20`/`00`, funding the classifier, and actually building the corpus, which the current build plan gives zero hours. |

**Delta: 29 points, and nearly all of it sits in five items the plan already knows about** — the corpus (F2), the base rate (F1), the quota and latency (F3), the unpropagated retractions (F6), and the fact that nothing is built (F5). Three of those five are pure documentation edits that take an afternoon. The corpus is the work, and it is the only one the plan calls its own top priority while allocating it no hours.

**If I could change three things before tomorrow morning, in this order:**

1. **Reconcile `13` and `15` on `π₀` and re-derive the bands.** If the Priority band comes out empty under `15`'s base rate, change the *bands*, not the base rate — and say on stage that the base rate is an assumption with a named source, not a measurement. This is the only finding that makes the demo's own scores wrong rather than merely thin (F1).
2. **Propagate the retractions.** Find-and-replace "4/6 → 6/6" out of `20` (lines 22, 42), `41` V6 and `00` (line 30, finding #5), and fix `14`'s "no network calls" claim and its €61/€1.53 figures. Those four documents are what the presenter reads; `17` and `41` are where the corrections live. An afternoon, and it removes three of the five items in the 29-point delta (F6, F3, F8).
3. **Put the corpus crawl spike on the clock today, with Playwright, and count the tokens.** Everything else in this folder is downstream of whether 312 companies with dense non-hiring evidence exist by Saturday — and the one instrument that unlocks 6 of the 10 archetypes is the one nobody has built (F2, F4).

---

## Appendix — measurements I ran, for reproduction

All against `https://classifier.dev/v1/classify` on 2026-09-25, from this machine, no key.

**Batch ceiling (288-token windows, one dimension with `instructions`):**

| Items | HTTP | Time | Notes |
|---|---|---|---|
| 10 | 200 | 1.10 s | 3,168 input tokens |
| 50 | 200 | 0.65 s | 14,888 tokens |
| 200 | 200 | 1.12 s | 59,554 tokens |
| 300 | 200 | 1.44 s | 89,312 tokens |
| 400 | 200 | 1.58 s | 119,328 tokens |
| 500 | **402** | 11.48 s | `request_spending_limit`, `retryable:false`, "Split the batch" |
| 700 | **429** | 0.99 s | "3,000 fast classifications/minute per IP" |
| 1,200 | **413** | 1.02 s | "maximum request body is 1 MB" |

**Rubric replication, five trials per condition, both cases, identical payloads** (`17_rubric_experiment.md` cases 1 and 2):

| Trial | Run A: RPA dev | Run A: KYC lead | Run B: RPA dev | Run B: KYC lead |
|---|---|---|---|---|
| 0 | not a signal 0.17 | not a signal 0.90 | not a signal 0.60 | signal 0.98 |
| 1 | not a signal 0.10 | not a signal 0.92 | not a signal 0.68 | signal 0.98 |
| 2 | not a signal 0.03 | not a signal 0.92 | not a signal 0.57 | signal 0.98 |
| 3 | not a signal 0.22 | not a signal 0.91 | not a signal 0.58 | signal 0.97 |
| 4 | not a signal 0.16 | not a signal 0.93 | not a signal 0.52 | signal 0.98 |

The KYC result reproduces in 5/5 and is the plan's best evidence. The RPA case is a false negative in 10/10, and the Run A confidence for one fixed input ranges 0.03–0.22. **All of this is now recorded in `17_rubric_experiment.md`'s own §"Replication attempt" (17:22), which reaches the same verdicts and draws the right conclusion.**

**Endpoint reachability:** `classifier.dev/llms.txt` 200 (0.29 s), `classifier.dev` 200, `api.typesafe.ai` **404** (consistent with `41` V5's correction to the sub-agent's report), `typesafe.ai` 308. Greenhouse `boards-api.greenhouse.io/v1/boards/n26/jobs` 200 with full posting JSON and real dates.

**Free-tier limits, from the service's own spec** (read, not inferred): `$0.01/Fast request`, `$0.10/Smart request`, `$0.50/IP/UTC day`, `$100/day` platform-wide, **four concurrent requests per IP**, 3,000 classifications/minute and **20,000/day** per IP on the fast tier. Published calibration: "answers at >= 0.9 were right 82% of the time and answers below 0.5 were right 29%". A `smart` tier escalates sub-0.7 answers to a reasoning model. Funded keys bypass the shared subsidy.

The 402 at 500 items and the 20,000/day cap are the same limit seen twice: the request ceiling is a **provider-spend** cap (≈400 windows ≈ the `$0.01` fast-request allowance), and the daily figure is the `$0.50/IP/day` subsidy. So batching smaller does not buy headroom past the daily number — which is why `41` V8's conclusion, "buy the $20/month Pro key before the corpus build", is the correct and only fix, and why the 55,410-decision rehearsal protocol needs 2.8 days of free-tier quota however it is batched. `41` V8 now states this and cites these measurements.
