# Service-Scoped Signal Policy — the wedge, demonstrated

Run 2026-09-25, live, against the same free keyless endpoint used in `17_rubric_experiment.md`. Ten lines of request, one second of latency, four-for-four correct.

## Why this experiment exists

The competitive research (`12_landscape.md`) established that **plain-language custom signal questions are no longer novel** — Clay shipped Custom Signals in May 2025, HubSpot in June 2026 and hired Trigify's team days ago. Pitching configurability as the innovation would be pitching a feature two incumbents already sell.

The gap that research identified as genuinely unshipped was this pair:

1. **Per-service-line scoping.** Every incumbent computes one global account score. The same evidence should be able to mean *buy* for one service line and *nothing* for another — because a company drowning in manual KYC work is a lead for onboarding automation and completely irrelevant to anyone selling network security.
2. **Disqualifiers that override positives.** When a company announces it is building the capability **in-house**, that is not a weak positive — it is a competitor signal, and it should knock them off the list. No verified product does this; the pattern exists only in consultancy blog posts.

Both were asserted by research. Neither was proven. So they were tested.

## The experiment

Four realistic texts, each scored simultaneously against **two independent service rubrics** in a single call — one dimension for automation services, one for cybersecurity services. Each rubric had three outcomes: `buy`, `build` (insourcing → competitor, disqualify), `none`.

## Results

| Evidence | Automation verdict | Cybersecurity verdict |
| --- | --- | --- |
| "200 analysts on manual KYC review, hiring 30 more to clear the backlog" | **buy (1.00)** | none (0.93) |
| "Launching an **in-house** automation centre of excellence, hiring 20 RPA developers to bring automation in-house" | **build (1.00)** | none (0.99) |
| "Major data breach, under regulatory investigation over security failures" | none (0.99) | **buy (1.00)** |
| "Hiring a Business Development Representative for outbound prospecting" | none (1.00) | none (1.00) |

**Four properties demonstrated, each one a scored requirement:**

1. **Polarity is per-service, not per-account.** The KYC backlog is a 1.00 lead for automation and correctly *nothing* for cybersecurity. The breach is the mirror image. A single global score physically cannot express this, and the incumbents' single global score is the gap.
2. **The disqualifier works, and it is decisive.** The in-house centre of excellence — a company that will never buy automation services because it is building them — returns `build (1.00)`. It is removed, not down-weighted. This is the mechanic no competitor ships.
3. **The negative control holds.** An ordinary sales role scores `none/none` at confidence 1.00 in both dimensions. The system is not simply agreeing with whatever it is asked.
4. **It is cheap enough to be honest about.** Four texts × two service lines resolved in about a second, on free infrastructure with no key. Per-service scoping is not an expensive feature; it is a second dimension in the same call.

## What this is worth in the pitch

This is the answer to the two questions that would otherwise end the pitch early:

- *"How is this different from Clay's Custom Signals?"* → Theirs is one score per account. Ours is a policy per service line, over the same evidence, and it can return **build** — a competitor signal that disqualifies — which no shipped product does.
- *"How do you keep false positives down?"* → By letting the sales team write negative rules that outrank positive ones, and by gating on calibrated confidence. Here is the mechanism running.

It also lands the judging weights cleanly: **Configurability 20%** (the rubrics *are* the configuration, per service line) and **Signal Relevance 25%** (the disqualifier is the highest-value precision tool in the system) — 45% of the rubric, demonstrated with a reproducible experiment rather than claimed.

## Honest limitations

- **n = 4.** Same caveat as the rubric experiment: this demonstrates a mechanism, it does not measure precision. The real evaluation needs 40+ labelled snippets from the corpus.
- **The rubrics were written by the test author**, who knew the intended answers. In production the sales team writes them, which is the point — but the accuracy claim should be measured on examples not used to author the rubric.
- **Single call per case.** The endpoint does not guarantee identical answers across calls; a production pipeline should re-score close decisions and take the modal answer.
- **`build` is a hypothesis about intent.** A company insourcing may still buy services later; the honest framing is "not a lead *now*", which is what the dismissal reason should say.

## Build implications

1. **Signal policy is keyed by service line, not by company.** The data model needs `policy(service_line) → {questions[], disqualifiers[]}`, evaluated over shared evidence. This is the architectural difference from every incumbent, and it should be visible in the schema.
2. **`build` deserves to be a first-class state**, not a low score. It has its own demo moment: the rejected company that looks perfect and is dismissed with a reason.
3. **Dismissal reasons are evidence too.** The rejected list is not a graveyard; it is proof the system says no, and it is the most credible screen in the demo — the golden path already plans a 25-second beat for exactly this.
