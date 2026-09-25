# Synthesis — what we are building, and why it wins

The single document to read. Everything here traces to a file in this directory; nothing is asserted without a source.

---

## 1. The decision

**Build a service-scoped signal policy engine whose headline capability is refusing to recommend bad companies — demonstrated live on a question the jury writes.**

Not a lead list. Not a scraper. Not "AI-powered prospecting". The product's identity is *the system that tells a sales team no, with evidence*.

## 2. Why the original framing had to change

The hypothesis proposed a two-tier architecture — a cheap classifier for triage, a generative model for outreach — and pitched configurability as the innovation.

**Two research results forced a change:**

1. **Configurability is table stakes.** Clay shipped plain-language Custom Signals in May 2025; HubSpot shipped its own in June 2026 and hired Trigify's team days before this hackathon. Leading with "the sales team can define their own questions" pitches a feature two incumbents already sell.
2. **The classifier's real edge is not price.** "40–400× cheaper" is vendor marketing; measured independently it is 5.4× faster and 96% cheaper. Repeating the marketing number in front of a technical jury invites the one question you cannot answer.

**What survives, and is better:** the *rubric*. Measured here, on live infrastructure — same model, same texts, one plain-language paragraph of business reasoning added, correct decisions go **4/6 → 6/6**, and the case that flips is the highest-value signal this client has. That is an original, testable claim, and it maps directly onto the challenge's own wording.

## 3. The three findings that decide the pitch

### Finding 1 — A rubric converts a classifier from guesswork into a tool (`17_rubric_experiment.md`)
Without instructions, "KYC Team Lead – EDD/ODD" is classified **not a buying signal** (0.78), because the text never says *automation*. With a rubric encoding the business inference — that hiring for high-volume manual compliance work is exactly what onboarding automation replaces — it becomes **signal (1.00)**. Meanwhile every boilerplate case is rejected at 1.00. Keywords cannot do this; a rubric can, and the rubric *is* the configurable signal question the brief asks for.

### Finding 2 — Service-scoped polarity with disqualifiers is genuinely unshipped (`18_service_scoping_experiment.md`, `12_landscape.md`)
The same evidence scores `buy (1.00)` for automation and `none (0.93)` for cybersecurity. An in-house automation announcement scores **`build (1.00)`** — a competitor, not a lead. Competitive research confirms no verified product does this: incumbents compute one global account score, and negative rules that override positives exist only in consultancy prose.

### Finding 3 — The intuitive signal is the worst one (`15_signal_taxonomy.md`)
"Hiring an RPA developer" looks like the perfect signal. Purchase data says it is the *weakest measured* — job-posting growth ranks last of eight signals in a million-purchase study, and roughly 70% of firms intend to run RPA internally. It is disambiguated by the verb in the job description: *choose/manage* buys, *build/code* does not. Two more counter-intuitive results: prior vendor experience — not new budget — is the top reason buyers make contact, and a new executive triggers a review of the inherited stack rather than fresh spend.

**This corrected an error in our own experiments.** The rubric test used "hiring an RPA Developer" as a positive case. Under the taxonomy it is a *disambiguated* case at best. The evaluation set was built from KYC/AML roles, which the research independently ranks as the strongest signal family — but the framing must change.

## 4. The evidence ladder

| Status | Claim |
| --- | --- |
| **Verified first-hand** | Keyless ATS APIs return dated postings (Greenhouse/Lever/Ashby); TED, SEC EDGAR, GDELT also verified keyless by the taxonomy agent |
| **Verified first-hand** | Rubric flips 4/6 → 6/6 on realistic cases |
| **Verified first-hand** | Service-scoped polarity and the `build` disqualifier work, 4/4 |
| **Verified first-hand** | Keyword matching on bodies is 19:1 noise |
| **Verified negative** | Slug resolution 0/8 on real careers pages; FCA notices 403 plain requests |
| **Measured, but biased** | 1.00 precision/recall on 20 real titles — self-authored rubric, titles only. **Do not quote as accuracy.** |
| **Asserted by vendor** | Classifier calibration figures — must be re-measured on our own labels before the 0.7 gate is claimed |
| **Unverified** | Corpus size, re-score latency, the 7-of-10 question test, all business-case inputs |

