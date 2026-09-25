# 13 — Scoring, Calibration, Explainability

**Judging weight targeted: Signal Relevance & Accuracy (25%), with AI/ML Innovation (20%) and Configurability (20%) as secondary.**
Author: agent 13. Date: 2026-09-25. Status: design proposal for `40_mvp_spec.md`.

---

## 0. The recommendation in one paragraph

Score with **additive log-odds (weight-of-evidence) accumulation**, not a weighted sum. Each detected signal
contributes a *weight of evidence* — a signed number of nats — built from four factors that all have units of
log-odds, so they multiply legitimately and the total is still evidence. Every signal is scored by a **narrow
question asked of a pre-segmented text window**, so the quoted evidence is a byte range of a document we already
hold and **fabrication is impossible by construction, not by hope**. Hard disqualifiers are **boolean gates
evaluated before scoring** and never enter the number. The output is recalibrated with a 2-parameter Platt fit on
a held-out set of 20 hand-labelled companies, and shown as a **ledger whose arithmetic is displayed**, so a juror
can check the sum on a phone calculator. The LLM never scores and never writes the explanation.

---

## 1. The model

### 1.1 Form

Let `π₀` be the base rate of qualified leads in our crawled pool, and `e₁ … eₙ` the evidence items retrieved for a
company. Then

```
logit P(fit | e₁…eₙ)  =  logit(π₀)  +  Σᵢ wᵢ                                     (1)
```

with each item's weight of evidence

```
wᵢ  =  tier(sᵢ)  ×  auth(srcᵢ)  ×  2^(−Δtᵢ / H(sᵢ))  ×  (2cᵢ − 1)                (2)
```

- `tier(sᵢ)` — the configured importance of the signal, as a log-odds contribution: **high +1.5 nats (≈4.5× odds),
  medium +0.7 (≈2.0×), low +0.3 (≈1.35×)**. Negative signals carry the same magnitudes with a minus sign.
- `auth(srcᵢ)` — source authority multiplier: company career page / IR filing 1.0, tier-1 press 0.9, company
  newsroom 0.9, trade press 0.7, PR wire and aggregators 0.5, unknown blog or forum 0.4, social 0.3.
- `2^(−Δtᵢ/H)` — exponential time decay, `Δt` in days, `H` the half-life (§4).
- `(2cᵢ − 1)` — the extractor's confidence `cᵢ ∈ [0,1]`, shrunk toward "no evidence" at chance. A coin-flip
  answer contributes exactly 0; a certain answer contributes in full; a confident *no* (c < 0.5) contributes
  **negative** weight with no extra code — the sign falls out.

**Why the product in (2) is legitimate and not a unitless mash.** Each factor is a multiplier on a *likelihood
ratio*: authority, recency and extraction confidence each scale how much a likelihood ratio moves toward the
buying hypothesis. A product of multipliers on a likelihood ratio is a likelihood ratio; its log is evidence; and
evidence from independent items adds. The sum on the right of (1) is therefore dimensionally coherent in a way
that `Σ wᵢ xᵢ cᵢ dᵢ` is not. This is the whole argument, and it is the answer to the first question a statistically
literate juror will ask.

### 1.2 Correlated evidence — the honest treatment

The naive-independence assumption in (1) is false, and its failure is the single biggest threat to this design's
credibility. Five articles about one press release are not five independent facts; five mentions of "efficiency"
in one document are not five signals. Two mechanisms, both cheap:

**(a) Within-signal rank discount.** Group items by the configured signal question they answer; within a group sort
by `|w|` descending and discount by rank with `ρ = 0.5`:

```
W_group  =  w₍₁₎  +  Σⱼ≥₂  ρ^(j−1) · w₍ⱼ₎          (3)
```

Bounded at `2·w₍₁₎` by construction. Explainable in one sentence to a sales rep: *"the second article about the
same thing is worth half as much as the first, the third a quarter."* This is the standard diminishing-returns
treatment of correlated evidence and it is what stops a press-release syndication from manufacturing a hot lead.

**(b) Theme saturation cap — the strongest false-positive control in the design.** Each signal question carries a
`theme` (automation, security, data, cost/efficiency). Cap each theme's total contribution at `W_theme_max`. We do
not guess this constant, we **derive it from the priority threshold**:

```
s*           =  ( logit(p*) − B ) / A                       (calibrated logit at the priority line)
W_theme_max  =  s*  −  logit(π₀)                            (4)
```

so that **one theme alone can reach, but never exceed, the priority threshold.** With `p* = 0.75`, `A = 0.85`,
`B = 0`, `π₀ = 0.25`: `s* = 1.293`, `W_theme_max = 2.39 nats`, and a single theme saturated at the cap lands at
exactly 0.75. The corollary is a rule a sales manager can memorise and a juror can verify: **entering the Priority
band requires independent evidence from at least two themes.** One very loud topic is a Nurture lead. That is
precisely the behaviour that keeps the queue clean, and it is one line of code.

### 1.3 Worked examples (the arithmetic a juror will check)

Constants below are the illustrative placeholders of §9: `π₀ = 0.25` ⇒ `logit(π₀) = −1.099`, Platt
`A = 0.85, B = 0` (mild shrinkage of an overconfident evidence score), `H = 90 d`, `ρ = 0.5`. **All of them are
replaced by values fitted on the labelled set during the build**; the arithmetic below is exactly what the code
does.

**Company A — a mid-size Moldovan logistics firm, one theme (automation).**

| # | Signal | tier | auth | Δt | decay | c | shrink | wᵢ (nats) |
|---|---|---|---|---|---|---|---|---|
| 1 | Hiring RPA developer (career page) | +1.5 | 1.0 | 6 d | 0.955 | 0.93 | 0.86 | **+1.232** |
| 2 | "Cost reduction / process optimisation programme" (tier-1 press) | +0.7 | 0.9 | 34 d | 0.769 | 0.78 | 0.56 | **+0.271** |
| 3 | Same programme restated (PR wire) — rank 2 in group, ×ρ | +0.7 | 0.5 | 30 d | 0.794 | 0.81 | 0.62 | **+0.086** |
| 4 | New CTO appointed (company newsroom) | +0.7 | 0.9 | 60 d | 0.630 | 0.88 | 0.76 | **+0.302** |
| 5 | Hiring freeze in logistics division (trade press) — **negative signal** | −0.7 | 0.7 | 12 d | 0.912 | 0.72 | 0.44 | **−0.197** |

