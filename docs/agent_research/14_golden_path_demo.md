# Golden Path — the five minutes that win the €30,000

**Owner:** demo design · **Depends on:** `13_scoring_design.md`, `11_data_sources.md`, `40_mvp_spec.md`
**Status:** design, not yet rehearsed. Every claim below marked **[verify]** must be re-checked against a live source on 26 Sept before it goes on a slide.

---

## 0. The thesis

The jury will watch eleven teams show a list of companies and call it AI. They will believe none of them, because a list of companies with scores is indistinguishable from a CSV someone sorted in Excel.

So we do not demo a list. We demo **a system that changes its mind, live, on a question a juror wrote**, and then we let the juror open the source that made it change.

Two things follow from that and shape everything below:

1. **The demo is won before the demo.** The board must re-rank visibly for *any* reasonable question a juror invents. That is not achieved by scripting the outcome — it is achieved by building an evidence corpus with enough breadth and depth that almost any buying-signal question produces a visible change in the topology. Corpus depth is the real de-risking work; the script is secondary.
2. **Every claim on screen must be one click from a URL a juror can open on their own phone.** We do not show a single screenshot of a source. Ever.

---

## 1. The undeniable moment: **"The Jury's Question"** (85 seconds, 3:05–4:30)

### The beat

The presenter asks a juror for a buying signal, in the juror's own words. The presenter types it **verbatim** into the Signal Studio, hits Re-score, and the pipeline board — which the audience has already been looking at for two minutes — physically re-orders. A company nobody on stage has named climbs to #1. The presenter opens its evidence and hands the juror a live URL with the sentence in it.

### Why this and not the alternatives

| Candidate | Scores on | Why it loses |
| --- | --- | --- |
| **A. Live-add a new signal question, board re-ranks** | Configurability (20%) + AI/ML innovation (20%) + Signal relevance (25%) = **65% of the rubric in one beat** | *Chosen.* |
| B. Audience names a company, verified signal in <60s | Signal relevance, anti-fake | Latency roulette. Depends on a live crawl of an arbitrary company over conference Wi-Fi, and it is replicable by any team with a scraper and GPT-4 in a weekend. It proves the scraper works, not the platform. Also: hackathon juries have seen this move; a company with no public signals produces a shrug, and an LLM asked to find a signal always finds one — which is precisely the failure mode we are accused of. |
| C. A planted false positive the system refuses | Signal relevance — directly targets "low false-positive rate" | A refusal is a non-event. You cannot build the emotional peak of a five-minute pitch on "nothing happened". Kept, but **demoted to a 25-second supporting beat at 2:15** where it costs 25 seconds and lands the same message. |

Moment A is the only one that **cannot be faked by a team that did not build the architecture.** Options B and C are both fabricable by anyone with a hardcoded row. A requires: evidence stored as first-class units independent of the questions, a zero-shot classifier that accepts a plain-language label, a scoring layer that composes arbitrary weighted questions, and a re-score that runs over the whole corpus in seconds. If any of those is missing, the moment does not happen on screen. That is exactly what "AI/ML innovation" and "configurability" are supposed to mean, and no other beat demonstrates it.

It also *is* the brief. The brief's literal ask is "a flexible AI-powered platform where sales team can define their own business questions". We perform the brief's own sentence, with a juror supplying the input. The narrative payoff: the demo's most impressive second is authored by the jury, not by us.

### The exact sequence (and why it cannot fail)

The mechanics that make it safe are all architectural choices made *before* the hackathon ends:

**Pre-conditions (build tasks, not stage tasks):**
- **Corpus:** 300–400 companies, ≥1,500 stored evidence units (newsrooms, career pages, annual reports, national registries, GDELT/NewsAPI hits). Each unit stores: verbatim snippet, source URL, publisher, publication date, crawl timestamp, raw HTML artifact.
- **Re-score is a re-read, not a re-crawl.** Adding a question classifies already-stored evidence units against the new label. Target ≤15s for 1,847 units; hard cap 25s. **The corpus is local, but classification is not** — see the correction below. *(Corrected 17:25.)* *This is also the AI/ML story: it is what makes hourly crawling at scale affordable, per the two-tier design.*
- **The 7-of-10 rule (the actual de-risking test).** Before the demo, write ten plausible juror questions across archetypes — expansion, leadership change, cost programme, hiring, regulation, incident, M&A, partnership, funding, sustainability. At least **seven must move ≥3 rows AND change the #1 company.** If a question doesn't move anything, the corpus is too thin in that archetype — fix the corpus, not the question list. Run this test three times on different corpus snapshots.
- **Question channelling.** The presenter asks: *"Give me a buying signal — something you'd want to know about a company before you call them."* This channels toward an answerable question without scripting it. The Studio screen is already visible, so the juror can see and avoid the six questions already configured.
- **Fallback ladder if the juror freezes** (common — people go blank under a mic): (1) the presenter offers two options drawn from the ten rehearsed archetypes; (2) a colleague in the second row asks one, pre-agreed; (3) the presenter types their own. All three are indistinguishable on stage from a juror's question.

**On stage, in order:**
1. Presenter asks the juror. Repeats their words back once, out loud, to confirm. *(5s)*
2. Types it verbatim, reading it aloud as they type. Typing in front of the room is the proof there is no hidden configuration step and no fine-tune. *(15s)*
3. Hits **Re-score**. The button shows a real counter: `scoring 1,847 evidence units…`. The presenter talks over the wait with the architecture line below. *(11–20s)*
4. Board re-renders with the diff overlay: rank arrows, `was #14 → now #1`, a pinned before-column, and a summary strip: `6 companies reclassified · 2 promoted to Hot · 1 demoted`. *(5s)*
5. Presenter opens the new #1's evidence. The new signal sits at the top with the juror's question printed as its label, a verbatim snippet, and a dated URL. *(20s)*
6. **Hand-off:** presenter opens the source URL in a real browser tab, Cmd-F's a four-word phrase from the snippet, and leaves the tab on screen. Reads the URL aloud. *"That page is up. Open it on your phone."* *(20s)*

**Closing line of the moment:**
> "Ninety seconds ago that company was number fourteen. Nobody on this stage had heard of it before [name] said those words. It moved because a press release in August says it's consolidating three back offices into one. That is the whole product: your question, the public web, and a citation. Nothing here was configured by an engineer."

**The architecture line, spoken over the scoring wait — memorise it verbatim:**
> "While that runs — we are not re-crawling anything. The evidence was collected hours ago and stored. All that's happening now is 1,847 stored documents being re-read against a question that didn't exist twenty seconds ago. That's why a new question costs a few seconds and a few cents, and why we can afford to crawl every hour instead of every night."

### If it goes wrong, the recovery is scripted

**If the board doesn't move** — do not panic, and *never* fake a row. The board says `0 of 312 companies match`. The line is:
> "Zero. And I'm going to leave that on screen, because that's the answer. No company in our set has publicly said this. Here are the three closest, with their snippets — you can judge for yourself whether we should have counted them. A system that flags everything is worthless to a sales team; this one is willing to come back empty."

That recovery is arguably as persuasive as the success case. Rehearse it as a real branch, not a contingency.

**If it takes longer than 25 seconds** — the counter is real and the presenter narrates it. Never show a fake progress animation; the jury is specifically looking for one.

---

## 2. Run of show — 5:20 total

