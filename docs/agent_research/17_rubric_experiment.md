# The Rubric Experiment — the single most important result in this research

Run 2026-09-25 against a live service. Reproducible in under a minute. This experiment converts the challenge's central requirement — *"sales team can define their own business questions"* — from a UI feature into a **measured accuracy claim**, which is what 45% of the score (signal relevance 25% + configurability 20%) actually rewards.

## Setup

A free, keyless zero-shot classification endpoint (`classifier.dev`, `POST /v1/classify`, no account, ~1s for a batch, calibrated confidence) was given six realistic texts and asked to decide: is this a buying signal for automation/onboarding services?

The same six texts were run twice:

- **Run A — labels only.** `{"labels": ["strong buying signal for automation services", "not a buying signal", "unclear or insufficient evidence"]}`
- **Run B — labels plus a plain-language rubric.** Same decision, but with an `instructions` field carrying the *business reasoning*: that hiring for high-volume manual back-office work (KYC, AML, financial-crime review, onboarding operations) is a signal **even when the text never says "automation"**, because that workload is what onboarding automation replaces — and that generic corporate language about efficiency or excellence is not.

## Results

| Case (realistic text) | Run A: labels only | Run B: labels + rubric |
| --- | --- | --- |
| Hiring an RPA Developer (UiPath) | signal 0.89 ✓ | signal 0.53 ✓ |
| **KYC Team Lead – EDD/ODD** | **not signal 0.78 ✗** | **signal 1.00 ✓** |
| "No plans to invest in automation or RPA" | not signal 0.99 ✓ | not signal 0.99 ✓ |
| "Committed to operational efficiency… digital transformation" | signal at 0.02 (coin-flip) | not signal 1.00 ✓ |
| Business Development Representative posting | not signal 0.94 ✓ | not signal 1.00 ✓ |
| Vague partnership announcement | not signal 0.87 ✓ | not signal 1.00 ✓ |
| **Correct** | **4 / 6** | **6 / 6** |

## Why this matters more than any architecture slide

**1. It fixes the exact failure that kills this product category.** Run A misclassified a *KYC Team Lead* posting — the highest-value signal the client has, mapping directly onto InSight KYC — as "not a buying signal", because the text never uses the word "automation". That is not a quirk of this model; it is the structural limit of any system that treats a signal as a keyword. The buying *reason* is an inference from business context, and it has to be supplied somewhere. Supplying it in plain language is what flips the answer to 1.00.

**2. The rubric is literally the challenge's requested feature.** The brief asks for "custom signal questions for each service" configured by the sales team. Run B *is* that feature, working, measurable. The pitch writes itself: every question Maria writes in the Studio is an `instructions` string, and here is the before/after showing it changes the answer.

**3. It dissolves the false-positive problem measured earlier.** The keyword experiment found a 19:1 title-to-body noise ratio, with "Business Development Representative" and boilerplate matching `process automation`. Run B rejects all three boilerplate/vague cases at confidence 1.00 while still accepting both genuine signals. The two experiments together tell one story: *keywords cannot do this, and a rubric can.*

**4. It reframes the innovation claim honestly.** The "AI/ML innovation" argument should **not** be "we use a model 40–400× cheaper than GPT" — that is vendor marketing, it is contested, and a knowledgeable judge will discount it. The defensible claim is: *"accuracy in this domain comes from encoding business reasoning as a rubric, not from a bigger model — and here is the controlled comparison."* That is a real, testable, original contribution, and it is exactly what a sales-intelligence product needs to justify its scores.

## The calibration observation

Calibrated confidence behaved exactly as documented, and it is doing real work:

- Genuine signal → 1.00, 0.53
- Genuine non-signal → 0.99, 1.00
- Ambiguity → Run A returned the *wrong* label for the boilerplate case at confidence **0.02**, which under the service's own published calibration ("answers below 0.5 were right 29% of the time") is correctly read as "do not act on this".

**So confidence is a usable gate, not decoration.** A threshold at 0.7 with everything below routed to human review is implementable today and is defensible to a jury. That is the "low false-positive rate" requirement answered with a mechanism rather than a promise.

## Replication attempt — the headline does not fully reproduce (added 17:22)

The adversarial review (`30_adversarial_grading.md`) re-ran the first two cases five times per condition. Results:

| Case | This file claimed | Replication (5 trials each) | Verdict |
| --- | --- | --- | --- |
| KYC Team Lead | flipped from `not signal 0.78` → `signal 1.00` | **`not signal` → `signal 0.97–0.98`, 5/5** | **Reproduces. This is the strong finding.** |
| "Hiring an RPA Developer (UiPath)" | `signal 0.89` → `signal 0.53` | **`not signal` in 10/10**, Run B confidence 0.52–0.68 | **Does not reproduce — and the original was probably wrong.** |

Two corrections follow, and both matter:

1. **The "4/6 → 6/6" headline is not robust and should not be quoted.** The KYC result — the one that matters, and the one that maps onto InSight KYC — is solid across trials. The RPA result was a single call that the replication contradicts.
2. **The service is non-deterministic.** For one fixed input, Run A confidence ranged **0.03–0.22** across five identical calls. A single-call result is not evidence of anything; any accuracy claim must be repeated and reported as a distribution, not a point.

**And the RPA case was mislabelled by this file anyway.** The taxonomy research independently concluded that hiring an RPA developer is the *weakest* measured buying signal — job-posting growth ranks last of eight in a million-purchase study, and most firms intend to run RPA internally. So the classifier's "not a signal" verdict is arguably **correct** and this file's original label was the error. The lesson is not "the model is unreliable" but "we labelled a case before doing the research".

**What survives:** the KYC flip, the boilerplate rejections (independently confirmed at 1.00), and the service-scoping result. **What does not:** the specific 6/6 count, and any single-call number in this file.

## Honest limitations

- **n = 6.** This is a demonstration, not an evaluation. It shows the mechanism works and gives the shape of the effect; it does not establish a precision figure. A credible number needs **40+ hand-labelled real snippets** (drawn from the corpus built in `16_live_signal_experiment.md`), which is a two-hour job and should be done before the pitch.
- **The rubric was written by someone who already knew the answer.** That is legitimate — it mirrors exactly what a sales team does when they configure a question — but the evaluation sample must not be drawn from cases used to write the rubric, or the number is meaningless.
- **Both runs were single calls.** The service documents that it does not guarantee identical answers across calls; a real pipeline should score each unit a small odd number of times and take the modal answer where the decision is close.
- **The `instructions` field is documented for the `dimensions` form of the endpoint**, which is what was used here. The plain label form (Run A) has no instructions parameter.

## What to build from this

1. **Make the rubric a first-class object in the product.** A "signal question" is not a keyword and not just a label — it is a label plus instructions. The Studio UI should show both, because the instructions are where the sales team's expertise lives.
2. **Ship the calibration gate.** Confidence < 0.7 → "needs review", never "hot lead". Say the threshold out loud in the pitch; it is the difference between a scoring toy and an operational tool.
3. **Run the before/after live if there is time.** The contrast between Run A and Run B is the most persuasive 20 seconds available: same text, same model, one sentence of business context added, answer flips from wrong to right.