```
W = 1.232 + 0.271 + 0.086 + 0.302 − 0.197 = 1.694 nats   (odds ×5.44)
logit = −1.099 + 1.694 = 0.595                            →  evidence score 0.64
Platt: logit' = 0.85 × 0.595 = 0.506                      →  calibrated fit 0.62   (Nurture)
```

Row 5 is why this model is not a sales brochure: a competing piece of evidence *the model surfaced against itself*
carries −0.197 nats and is displayed in the same ledger with the same rigour as the supporting rows.

**Company B — the same firm plus an independent security theme.** Rows 1–3 and the negative row are unchanged;
add a new CISO appointed 30 days ago (company newsroom, c = 0.90, high ⇒ **+0.857**) and a NIS2 compliance
obligation in the annual report 120 days old (IR filing, c = 0.85, medium ⇒ **+0.195**).

```
W = 2.444 nats  →  evidence 0.79  →  calibrated fit 0.76   (Priority)
automation theme 1.589 nats · security theme 1.052 nats · cap 2.391 neither binding
```

**This pair is the whole false-positive argument in two lines.** Company A's automation theme totals 1.589 nats —
loud, but under the cap and drawn from one theme, so it lands at 0.62 and stays Nurture *at every half-life from 30
to 270 days*. Company B clears the Priority line only because a second, independent theme corroborates. Independent
corroboration, not volume, is what buys a place in the queue.

**Staleness horizon.** Because every row ages by the same `t`, `W(t) = W₀ · 2^(−t/H)` exactly, so the date a
company crosses any band boundary is closed-form:

```
t*  =  H · log₂( W₀ / (s* − logit(π₀)) )        (5)
```

Company A above the Nurture floor (0.50): `t* = 90 · log₂(1.694 / 1.099) = 56 days`. (Against the Priority line it
is undefined and correctly reports *n/a* — A is not Priority and no mere passage of time will make it so.)
Company B: `t* = 3 days` against the Priority line and `104 days` against the Nurture floor. **"Priority for three
more days; above the Nurture floor for 104"** is a better sales instruction than any static score, and it is two
lines of code. It converts the score into the question that actually matters — *is this still true?*

---

## 2. Alternatives, compared honestly