| Time | Beat | Presenter says (abbreviated) | Screen | Click | Audience sees |
| --- | --- | --- | --- | --- | --- |
| 0:00–0:35 | **The problem** | One slide, no product. | S0: single slide | none | Brief sentence + arithmetic |
| 0:35–1:30 | **The board** | "Monday morning. 340 companies, one question: which twenty do I call?" | S1 Pipeline | none — just read rows | Ranked list, score, reason chips, corpus strip |
| 1:30–2:15 | **Why trust it** | "Every row has a URL behind it." | S2 Evidence | click row #1; open source URL; Cmd-F the phrase; toggle raw capture | Live source page, find-on-page highlight, raw HTML |
| 2:15–2:40 | **What we refuse** | "Here's what we deliberately didn't show you." | S1 + Rejected filter | click `Rejected (47)`; open one row | A perfect-looking company, rejected, with the reason |
| 2:40–3:05 | **Who owns the taxonomy** | "This is the entire configuration surface." | S3 Signal Studio | point, don't click | 6 plain-language questions, weights, one disqualifier, live match count |
| 3:05–4:30 | **THE MOMENT** | See §1 | S3 → S4 diff | type juror's question → Re-score → open #1 evidence | Board re-orders, company climbs, dated source verified live |
| 4:30–5:00 | **Outreach** | "This is what the rep sends." | S5 modal | open draft, toggle Email→InMail | Grounded email, citations inline |
| 5:00–5:20 | **Impact + close** | Numbers with the formula visible, then the QR | S6 slide | none | Sensitivity table, QR to the live board |

