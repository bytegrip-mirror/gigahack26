# Evaluation — first measured precision/recall, and why to distrust it

Run 2026-09-25 on real job postings pulled live from company job boards. **Read the bias section before quoting any number here.**

## Design

A balanced set of **20 real titles** (10 signals, 10 non-signals), taken from a live crawl of 2,396 postings across ten companies. Ground truth was **my own expert judgement, assigned before the classifier was run**, using the definition: *does this role exist because of high-volume manual back-office work that an automation/onboarding vendor is paid to automate?*

The classifier received the titles and a plain-language rubric (the same `instructions` mechanism as `17_rubric_experiment.md`), which explicitly excluded engineering, marketing, sales, legal and recruiting roles, "automation" that means software the company builds, and "compliance" that means employment/ethics/privacy rather than financial crime or customer onboarding.

## Result

```
confusion: TP=10  FP=0  TN=10  FN=0
precision = 1.00   recall = 1.00   accuracy = 1.00   (n = 20)
```

Every one of the ten KYC / AML / financial-crime / due-diligence roles was accepted; every one of the ten engineering, marketing, legal, sales and ethics-compliance roles was rejected — including the three that had fooled keyword matching in the earlier experiment ("Marketing Automation Manager", "Network Automation", "Sales Operations Analyst").

## The bias, stated first

**Do not quote 1.00 on stage as an accuracy figure.** It is a demonstration, not an evaluation, for three reasons a competent judge will spot:

1. **The rubric and the labels came from the same head.** I wrote the rubric knowing which cases it had to get right. That is legitimate for showing that a rubric *can* encode the distinction, but it measures my ability to write a rubric for cases I already knew, not the system's accuracy on cases it hasn't seen.
2. **n = 20 is small.** One error moves accuracy by 5 points. The confidence interval on 20 items is roughly ±20 points, so "1.00" and "0.80" are not statistically distinguishable here.
3. **Titles only.** Real decisions are made on the posting body, where the noise lives (19:1, per `16_live_signal_experiment.md`). Title-only is the easy mode.

**What the result does legitimately support:** the mechanism works end-to-end on real, current, third-party data, with no key and no cost, and it separates the two classes that keyword matching provably cannot. That is a real claim. "Precision 1.00" is not.

## An honest behaviour worth showing a jury

Two genuine signals were accepted but with **low confidence**: "Data Science Manager, Financial Crime" at 0.04 and "Compliance Manager, User Enablement" at 0.13. Under a 0.7 gate — the design recommended in `17_rubric_experiment.md` — both would be routed to *human review* rather than straight to the hot list.

That is the system behaving correctly and unglamorously: it is not confident about a data-science role and an enablement role even though a human would count both. **Say this out loud in the pitch.** A scoring engine that reports high confidence everywhere is the thing juries distrust; one that admits two of its ten hits are marginal is the thing they believe. It is also the honest answer to "what happens with a false positive?" — it goes to review, not to a rep's call list.

## The evaluation protocol to actually run before the pitch

The scoring design (`13_scoring_design.md`) calls for **60 snippet labels + 30 company labels, about six person-hours**. Concretely, and in this order:

1. **Sample without looking at the rubric.** Pull 60 postings at random from the built corpus. Label them before opening the rubric file, or the bias above repeats at a larger n.
2. **Include the hard cases deliberately.** Half should be drawn from the near-miss classes: marketing/network/database "automation", ethics/privacy "compliance", operations-analyst roles, and insourcing announcements. Easy negatives inflate the score.
3. **Report Wilson intervals, not point estimates.** With 60 items an interval is honest; a bare "0.93" invites a challenge no one can answer on stage.
4. **Publish the false-positive root causes.** For every FP, name the pattern that caused it. A short list of known failure modes is more credible than a clean number, and it feeds the disambiguation rules.
5. **Re-measure the calibration claim.** The vendor's calibration figures are vendor-claimed; if the pitch leans on the 0.7 gate, verify the gate on our own labels first.

## What is safe to claim today

| Claim | Safe? |
| --- | --- |
| "Zero-shot classification with a plain-language rubric separates genuine buying signals from boilerplate on real postings" | **Yes** — demonstrated three times, on live data |
| "Keyword matching cannot do this — measured 19:1 noise" | **Yes** — measured, reproducible |
| "Our classifier is 100% precise" | **No** — n=20, self-authored rubric |
| "Confidence is usable as a review gate" | **Yes, with a caveat** — it correctly marked two marginal hits as low-confidence; the threshold itself still needs validation on more data |