| Approach | What it buys | Build cost | Explaining one company to a judge | Failure mode | Verdict |
|---|---|---|---|---|---|
| **(a) Weighted sum of binary signals** `Σ wᵢxᵢ / Σwᵢ` | Trivial to build; matches the brief's high/medium/low vocabulary directly; any sales rep understands it instantly | ~1 h | Trivial: "high signal present = 30 of 100 points" | Score is not a probability; a number between 0 and 1 that does not mean a frequency. Saturates: five correlated mediums beat one high. No way to express that a *missing* signal is informative. Changing one weight silently rescales every historical score. No false-positive control at all: no source authority, no recency, no extraction confidence | **Keep as the configuration vocabulary. Reject as the arithmetic.** |
| **(b) Weighted sum × confidence × decay** | Fixes the two most obvious objections (a 0.55 extraction ≠ a 0.95; an 8-month-old signal ≠ today's) | ~half a day | Easy but fragile — a juror asks "what does 0.55 × 0.79 × 1.5 = 0.65 *mean*?" and the honest answer is "nothing"; the factors have different units and their product is uninterpretable | Units incoherence; still additive over correlated evidence so it double-counts and inflates; no calibration story, so "0.87" remains meaningless and any threshold is arbitrary | **Correct instincts, wrong algebra — fold into (c) as likelihood-ratio factors** |
| **(c) Log-odds / weight-of-evidence accumulation** — **RECOMMENDED** | Every factor has log-odds units so the product is coherent; output is a probability so thresholds and precision statements are possible; explanation is a sum a juror can verify; handles negative evidence, absent evidence (via `π₀`) and confidence symmetrically; **is exactly a logistic regression**, so the learned upgrade path is the same model with fitted coefficients | ~1 day + calibration | The ledger. Rows are signed nats summing to the logit, and the arithmetic is printed on the card | Naive independence is false → correlated evidence double-counts (mitigated by (3) and (4), and *measured* by the ablation in §8); `π₀` dominates when evidence is thin, so thin-evidence companies hover near the base rate — which is correct behaviour but must be *explained*, not hidden (§3, abstention) | **Ship this** |
| **(d) Learned model** (gradient boosting on signal type, authority, recency, count, length, …) | Drops the independence assumption; learns interactions; naturally calibrated after Platt/isotonic on a held-out split; the right answer at scale | Days. Needs ~300–1000 labelled company-level examples for a company-level model; we will have 30 | SHAP values: "recency contributed +0.13." Follow-up "so what does that leaf value mean for a sales rep?" has no good answer, and it contradicts the brief's explainability requirement | With n=30 a GBM either overfits catastrophically or, regularised until it doesn't, learns the weights we already configured — at the cost of the explainability the rubric rewards. Labels bootstrapped from model output are circular | **Reject for the 48 h build. Keep as the documented roadmap** |
| **(e) LLM-as-judge** | Zero labels; absorbs nuance; reads a whole document at once; writes fluent prose | ~1 h | It writes a beautiful paragraph — **and that is the defect.** A generated paragraph cannot be checked; "where did it say that?" gets a paraphrase | Non-deterministic: the same company scores differently on two runs, and a juror who re-runs the demo will find it. Self-reported confidence is not calibrated (Guo et al. 2017; Kadavath et al. 2022 — RLHF policies are systematically miscalibrated). Documented judge biases: position, verbosity, self-enhancement (Zheng et al. 2023; Wang et al. 2023). Model upgrade silently invalidates every historical score | **Confine to extraction and to prose-only outputs (outreach drafts). Never scores, never explains** |

**(d) is not rejected, it is deferred — and the migration path is free.** Equation (1) *is* a logistic regression on
sparse evidence features, with the coefficients set by the sales team instead of by a fitter. When the label set
grows past ~200 companies, switch on **fitted mode**: keep the features and the ledger display, estimate the `tier`
coefficients with L2 regularisation on a train split, and show them as *suggestions* rather than overrides —
*"your configured weight for signal X is 1.5; the observed lift over 240 labelled snippets is 0.9."* The system
proposes, the human disposes. That preserves configurability (20%) and accuracy (25%) simultaneously instead of
trading one for the other.

---

## 3. Calibration — making "0.87" mean something

### 3.1 Define the target first, then calibrate to it

A jury's first attack is that "0.87 probability of buying" is unfalsifiable, and they are right. So we publish a
narrower claim we can actually label in 48 hours:

> **0.87 is the estimated probability that a company with this evidence profile is accepted as a qualified lead by
> the sales team, under our labelling protocol, among companies in our crawled pool.**

Three deliberate restrictions, each of which closes an attack: *accepted by the sales team* (a variable we can
label), *under our protocol* (published rubric), *among companies in our crawled pool* (the probability is
conditional on being crawled — which is the honest scope and avoids the base-rate trap in §9).

### 3.2 Method: Platt, not isotonic, not a vendor claim

| Method | Feasible in 48 h? | Assessment |
|---|---|---|
| **Platt scaling** `p = σ(A·s + B)` | **Yes** | 2 parameters. With 20 held-out companies and ~10 positive events, 2 parameters is exactly the amount of learning the data supports (≥10 events per parameter). Smooth, never emits 0 or 1 (isotonic can, which breaks any downstream arithmetic) |
| **Temperature scaling** | **Yes** — it is Platt with `B = 0` | One parameter. Use it at the **snippet** level with 60 labels to test the extractor's own probabilities. If the fitted `T ≈ 1` we report that we measured it and it was fine — **the test is the deliverable, not the correction** |
| **Isotonic regression** | No | Needs hundreds of points, is piecewise-constant, overfits badly with scarce calibration data (Zadrozny & Elkan 2002; Niculescu-Mizil & Caruana 2005 — isotonic is more flexible but loses to Platt when calibration data is small). Revisit at ~200 company labels |

**We do not trust the extractor's claimed calibration.** Jev is trained for calibration (RLCD) and its launch
evidence is a small spam test; independent buyers report that "the accuracy and the calibration aren't in the box"
(§11, source 19/20). So we treat `cᵢ` as an uncalibrated score and **measure it against our own 60 snippet
labels**, fitting a one-parameter temperature. That converts a vendor claim into our own measurement — which is
also, conveniently, the more impressive thing to say out loud.

### 3.3 Reporting rule: cap the precision at what the data supports

Show the number to **two significant figures, with the empirical band behind it and its interval**:

> **0.81** · n = 12 companies in the 0.80–0.85 band, 10 qualified (Wilson 95%: 0.55–0.95)

Over-precision is a tell of a fake model. Deliberately showing the band and the `n` signals the opposite. Show the
reliability diagram in the demo, and state the ECE **with the caveat that 5 bins over 30 companies is 6 per bin and
the estimate is descriptive, not inferential.**

### 3.4 What ranking needs versus what thresholding needs

Ranking needs only monotonicity, so calibration is strictly optional for *ordering*. Thresholding needs
calibration: the moment a human reads "0.87" and the dashboard says "surface anything over 0.8", precision at that
cut is a claim that is either calibrated or a lie. We show both numbers on the card (`evidence score` and
`calibrated fit`) so the recalibration step is visible and auditable rather than cosmetic.

---

## 4. Time decay

### 4.1 Derive the half-life; do not pick it

Model the buying state as a latent process that persists day to day with probability `q`. Evidence observed `t`
days ago is still indicative with probability `q^t`, which is exponential decay with

```
H  =  ln 2 / ( −ln(1 − 1/D) )        D = mean buying-window duration in days       (6)
```

| Mean buying window `D` | Daily persistence `q` | Implied half-life `H` |
|---|---|---|
| 60 d | 0.983 | 41 d |
| 90 d | 0.989 | 62 d |
| **130 d** | **0.992** | **90 d** |
| 180 d | 0.994 | 124 d |
| 270 d | 0.996 | 187 d |

Corporate IT-services evaluation cycles run three to six months, so `H ∈ [60, 125]` days and **we pick H = 90
days**. Note what this argument does: it converts an arbitrary-looking constant into a *consequence of an
observable business fact*. Exponential decay is not assumed, it is the shape implied by a persistent state.

Per signal class, because a point event and a structural fact do not age alike:

| Class | `H` | Rationale |
|---|---|---|
| News event, incident, funding round | 30 d | A point event. Its informational value is bounded below by the news cycle — a signal nobody can still find has no sales value even if the underlying state persists. Practitioner practice for intent surges is 14–21 days |
| Hiring, leadership change | 90 d | The state it indicates (building a capability) persists for a quarter |
| Strategic initiative, tech-stack, compliance obligation | 180 d | Structural; a multi-year programme announced in March is still true in September |

The mission's example, quantified: a hiring signal today carries full weight; the same signal at **8 months** (240 d)
retains **16 %** under `H = 90`, **0.4 %** under `H = 30`, **40 %** under `H = 180`. We **print that sensitivity
strip on the evidence card** (§6). A juror cannot attack a parameter choice whose sensitivity you have already
published.

### 4.2 Two provenance traps

- Decay uses the **fetch timestamp**, not the article's claimed publication date, because claimed dates are
  frequently absent, wrong, or future-dated. When an article date is extractable we show both, and label which one
  fed the decay.
- A page re-crawled today does not make an eight-month-old fact new. `Δt` is anchored to the **first observation**
  of that item, not the latest crawl. Getting this wrong is the most common way a decay implementation is quietly
  defeated.

---

## 5. Negative signals and disqualification

### 5.1 Three kinds of negative, three mechanisms

The incoherence the mission asks about comes from mixing three different questions into one number. Split them:

| Kind | Mechanism | Example | What breaks if mishandled |
|---|---|---|---|
| **Hard gate** | Boolean filter evaluated **before** scoring; company leaves the ranked queue, score still computed and displayed for audit | Wrong geography; competitor; below size floor; sanctions/export control; already a customer | As a weight, enough positive evidence overrides it — the exact thing a hard rule must never allow. As `×0`, the ledger no longer sums to the score and two disqualifiers become indistinguishable |
| **Negative signal** | Negative `wᵢ` in the same ledger as positive evidence | Layoffs; hiring freeze; competitor contract signed; in-house automation team expanding | A "−1000 points" cliff with no explanation, or an unbounded tug-of-war |
| **Weak evidence** | Small `|w|`, heavy decay | One ambiguous blog mention | Noise promoted to signal |

**Is this coherent? Yes, because the score and the gate answer different questions.** The score answers *"how
strong is the buying evidence?"* The gate answers *"are we permitted to sell to them at all?"* The first is a
quantity, the second is a fact about our own policy. A number cannot express "we are contractually barred from this
account", so we never make it try. This yields a clean jury line: **hard rules stay hard, and soft evidence stays
soft.**

### 5.2 Gates that do not misfire

Gate criteria sourced from *inferred* data (Crumbase-style headcount bands, HQ country) are frequently wrong, and a
jury will ask about false disqualification before you raise it. Therefore:

- Every gate input carries its own confidence. **A gate fires only on a high-confidence input.** A 60 %-confident
  headcount guess is automatically downgraded to a soft negative weight, not a disqualification.
- A range-valued field (employee band `51–200`) is tested by **interval overlap** against the ICP range; if the band
  straddles the threshold the gate is **inconclusive and downgraded to soft**. Disqualifying on a straddle is the
  single most likely false negative in the whole pipeline.
- Gate failure is displayed with the deciding field (`Gate: geography — HQ Chișinău, MD ∈ ICP [MD, RO, EU] → PASS`).
- Failed gates are **overridable, with a required written reason and an audit-log row**. Auto-disqualification
  without recourse is a product that will be turned off after it loses one enterprise deal.
- **The score is still computed for disqualified companies.** It costs nothing, it lets a rep see the counterfactual,
  and it lets us measure the gates' own false-positive rate — which goes on the dashboard next to precision.

### 5.3 Negative signals are just negative weights

"No layoffs" is not evidence, so an absent negative signal contributes nothing — this falls out of the model rather
than needing a rule. A confident negative is bounded by the same `W_theme_max` cap as everything else, which
prevents a single bad news week from driving a company to a floor it cannot climb out of.

---

## 6. The evidence card

**Design principle, and the answer to the unfalsifiability attack: the explanation is a deterministic render of the
ledger, not generated text. Every number is computed by the scorer and every string is a verbatim slice of a
fetched document. The LLM contributes no prose to this card at all.** There is no paragraph to attack. What a jury
sees instead is an auditable derivation.

Order and content, top to bottom:

1. **Verdict line.** Company, calibrated fit to two figures, band (Priority / Nurture / Insufficient evidence /
   Disqualified), queue rank, and one **template-substituted** sentence — slots filled from computed values only,
   no generation: *"Automation fit 0.62 — 5 weighted signals, strongest: hiring an RPA developer (career page, 6
   days old)."*