## 5. What to build

**Architecture** (`13_scoring_design.md`): additive log-odds (weight-of-evidence) accumulation, not a weighted sum — every signal contributes `wᵢ = tier × source_authority × 2^(−Δt/H) × (2cᵢ−1)` nats, so the score is a ledger a juror can verify on a phone calculator. Half-life *derived* from a 3–6 month buying window (H≈90d), not picked. Hard disqualifiers are boolean gates outside the score, because "we may not sell here" is not expressible as a number. Evidence is **extractive by construction** — a byte range in a stored capture — which makes fabrication structurally impossible rather than merely unlikely.

**Signals** (`15_signal_taxonomy.md`): lead with the three highest strength×detectability — data-shaped AML/KYC enforcement findings (FCA data deficiencies appear in 68% of AML final notices); a public automation programme with a quantified target, a named function and no vendor; and cost reduction alongside automation hiring in the same function. Anchor the whole taxonomy in InSight KYC's value story, because that is what this client is best known for selling.

**Screens** (`14_golden_path_demo.md`): pipeline board, evidence card, Signal Studio, re-score diff, outreach modal. Five surfaces, no settings wizard, no charts.

**Demo** (`14_`, `21_`): the peak is the jury writing their own signal question and watching the board re-order around it — the only beat a team that did not build the architecture cannot fake. The second-strongest is the **negative space**: a company that looks perfect, rejected, with a reason.

## 6. What would lose it

1. **Quoting 1.00 precision.** Self-authored rubric, n=20, titles only. A jury that spots it discounts everything else.
2. **Quoting 40–400×.** Overstated and checkable.
3. **A thin corpus.** The demo dies if a juror's question moves fewer than three rows. Corpus breadth is the actual build priority — not the model.
4. **Building a slug resolver.** Measured 0/8. Seed a curated list.
5. **Pitching configurability as novel.** It shipped 16 months ago at Clay.
6. **Any slide about the architecture.** One memorised sentence spoken over the scoring wait.

## 6b. The adversarial verdict — and what it changes

A hostile reviewer graded the plan against the rubric and independently re-ran our own experiments. **Score: 40/100 as the research stands, 70/100 if everything specified is executed.** All 30 points of delta sit in four items, and three are decisions rather than work.

**Three findings that outrank everything above:**

1. **The Priority band is empty by construction.** The scoring design fixes a 25% base rate; the taxonomy assumes ~5%. At 5%, a realistically-evidenced company needs 5–6 strong independent signals and the corpus holds ~5.9 evidence units *per company in total* — so the top band has nothing in it and the demo's re-rank moment has nothing to re-rank. A juror with a calculator finds this in ninety seconds. **Reconcile the base rate and re-derive the bands before building anything else.**
2. **The plan's own rehearsal test cannot run on the tier it chose.** Measured limits: 3,000 classifications/minute and 20,000 decisions/day. The 7-of-10 test across three corpus snapshots needs 55,410 decisions — 2.8× the daily cap. A $20/month key lifts limits 10×. Absent from every document.
3. **Our headline accuracy result does not fully replicate.** The KYC flip reproduces 5/5 and is the plan's best evidence. The RPA case came back opposite in 10/10 trials — and the taxonomy research says our original label was wrong anyway. Single-call results from this endpoint are noise: one fixed input ranged 0.03–0.22 across five identical calls.

**What the reviewer credited:** the 19:1 measurement, the KYC/financial-crime finding, the counter-interpretation discipline, the byte-range evidence design, and the disqualifier-as-gate semantics. **Its sharpest single sentence, which is worth internalising:** *"The one genuinely novel thing here is that your citations are byte ranges instead of sentences — and you spent the pitch explaining the sum."*

## 7. Open questions carried forward

- The **Annex 1 participant reference pack** is referenced by the brief and absent from the repo. It contains the real manual process and two worked examples — if obtained, the demo should mirror them exactly rather than approximately.
- **Prize discrepancy:** the event site lists 30,000 MDL for the AI/ML track; the brief says €30,000. Worth confirming with organisers.
- Whether the corpus can reach 300+ companies in the remaining time — the single largest execution risk.
- The adversarial review (`30_adversarial_grading.md`) is in flight and may add to this list.