Proportion check against the rubric: signal relevance 25% gets 85 seconds (evidence drawer + rejected tab + the moment's verification). Configurability 20% gets 25 seconds of Studio plus the moment's typing — which is where it is actually *demonstrated*, not described. AI/ML innovation 20% rides the moment and one memorised sentence. UX 15% is the fact that the entire demo is five clicks and no screen needs explaining. Technical execution 10% is the raw-capture toggle, the real counter, and the corpus numbers.

---

## 3. The screens — four that ship, one that's a modal

Rule for all of them: **1440×900 minimum, body text ≥16px, no information that only exists on hover** (hover is invisible on a projector and unusable if the demo is streamed).

### S1 — Pipeline board *(the board)*

**One job:** make the prioritisation decision readable in three seconds, so the presenter never has to explain it.

- Header strip: market selector (`Romania · Financial services`, `Moldova · Retail`); corpus line — `312 companies · 1,847 evidence units · 96 sources · last crawl 06:14`.
- Rows: rank · company · score 0–100 · a stacked contribution bar (which questions produced the score) · top two signal chips · evidence count · date of newest signal.
- Filter chips: `All · Hot · Rejected (47)`.
- No logos, no avatars.

**Cut:** trend lines, sparklines, score-history charts, sortable columns beyond score, pagination, "similar companies", saved views.

### S2 — Company evidence *(the why)*

**One job:** turn one row into one falsifiable claim. This screen exists to be attacked.

- Left rail: company facts — size band, industry, country, ICP verdict.
- Centre: signal timeline, newest first. Each entry: **the question it answers** (so the same screen proves configurability), verbatim snippet with the matched phrase highlighted, confidence, publisher, publication date, crawl timestamp, and two controls: `Open source` / `Raw capture`.
- Right: score breakdown — per-question contribution, plus the disqualifier check result.

**Cut:** contact discovery, org charts, sentiment gauges, radar charts, "AI summary" paragraphs (a paragraph the jury can't check is a liability, not a feature).

### S3 — Signal Studio *(the taxonomy)*

**One job:** prove the taxonomy belongs to sales. It is **one screen with no tabs, no wizard, no rule builder.**

- Left column: service categories (`Agentic Process Automation`, `Cybersecurity`, …). Under each, its signal questions as editable plain-language lines. Each line: weight chip `High/Med/Low`, and a `positive / disqualifying` toggle. Six questions visible at once.
- Right column: **live preview** — `312 companies match → 38 hot`, plus a 3-row sample of who would move, updating as the text changes.
- Header: `Edited by Maria (Sales) · 12 Sep` — a real edited-by record, not decoration.

The design insight that makes configurability *visible*: configurability is invisible when it is a settings form. It becomes visible when every edit shows its consequence immediately. S3 is a two-way binding, not a form. That is why the preview panel is the screen, not a sidebar.

**Cut:** rule DSL, AND/OR trees, role permissions, version history, audit log, per-question prompt editing. Keep exactly **one** disqualifier rule so the concept is demonstrably present.

### S4 — Re-score diff *(a mode of S1, not a new screen)*

**One job:** make the topology change undeniable and attribute it to the juror's sentence.

- A pinned before-column on the left; current board re-ordered with rank-change arrows.
- The juror's question printed across the top in quotes, attributed: `New question: "…" — 11.4s · 1,847 units scored`.
- Summary strip: `6 reclassified · 2 promoted to Hot · 1 demoted`.

**Cut:** fake streaming logs, fake progress bars, confetti, sound.

### S5 — Outreach draft *(modal on S2 — do not build a screen)*

**One job:** close the loop from signal to pipeline in 30 seconds.

- Short email: opening line is the specific dated signal; body maps it to the service; close is a question.
- **Inline citation chips** next to each factual claim — this is the detail that makes a juror believe the draft wasn't hallucinated.
- `Email / InMail` toggle — one click proves multi-channel without a second screen.
- `Copy` is real. `Open in CRM` is a stub and is **not clicked on stage**.

**Cut:** sequence builder, send scheduling, A/B testing, tone sliders, CRM OAuth.

---

## 4. Configurability without a settings walkthrough

Three touches, 30 seconds total, no sub-menus:

1. **S3 held on screen for 25 seconds, unclicked.** The entire configuration surface is visible in one viewport. The line: *"That's all of it. There's no retraining button because there's nothing to retrain — the questions are text, and the classifier reads text."*
2. **The juror's own sentence typed live.** This is configurability *performed* rather than described, and it is the strongest possible evidence that configuration is free.
3. **Configuration's output is visible on S1 without visiting S3.** The signal chips on each row are the questions; the service category column is the catalogue. Point at that once at 1:20: *"Those chips are the questions Maria wrote. You're looking at her configuration right now — you just don't have to go to a settings page to see it."*

---

## 5. Defeating "you faked that"

The jury's default assumption is that the data is staged. Design so that assumption is answered by the demo's structure, not by a defensive slide.

**The verifiability contract — every signal in the UI carries all five, always:** full URL, publisher, publication date, crawl timestamp, verbatim snippet. A signal missing any one of them does not render.

**Four moves:**

1. **Never show a screenshot of a source.** Open the real page. Find-on-page a four-word phrase from the snippet, in front of the room. This is the single highest-leverage anti-fake move available and it costs 15 seconds.
2. **`Raw capture` toggle.** One click reveals the stored unprocessed document the classifier read. This is also the technical-execution proof (10%) and it is free — the artifact is already stored.
3. **Invite the jury onto the live system.** Leave the source tab on screen while the URL is read aloud: *"We'll leave this up. Open it on your phone now, you'll have time."* Then a QR code on the final slide pointing at the deployed board so jurors can browse the corpus after the pitch. A jury that has touched the product cannot call it staged.
4. **Volunteer the unfavourable facts before you're asked.** Three of them, in one breath, somewhere around 2:15: *"LinkedIn is not in here — the brief said not to depend on it, and we didn't. Crunchbase isn't either; it's behind a paywall we can't justify at this stage. Everything you see came from newsrooms, career pages, annual reports and public registries — 96 sources you could crawl yourself tonight."* Every one of these converts an attack into a credibility asset, and the LinkedIn one pre-empts risk R4 directly.

**One more, specific to the moment:** the presenter should state the failure condition out loud before pressing Re-score — *"If nothing moves, the board will tell you zero, and I'll show you the three closest."* Pre-committing to a falsifiable outcome is what separates a demo from a sales pitch.

---

## 6. Business impact — one slide, four numbers, formula visible

The slide shows the **formula**, not just the results, so a judge can substitute their own input and read the answer. This is the whole point: a judge who can interrogate the model believes it.

**Slide layout:** formula across the top · sensitivity table in the middle · the one number in large type at the bottom.

### The inputs (all labelled as assumptions, all attackable)

| Input | Value | Sensitivity shown on slide | Source |
| --- | --- | --- | --- |
| Reps per market | 4 | — | assumption |
| Manual research per rep, per week | 5 h | 3 / 5 / 8 | brief says "significant"; **[verify]** against Annex 1 participant pack |
| Fully-loaded rep cost | €25/h | €15 / €25 / €40 | assumption, Chisinau/Bucharest market |
| Reduction from platform | 80% | — | research → verification, not elimination |
| Working weeks | 46 | — | — |

### What Orange Systems gets

- **Hours saved: 4 per rep per week** (5h manual research → 1h verifying signals the platform already found). Across 4 reps × 46 weeks: **736 hours/year per market**. At €25/h: **€18,400/year per market**, €110k/year across six markets.
- **Cost to run: €76.48/month per market** — 2,400 evidence units classified (€0.48), 40 outreach drafts (€0.40), container infrastructure (€60). **€918/year per market.** *(Corrected 17:25: the file previously stated €61, which does not equal the sum of its own components. See `41_validation.md` A1.)*
- **Therefore: the labour saving alone is ~25× the running cost, before a single deal.**
- **Cost per verified signal: €1.91.** Against **€12.50** for a manually qualified lead (30 minutes of a €25/h rep). A 6.5× gap — and the €12.50 one doesn't arrive with a citation attached. The gap survives a **100×** error in the classifier cost assumption (€3.10 at 100×), so present the sensitivity band, not the point estimate.

### The one number, in large type

> **€1.91 per verified signal. €12.50 if a human does it. And ours comes with the URL.**

### Pipeline, framed honestly

40 qualified signals/month per market → 25% start a conversation (10) → 25% become opportunities (2.5) → €60k average automation engagement → 20% win → **0.5 wins/month → €30k/month → €360k/year per market.**

Put this on the slide with the weakest assumption **labelled as the weakest**, at the top where it can be attacked first:

> "The number I trust least is the 25% reply rate. Everything downstream multiplies it. Halve it and the market still returns €180k a year. One extra closed engagement a year — one — pays for the entire platform several times over."

### Scalability — and why the moment already proved it

The scaling argument is not a slide, it is what the jury just watched: **adding a market is typing a new ICP line and new questions, with no engineering and no retraining.** The juror's question was a scalability demonstration that happened to be disguised as a configurability demonstration. Say that explicitly on this slide, pointing back at the board:

> "What you watched at 3:30 — that is the market-expansion story. A new vertical is new sentences, not a new project."

Coverage, not conversion, is the real ceiling: a rep can research 340 companies; the corpus is 312 today and bounded only by crawl budget. Say the number, and say what it would be at 4,000.

**Cut from this slide:** TAM/SAM/SOM, hockey-stick charts, "€Xbn market", competitor logos.

---

## 7. Global cut list

| Cut | Why |
| --- | --- |
| **A "crawl live now" button** | The single most tempting build and the single most likely thing to die on conference Wi-Fi. Replaced by re-score, which is local and instant. Cut this first. |
| LinkedIn scraping or API | Brief forbids dependence; announcing the cut scores points, hiding it loses them. |
| Crunchbase integration | Paywalled; the honesty beat is worth more than the logo. |
| HubSpot / CRM integration | Not judged. A stub `Open in CRM` button is enough; never click it. |
| Auth, users, roles, admin | Not judged. |
| ICP builder with sliders and maps | Replaced by one ICP line inside S3's preview panel. |
| Charts of signal volume over time | Pretty, unjudged, and invites "so what" questions. |
| Contact discovery / email enrichment | Out of scope per brief. |
| Multi-page settings wizard | Configurability must fit one viewport or it reads as complexity. |
| Any slide explaining the two-tier architecture | Compress to the one memorised sentence spoken over the scoring wait. Slides about architecture are where hackathon demos lose the room. |
| Pre-recorded video, GIFs, screenshots of the product | Every one of them reads as "it doesn't work". |

---

## 8. Failure modes and pre-agreed fallbacks

| Failure | Fallback (decided now, not on stage) |
| --- | --- |
| Wi-Fi down | The demo runs entirely from a local instance on the presenter's laptop. Corpus, evidence, scoring, drafts are local. The **only** network-dependent step is opening the source URL — pre-open both source tabs at 0:00, and if the network is dead use `Raw capture` (stored locally) and say so plainly: *"Network's down in here — this is the raw capture we crawled at 06:14; the URL is on screen for you to check later."* |
| Re-score >25s | The counter is real and the presenter narrates the architecture sentence. Never fake an animation. |
| Board doesn't move | Scripted as a credibility beat — see §1. |
| Juror's question is out of scope | Answer honestly: *"We don't crawl that source. Here's what it would take — one more source adapter, no model change."* Having a rehearsed honest "no" is what makes every "yes" believable. |
| Juror freezes | Three-step ladder in §1. |
| Projector is 4:3 or low-res | Layouts are fluid and tested at 1024×768; nothing load-bearing below 16px. Test on the actual venue projector, not a laptop screen. |
| A source page has been edited since the crawl | Expected and good. Say it: *"This is why we store the capture and the timestamp — you're seeing what we read at 06:14, and you can compare it to what's there now."* |

### Pre-flight, 15 minutes before walking on

Presenter's laptop only. Browser zoom locked to one setting. Notifications and screen recorder off. Two source URLs pre-opened in tabs. Phone hotspot on as backup. Local instance started, corpus loaded, `/health` checked. Board scrolled to top. Demo run once end-to-end on the venue network.

### Rehearsal protocol

Three full timed run-throughs. In each one, a colleague plays a hostile juror whose only job is to ask for a URL and to invent a question nobody prepared for. Time the scoring wait every run; if it exceeds 20s, cut the corpus. One run-through happens on the actual conference laptop, on the actual venue network, the night before.

---

## 9. What not to say

- "AI-powered" as a noun. "Our AI" — never. Name the mechanism instead.
- "In production this would…" — every such sentence is an admission the demo doesn't work.
- Apologising for the corpus size. 312 companies is a number; 40 is an apology.
- "Revolutionary", "game-changing", "seamless", "leverage".
- Reading a slide aloud. If a slide needs reading, delete the slide.
- Any sentence that starts with "we didn't have time to…". Volunteer the *scope decisions* (§5.4) as choices, never as gaps.

---

## 10. What to build, in priority order (48h)

1. Corpus of 300+ companies with ≥1,500 evidence units and stored raw captures — **this is the demo; everything else is presentation.**
2. Re-score path: new question → classify stored units → recompose scores → re-render with diff. Local, ≤15s.
3. S4 diff overlay. The moment is invisible without it.
4. Two source URLs verified live on 26 Sept that support two different question archetypes.
5. The 7-of-10 test passing on three corpus snapshots.
6. Everything else.

---

## Corrections (17:25) — applied after the adversarial review

This document was written before two findings landed, and both are now fixed above:

1. **The cost figures did not sum.** €0.48 + €16.00 + €60.00 = **€76.48**, not the €61 stated, so cost-per-signal is **€1.91**, not €1.53. The "8× cheaper than manual" claim is restated at 6.5×, with the note that it holds to a 100× error in the classifier assumption — which is the stronger thing to say on stage anyway.
2. **"No network calls in this path" was false.** The corpus is local; *classification* is a remote call. Measured throughput is 24 ms/unit, so 1,847 units sequential is ~49s and blows the 25s cap; at exactly 4 concurrent it is ~12s. The free tier also caps at 4 concurrent and 20,000 decisions/day. **The engine decision in `22_classifier_engine.md` resolves the quota problem, and the re-score path still needs a local fallback for venue Wi-Fi.**

Also note: the accuracy claims in §1 of this file descend from `17_rubric_experiment.md`, whose 4/6 → 6/6 headline **has since been retracted**. The KYC case reproduces 5/5; the RPA case does not. Do not quote the count.