2. **Gate line.**  *"Gate: geography — HQ Chișinău, MD ∈ ICP [MD, RO, EU] → PASS"*, or the failing field with the
   note that the score did not enter ranking.
3. **The ledger — the core artefact.** One row per evidence item, each showing the full arithmetic of (2):
   `Hiring RPA developer [high +1.5] × career page [×1.0] × 6 d old [×0.955] × conf 0.93 [×0.86] = +1.232 nats (×3.4 odds)`
   sorted by `|wᵢ|` descending, with rank-discounted rows **marked as discounted** ("rank 2 in group, ×0.5"). Footer
   prints the whole sum: `logit(0.25) = −1.099 → +1.232 +0.271 +0.086 +0.302 −0.197 → logit 0.595 → evidence 0.64 →
   calibrated 0.62`. **A juror can verify the score with a phone calculator.** That is the difference between
   explainability and a plausible-looking paragraph, stated as a property of the interface rather than a promise.
   Also show the suppressed near-misses ("2 candidates below the 0.5 confidence floor, hidden") — displaying what
   you threw away is a credibility signal no generated summary can fake.
4. **Per-row provenance** (expandable), which is where §7 becomes visible:
   - **Verbatim snippet** with the matched span **highlighted**.
   - **Source URL**, source type, and its authority multiplier.
   - **Both timestamps**: fetch time (ISO 8601, UTC) and extracted article date, labelled as to which one fed the
     decay, plus the **snapshot hash** of the retrieved document.
   - **Character offsets** of the span in the retrieved document: `chars 4120–4318 of 18244`. A juror can
     `Ctrl-F` that string in the live page and confirm it in ten seconds. Nothing else in the demo is falsifiable
     that fast, and it is the single highest-value line on the card.
   - **The configured question, verbatim**, and the **raw extractor output** before the `(2c−1)` shrink, so the
     shrink is auditable rather than mysterious.
   - **Why this snippet matched — as an audit record, never as a story**: the extractor model and version, the
     prompt hash, and the verification verdict `containment: VERIFIED (exact, offsets 4120–4318)` or
     `FUZZY (similarity 0.97, whitespace/apostrophe normalisation)`. Failed rows appear **struck through with the
     reason**, in the UI, in production.
5. **Counter-evidence panel.** Every negative-weight row, same format, same rigour. Plus the *staleness horizon*
   from (5) — *"stays above the Nurture floor for 56 more days without new evidence"* — the time-decay sensitivity
   strip (`H = 30 / 90 / 180 d → 0.54 / 0.62 / 0.65`), and the **weakest link**: the row contributing the smallest
   share, i.e. the item to check first if you want to falsify the score.
6. **Config provenance footer.** Which ICP, signal-question set, weight config and score-model version produced
   this, and when the config last changed. Scores are **not comparable across config versions**, and saying so is
   what makes the historical list trustworthy.

Order rationale: a rep reads top-down and stops the moment they are convinced; an auditor reads the same card and
never needs a second screen. For the queue table, show only row 1 plus the single largest ledger row — comparison
speed is a different job from explanation, and conflating them is why most dashboards are neither.

---

## 7. The anti-hallucination story

### 7.1 Primary mechanism: extractive by construction

Do not ask a generative model to read a document and quote from it. Instead:

1. **Segment deterministically** — split the fetched document into sentence-safe windows of ~200–400 tokens with a
   one-sentence overlap.
2. **Ask one narrow question per window** (`noul`: *"Does this window state or directly imply that the company is
   hiring for RPA or automation engineering roles?"*), returning a probability in `[0,1]`.
3. **The evidence span IS the window** — a byte range of a document we already hold. No text is generated, so
   **fabrication is impossible, not merely unlikely.** The quote is a slice, not a claim.

This is not only a safety measure, it is an **accuracy** measure, and the evidence is direct: on a phishing-detection
task Jev scored 62.6 % against Claude Haiku 4.5's 81.3 % when asked one broad question, but **splitting the task into
five narrow questions raised Jev to 95.0 %** — decomposition helped Jev and did not help the frontier model
(source 20). The rigorous design and the accurate design are the same design.

**It is also cheaper.** A 400-token window × ~30 windows = 12 k input tokens per document at $0.042/M =
**$0.0005 per document**. One frontier call over the same document is ~10 k tokens at ~$3/M = **$0.03** — the
verifiable path is **~60× cheaper** than the unverifiable one. Latency is unchanged at the wall-clock level: 30
calls at 70–500 ms each, issued in parallel.

### 7.2 Fallback: generated quotes must be verified

When a question is genuinely generative, or a document cannot be windowed:

1. The model returns `{quote, …}` and **nothing else it says is trusted**.
2. **Exact containment on normalised text.** Normalise both sides (Unicode NFKC, whitespace collapse, curly
   quotes and dashes to ASCII, zero-width characters stripped, casefold) and require
   `normalise(quote) ⊆ normalise(document)`. Cost: microseconds, zero API calls, deterministic, version-stamped.
3. On failure, **fuzzy span matching**: token-level alignment requiring ≥ 0.9 token overlap over a **contiguous**
   span. Contiguity is load-bearing — a bag-of-tokens match lets a model stitch a sentence out of scattered
   fragments, which is the subtle failure a jury that knows this literature will probe. Mark the row `FUZZY`.
4. On failure of both: **strike the row**, log it, and increment `span_verification_failure_rate`, which is
   **published on the dashboard**. We do not claim zero hallucinations; we claim a *measured rate*, with every
   failure visible and struck through. That claim is unfalsifiable-proof because it is a number we are willing to be
   judged on.
5. **Optional second stage**: an NLI entailment check (MiniCheck-FT5, 770 M, GPT-4-level fact-checking at 400× lower
   cost — Tang et al. 2024) to catch quotes that contain the right words but do not entail the claim.

**State the limit out loud, in the pitch.** Containment proves *the string exists in the source*. It does **not**
prove *the string supports the claim*. That is exactly the gap between the two halves of the AIS definition of
attribution (Rashkin et al. 2023): a claim must be both interpretable and fully supported. Extraction by
construction gives us interpretability for free and containment gives us the support check for the span — but
"source contains this sentence" and "this sentence means the company needs automation" are different assertions,
and only the second is a judgement. Naming that gap before the jury finds it is worth more than pretending it does
not exist.

### 7.3 What verification costs

| Cost | Magnitude | Honest note |
|---|---|---|
| Containment check | ~0 API calls, microseconds | Free |
| **Recall loss** | Measured, report it | Containment **rejects correct quotes that were lightly paraphrased**. This is a real cost paid in recall for a guarantee in precision. We measure it on the labelled set and state the trade rather than hiding it |
| NLI second stage | ~$0.0001/document | Optional; only if the demo has budget for it |
| Storage | The **retrieved document**, not just the URL | The non-negotiable one: a page edited after the fetch invalidates the offsets, so we store the snapshot and its hash. This is also the answer to *"what if the page changes?"* |
| Translation | Index in source language | Show the original text; if a translation is shown it is marked as a translation and is **never** the evidence string |

---

## 8. Evaluation protocol — concrete, and runnable during the build

### 8.1 Two levels of labelling, because they measure different things

| Level | Question put to the labeller | n target | Cost | What it measures |
|---|---|---|---|---|
| **Snippet** | "Does this snippet evidence signal X?" (with a written rubric) | **60** (floor 40) | ~2 min each ≈ **2 person-hours** | The **extractor**: per-question precision/recall/F1, the empirical lift behind each configured tier, and the temperature fit for `cᵢ` |
| **Company** | "Would you accept this company as a qualified lead?" yes / no / maybe | **30** (20 calibrate, 10 held out) | ~6 min each ≈ **3 person-hours** | The **score** — the only thing that can measure the score. `maybe` counts as negative for precision (conservative) |

≈ **5–6 person-hours total**, i.e. one agent plus one human for half a day. This is the minimum that is honest, and
it fits inside the 48 hours.

**Two-stage calibration follows from the label counts**: a one-parameter temperature on 60 snippets, a two-parameter
Platt on 20 calibration companies. **We fit exactly as many parameters as our labels support — two, not five
hundred.** That sentence is the strongest defence of the whole approach.

### 8.2 Metrics to report, with their intervals

- **Per-signal extractor precision / recall / F1** against the 60 snippet labels, each with `n` and a Wilson 95 %
  interval. Example of the honest form: *precision 0.85, Wilson 95 % [0.74, 0.92], n = 60*. The interval is ±9
  points, and saying so is the point — we know exactly how much our own evaluation can and cannot prove.
- **Precision@10** on the held-out companies, Wilson interval attached: 8/10 ⇒ **0.80, [0.49, 0.94]**. That
  interval is too wide to distinguish 0.7 from 0.9, and we say so unprompted. Stating the width of your own
  headline metric is the move that makes a jury believe the rest of the numbers.
- **PR curve** by sweeping the threshold over the 30 scored companies. Report AUC but flag it as unstable at
  n = 30 and do not lean on it.
- **Reliability diagram**, 3–5 bins, with ECE labelled descriptive. Report the fitted Platt `A, B` and the snippet
  temperature `T`.
- **The false-positive audit — the most persuasive artefact in the demo.** Every company scored above threshold
  that the labeller marked *no* gets a written root cause, categorised: *signal fired on a press release about a
  **customer** of the company rather than the company; window contained a competitor comparison; headcount band
  straddled the ICP floor; article was about a same-named entity.* Show the histogram of causes. **A team that
  brought its own error analysis cannot be attacked on accuracy** — there is nothing left to reveal.
- **Ablation, pre-registered before the numbers are in**: precision@10 for (i) raw weighted sum, (ii) + decay,
  (iii) + confidence shrink, (iv) full log-odds + rank discount + theme cap. Commit in advance to shipping (i) if
  (iv) does not win. At n = 30 the differences will sit inside the interval, so report the **effect size with its
  CI, not a p-value**, and say that the ablation is directional evidence, not proof.
- **Adversarial negative controls**: 10 companies known to be non-fits (direct competitors, a company with zero
  signals, a same-name decoy). Report how many surfaced. Cheap, brutal, and the single best answer to "how do we
  know it doesn't just fire on everything?"
- **Null-company test**: a company with zero evidence must score at `π₀`, not above it. Ten seconds to check,
  catches an entire class of accumulation bugs.

### 8.3 The two dashboard numbers that keep the sales team using the tool

False positives are the stated reason adoption dies, so make them a first-class, always-visible metric:

- **Queue precision (rolling)**: of the last 20 companies actioned, how many were accepted by the rep. Feeds the
  next recalibration. This is also the flywheel that makes the roadmap credible: labels accrue as a *by-product of
  use*, so fitted mode becomes reachable without a labelling project.
- **Extraction hallucination rate**: `span_verification_failure_rate`. Published, not buried.

### 8.4 Configurability that is measured, not asserted

Offer a **"re-score the labelled set under this config"** button on the configuration screen. Changing a weight or a
half-life re-runs the held-out evaluation in milliseconds and shows the `precision@10` delta *before* the config is
saved. This makes one feature serve both the accuracy criterion and the configurability criterion: the sales team is
not merely *allowed* to define the scoring logic, they are **told what their definition did to measured precision**.
It also answers the "was the old config better?" question with data instead of archaeology.

**What is configurable and what is not, and why**: configurable = gates and their thresholds, signal questions,
themes, tiers, the source-authority table, half-lives per signal class, `ρ`, `π₀`, band cut-offs. **Not**
configurable = the *form* of the arithmetic (that it is log-odds and additive), the `(2c−1)` shrink, and span
containment. Configurability that lets a user make the arithmetic incoherent is not a feature. Offer
configurability of *coefficients*, not of *structure*.

**Guard against overfitting the config to 30 companies**: hold out 10, and refuse to display an empirical lift when
a signal type has fewer than 5 labelled snippets — show *"insufficient data — showing configured prior"* instead.
A system that declines to produce a number it cannot support is a system a jury trusts.

---

## 9. What we verified versus what we are assuming

State this plainly; a jury that hears a team distinguish its evidence from its assumptions will grant the
assumptions.

| Claim | Status |
|---|---|
| Jev exists, its API is `state` + typed questions (`choice` ≤255 options, `score` 2–10 levels, `noul` → probability 0–1), $0.042/M input tokens with free output, 70–500 ms | **Verified** across vendor and independent coverage (sources 19–20) |
| Jev's probabilities are calibrated out of the box | **Not trusted.** Vendor-claimed from a small spam test; independent coverage reports "the accuracy and the calibration aren't in the box". We measure and refit against our own labels (§3.2) |
| Narrow decomposition raises accuracy (62.6 % → 95.0 % on one task) | **Vendor-adjacent, single task.** We adopt the design because it also makes fabrication structurally impossible and is ~60× cheaper — the accuracy claim is a bonus, not the justification |
| TypeSafe's own benchmarks are biased | **Acknowledged by TypeSafe itself** (correctness defined as agreement with two other models, self-authored workflows). We cite the architecture, not the leaderboard |
| Half-life 90 days | **Derived** from a 3–6 month buying window via (6), not picked. Sensitivity published on the card |
| Tier weights (+1.5/+0.7/+0.3 nats) | **Placeholders from the sales team's priors**, to be replaced by the empirical lifts measured on the 60 snippet labels |
| **`W_theme_max = 2.39 nats`** | **Derived** from the priority threshold by (4), not tuned |
| Log-odds beats weighted sum on our data | **Pre-registered to be tested** by the §8 ablation. If it loses, we ship the weighted sum and say so |

---

## 10. Build plan against the 48-hour clock

| Step | Hours | Output |
|---|---|---|
| Ledger data model + evidence tables (signal, window, span offsets, snapshot hash, fetch/observed timestamps, extractor version, raw probability) | 3 | Schema |
| Window segmentation + per-window narrow question + extraction | 4 | Evidence rows with byte-range spans |
| Log-odds scorer: (2), (3), theme cap (4), gates, staleness horizon (5) | 4 | Scores |
| Containment verification + normalisation + failure logging | 2 | Verified/`FUZZY`/struck rows |
| Evidence card (§6) | 5 | The demo surface |
| Labelling harness + rubric + 60 snippets / 30 companies | 5 | Labels |
| Temperature + Platt fit, reliability diagram, ablation, FP audit | 4 | The evaluation slide |
| Config screen with re-score-under-this-config | 4 | Configurability with measurement |
| Adversarial negative controls + null-company test | 1 | Robustness evidence |
| **Total** | **32 h** | ~16 h of slack for integration and the pitch |

The ordered risk: if only one thing ships, ship the **ledger card plus the 60-snippet extractor evaluation**. The
25 % criterion is won by measurable precision and a checkable explanation; a fancier model with no evaluation is
worth less than a simple model with a published PR curve, a Wilson interval, and an error analysis.

---

## 11. Questions a jury will ask, and the answers

**Q: Why not just a weighted sum? The brief says high/medium/low.**
The brief specifies the *configuration vocabulary*, and we implement it exactly — the sales team sets high, medium
or low. The arithmetic behind those tiers is log-odds, because a weighted sum cannot produce a probability, cannot
say what a "87" means, cannot express that a missing signal is informative, and lets five correlated mentions of
one press release outvote one hard fact. We keep the vocabulary and fix the arithmetic. High is not "3× low", it is
odds ×4.5 versus ×1.35 — which is what a weight *should* mean.

**Q: Why is multiplying authority × recency × confidence × tier legitimate? Those have different units.**
In a weighted sum it would not be legitimate, and that is exactly why we do not use one. Each factor multiplies a
*likelihood ratio*, and a product of multipliers on a likelihood ratio is still a likelihood ratio. That is why the
sum in equation (1) is coherent and why every row on the card can be printed in the same unit — nats.

**Q: Your evidence items aren't independent. This is naive Bayes and it double-counts.**
Correct, and it is the main threat to this model, so we handle it twice. Within a signal, items are rank-discounted
at ρ = 0.5, so a syndicated press release contributes at most twice its strongest article and no more. Across
signals in the same theme, a single theme is capped so that it can *reach* the priority threshold but never exceed
it — which means entering Priority requires independent evidence from two themes. And we do not just assert that
this works: the ablation in §8.2 measures precision@10 with and without each correction, and we will report the
effect size with its interval, including if it goes the wrong way.

**Q: A company that appears in no sources — what does it score?**
Exactly `π₀`, the base rate, and it is labelled **"Insufficient evidence — needs manual research"** rather than shown
as a weak lead. Absence of evidence is not evidence of absence, and the model says so instead of guessing. A null
company must score at the base rate; we test that explicitly.

**Q: Isn't "0.87 probability of buying" unfalsifiable?**
As stated, yes — which is why we do not claim it. Our claim is narrower and labellable: the probability that a
company with this evidence profile is accepted as a qualified lead by the sales team, under our published rubric,
among companies in our crawled pool. The test is the reliability diagram: among companies scored 0.85–0.90, what
fraction were labelled qualified. We show that diagram with its bin counts and its interval, including where it is
bad.

**Q: What is the base rate? With a 1 % base rate no amount of web evidence gets you to 0.87.**
This is the sharpest question in the room and the answer is a scoping decision, not a fudge. `π₀` is not the
prevalence of good leads among all companies on Earth — it is the prevalence **within our crawled pool**, which we
set empirically from the labelled set. That is why the claim is explicitly conditional on being in the pool. If we
claimed an unconditional probability of buying, a 1 % base rate would indeed make every score near zero and the
whole thing dishonest. We would rather hold a narrow claim we can defend than a broad one we cannot.

**Q: You're using an LLM. How do you know the quotes are real?**
They are not quotes in the usual sense: they are byte ranges. We segment the document deterministically and ask one
narrow question per window, so the evidence is a slice of a document we already hold and no text is generated —
fabrication is structurally impossible, not merely unlikely. Every row shows character offsets, so you can Ctrl-F
the page and confirm it in ten seconds. Where generation is unavoidable we verify by exact containment on
normalised text, fall back to contiguous fuzzy span matching at ≥ 0.9, and strike the row otherwise. The strike rate
is a published metric on the dashboard. We do not claim zero hallucinations; we claim a measured rate with every
failure visible.

**Q: So containment proves the quote supports the claim?**
No — and this is the limit we want to name before you find it. Containment proves the string exists in the source.
It does not prove the string means the company needs automation. That is the gap between the two halves of the AIS
attribution definition: interpretable, and fully supported. Extraction by construction gives us interpretability
for free, containment covers the span's existence, and the semantic step remains a judgement with a measured error
rate. We report it rather than assert it away.

**Q: What happens when the source page changes?**
We store the retrieved snapshot and its hash, not just the URL, so the audit is reproducible after the page is
edited. Offsets are relative to the snapshot, and the card says so.

**Q: How do you know your classifier's confidence is calibrated? It's a vendor claim.**
We don't. We measure it: 60 hand-labelled snippets, a fitted temperature, and a reliability diagram. If the fitted
temperature comes out at 1.0 we report that we tested it and it was fine — the test is the deliverable, not the
correction.

**Q: Why Platt and not isotonic regression?**
Isotonic needs hundreds of calibration points and overfits with scarce data, and it emits probabilities of exactly 0
and 1, which breaks downstream arithmetic. Platt is two parameters, which is precisely what 20 labelled companies
support. At ~200 labels we would revisit isotonic, and we say so.

**Q: Why a 90-day half-life and not 30 or 180?**
It is derived, not picked. Modelling the buying state as persistent day-to-day gives exponential decay with
H = ln2 / −ln(1 − 1/D) for a buying window of mean duration D. IT services evaluation cycles run three to six
months, which puts H between 60 and 125 days; we take 90 and use 30 for point news events and 180 for structural
facts. The card prints the score at H = 30 / 90 / 180 so you can see how much the choice matters for this company:
0.54 / 0.62 / 0.65. And we will tell you the uncomfortable part unprompted — for a borderline company the half-life
is **band-determining**: our second example scores 0.72 at H = 60 and 0.76 at H = 90, straddling the Priority line.
That is exactly why the half-life is configurable, why the sensitivity strip is printed rather than buried in a
config file, and why the config screen re-scores the labelled set before saving.

**Q: You have two example companies. What actually separates them?**
Independent corroboration, not volume. The first draws 1.59 nats from a single theme and lands at 0.62 — Nurture at
every half-life from 30 to 270 days, because the theme cap is derived from the priority threshold and one theme
cannot breach it. The second adds a security theme and clears 0.76. This is the false-positive control in one
sentence: **a company has to be interesting for two independent reasons before it reaches the top of the queue.**

**Q: A competitor with a perfect automation profile — does it score 0?**
No, and that is deliberate. It scores whatever the evidence says and it is removed from the queue by a **gate**,
with the deciding field shown and the score still displayed for audit. A hard rule that lives inside a weighted
score is not a hard rule — enough positive evidence overrides it. Gates stay boolean; soft evidence stays soft.

**Q: Gates on headcount and geography are often wrong. How many good leads do you throw away?**
We gate only on high-confidence inputs; a 60 %-confident headcount estimate is downgraded to a soft negative weight
instead. Range-valued fields are tested by interval overlap and a straddle is inconclusive, so it does not
disqualify. Failures are overridable with a logged reason. And because we still compute the score for disqualified
companies, the gates' own false-positive rate is a number on the dashboard, not an unknown.

**Q: Can a sales rep game this by setting every weight to high?**
They can, and the re-score button will immediately show them the precision@10 that results. Configurability that is
measured is the difference between a tunable system and a toy.

**Q: What if we upgrade the extraction model mid-quarter?**
Every evidence row stores the model and version that produced it, and the score carries a model version. Scores
across versions are marked as not comparable rather than silently blended. Re-scoring under a new model is an
explicit, evaluated operation, not a side effect of a deploy.

**Q: What's the single weakest part of this design?**
The company-level label set is 30 items, so precision@10 carries a Wilson interval of roughly [0.49, 0.94] — too
wide to distinguish a good system from a mediocre one. We report it with the interval and we lean on the 60-snippet
extractor evaluation and the false-positive audit, which are far better powered, as the load-bearing evidence. The
honest statement is: we can prove our extractor is precise and our explanations are verifiable; we can only show
directional evidence that the ranking is good.

---

## 12. Sources

1. Guo, Pleiss, Sun, Weinberger — *On Calibration of Modern Neural Networks*, ICML 2017. arXiv:1706.04599. (Temperature scaling; modern nets are miscalibrated; T is fit by NLL on a validation set and does not change accuracy.)
2. Platt — *Probabilistic Outputs for Support Vector Machines*, 1999. (Sigmoid fit; out-of-sample fitting to avoid overfitting the calibration set.)
3. Zadrozny, Elkan — *Transforming Classifier Scores into Accurate Multiclass Probability Estimates*, KDD 2002. (Isotonic regression, PAV, piecewise-constant, monotonicity assumption.)
4. Niculescu-Mizil, Caruana — *Predicting Good Probabilities with Supervised Learning*, ICML 2005. (Isotonic more flexible but overfits; Platt preferable with scarce calibration data.)
5. Kadavath et al. — *Language Models (Mostly) Know What They Know*, 2022. arXiv:2207.05221. (Self-evaluation; RLHF policies need temperature adjustment to be calibrated.)
6. Rashkin et al. — *Measuring Attribution in Natural Language Generation Models*, Computational Linguistics 2023. (AIS framework: a claim is attributable only if interpretable **and** fully supported.)
7. Bohnet et al. — *Attributed Question Answering*, 2022. arXiv:2212.08037. (Answer-attribution pairs; AutoAIS as NLI-based attribution evaluation.)
8. Gao, Yen, Yu, Chen — *Enabling Large Language Models to Generate Text with Citations* (ALCE), 2023. arXiv:2305.14627. (Citation precision/recall/F1; ~50 % of generations not fully supported by cited passages.)
9. Tang, Laban, Durrett — *MiniCheck: Efficient Fact-Checking of LLMs on Grounding Documents*, EMNLP 2024. (GPT-4-level grounding verification at 400× lower cost; 770 M model.)
10. Zheng et al. — *Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena*, 2023. arXiv:2306.05685. (>80 % human agreement, but position/verbosity/self-enhancement biases.)
11. Wang et al. — *Large Language Models are not Fair Evaluators*, 2023. arXiv:2305.17926. (Position bias; calibration strategies.)
12. Angelopoulos, Bates — *A Gentle Introduction to Conformal Prediction and Distribution-Free Uncertainty Quantification*, arXiv:2107.07511 / FnT ML 2023. (Distribution-free uncertainty sets; abstention.)
13. Geifman, El-Yaniv — *Selective Classification for Deep Neural Networks*, NeurIPS 2017. (Abstention / reject option as a first-class output.)
14. Good — *Probability and the Weighting of Evidence*, 1950. (Weight of evidence as log-likelihood ratio; additive across independent items.)
15. Jakulin, Bratko — *Analyzing Attribute Dependencies*, ECML 2003. (Interaction gain; attributes providing the same evidence get subtracted twice — the formal statement of the naive-Bayes double-counting failure.)
16. Wilson — *Probable Inference, the Law of Succession, and Statistical Inference*, JASA 1927. (Score interval used for all reported binomial intervals.)
17. Abmatic AI — *Segmenting Customers by Intent Signal Strength*, 2026. (Published half-lives: first-party 7 d, search 14 d, Bombora 21 d, review sites 10 d; explicit warning against double-counting correlated multi-channel signals.)
18. Bombora / 6sense — *Company Surge* documentation and FAQ. (Surge = 3 weeks of sustained intent vs a 12-week baseline; score 60+ signals a surge; weekly updates.)
19. Salesmotion — *How Fresh Is Your Sales Signal Data?*, 2026. (Trigger events are worth most in their first days, "something in its first weeks, and almost nothing after a quarter"; ~80 % of the vendor shortlist is set on day one.)
20. O'Reilly Radar — *Will TypeSafe's Jev Change How We Build AI Applications?*, Sept 2026. (Jev API shape; $0.042/M input tokens, free output; 70–500 ms; no explanations by design; "the accuracy and the calibration aren't in the box"; TypeSafe's own benchmarks are self-authored and biased.)
21. greennode.ai / Zapier — *What is Jev?* Sept 2026. (Primitives `choice` ≤255 options, `score` 2–10 levels, `noul` → probability 0–1; request/response shapes; the phishing case where decomposition into five narrow questions moved Jev from 62.6 % to 95.0 %.)
