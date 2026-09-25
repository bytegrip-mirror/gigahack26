# New Ideas — the ones a jury will remember

Every idea here is grounded in something measured in this research, and each names the rubric criterion it buys. Ranked by expected return on build time. None of them is "add AI to a dashboard".

---

## A. Grade us on outcomes, not on our own rubric — the backtest

**The idea.** Instead of claiming accuracy, *demonstrate* it retrospectively. Take the signals the system would have flagged 90 days ago, then show what actually happened to those companies: did they post more automation roles, announce a transformation programme, hire a COO, get fined, raise a round? Compare against a random control set of the same size. The slide is a hit-rate comparison, not a precision claim.

**Why nobody else will have it.** Every other team will present a precision number computed against their own labels — a self-marked exam, and a jury knows it. A backtest moves the ground truth outside the system: the world grades it, not the team.

**Why it is affordable.** ATS APIs return dated postings, and news sources carry publication dates, so historical evidence is obtainable without a time machine. The corpus build already crawls this data; the backtest is a query over what is stored, plus a control sample.

**Serves:** Signal Relevance & Accuracy (25%), Technical Execution (10%).
**The line:** *"Don't trust our number. Here's what happened to the twenty companies we would have called in June, and here's the same chart for twenty we picked at random."*

**Honest risk:** at 90 days and small n, the difference may not be statistically visible. Even a null result is defensible if presented as measurement rather than proof — and it is still the most scientific slide in the room.

---

## B. Make the disqualifier the hero — demo the negative space

**The idea.** Every team demos what the system found. Lead with what it **refused**. A company that looks like a perfect lead on every positive signal — big, funded, hiring in operations — shown rejected, with one line: *"They announced an in-house automation centre of excellence in March. They are not buying. They are competing."*

**Why it is the strongest available differentiator.** The competitive research found that no shipped product implements negative rules that override a positive score, and that insourcing announcements are precisely the signal that flips a company from lead to competitor. It is also the finding verified live: the same text returned `build (1.00)` — a competitor signal — rather than a lead.

**Why it lands emotionally.** A rep's real pain is not "I can't find leads", it is "I wasted three weeks on a company that was never going to buy". The demo speaks to the wound, not the want.

**Serves:** Signal Relevance & Accuracy (25%), AI/ML Innovation (20%).
**The line:** *"A lead list is easy. The valuable thing is a system willing to tell you no — and to say why."*

---

## C. Point it at the product that already won them an award

**The idea.** Build the signal taxonomy around **InSight KYC** — Orange Systems' own onboarding-automation product, the one that cut onboarding from 8 hours to under 1 hour, saved 2M hours a year, won Best Service Innovation, and made their hub the group's RPA Center of Excellence. The ICP and questions are not generic "digital transformation"; they are *"whose onboarding is visibly breaking?"* — KYC/AML hiring at volume, financial-crime review backlogs, regulatory fines, expansion into new markets with new compliance obligations.

**Why it is unusually strong.** The research found this is the highest-precision signal family on the open web *and* it maps exactly onto what the client is best known for selling internally. The platform becomes the front door to their flagship product rather than a generic prospecting tool — which is the difference between "interesting demo" and "we want this on Monday".

**Serves:** Business Impact (10%), Signal Relevance (25%) — and it is the clearest possible answer to "what would you do with it after the hackathon?"

**The line:** *"This finds the companies your InSight KYC pitch was written for."*

---

## D. The decay clock — every lead says *why now*

**The idea.** A job posting from today and one from eight months ago are not the same evidence. Every signal carries an age, a half-life, and a visible decay. The lead card answers *"why should I call this week?"* with a dated, decaying reason rather than a static score.

**Why it is rare.** Incumbents sell static intent scores. Timing is what a rep actually acts on, and a decaying signal is a *falsifiable* claim about urgency — it can be wrong in a way a score cannot.

**Affordable:** the scoring design already derives the half-life from a buying window rather than picking a number, and the arithmetic is a two-line formula a juror can check on a phone.

**Serves:** Usability & UX (15%), Signal Relevance (25%).
**The line:** *"Scores don't close deals. Timing does."*

---

## E. "Show me the bytes" — proof, not summaries

**The idea.** No generated paragraph ever appears as an explanation. Every claim points at a **byte range in a stored raw capture**, and one click highlights exactly those bytes in the document the machine actually read. Because extraction is extractive — the model selects a span rather than writing prose — fabrication is not merely unlikely, it is structurally impossible.

**Why it wins the room.** The plan's own critique of the category is that LLM-written explanations are unfalsifiable. This is the answer, demonstrated: the audience watches a claim resolve to characters in a document.

**Serves:** AI/ML Innovation (20%), Technical Execution (10%).
**The line:** *"We don't ask you to trust a summary. Here are the bytes."*

---

## F. Publish the blind spots — coverage as a feature

**The idea.** The system reports what it **cannot** see: *"This company uses Workday, which we don't crawl — confidence penalised 40%."* Per-company coverage, honestly stated, with the penalty reflected in the score.

**Why it is disarming.** The measured reality is that slug resolution failed on 8 of 8 careers pages and two major sites returned 403. Every team will hide that. Displaying it converts the project's biggest weakness into evidence of rigour, and it pre-empts the killer question — "what about the companies you missed?" — with a screen rather than a stammer.

**Serves:** Signal Relevance (25%), Technical Execution (10%).
**The line:** *"A tool that claims to see everything is lying. Here's what we can't see, per company."*

---

## G. The market map — the closing move that reframes the product

**The idea.** Aggregate the same signals across the whole corpus and show the map: which countries, industries and company sizes show accelerating automation demand. The lead list becomes a by-product of a market-intelligence view.

**Why it is the right last slide.** It changes what the buyer thinks they are getting — from a rep's tool to a planning instrument that tells Orange Systems *where to point the sales team next quarter*. That is a different budget line and a different conversation. It also answers scalability (10%) visually, since a new market is a new row on a map the system already computes.

**Serves:** Business Impact & Scalability (10%), Usability & UX (15%).
**The line:** *"You asked for a lead list. Here's where the demand is moving."*

---

## H. The rep's rejection is training data — a visible calibration loop

**The idea.** When a rep marks a lead "not a fit", the question that produced it loses weight, and the change is visible on screen as the weights shift. The loop is closed in the product rather than claimed in an architecture diagram.

**Why it matters.** The scoring research names the calibration loop as one of only three genuinely unshipped capabilities in this category. It also converts the platform from a static configuration into something that learns from the team that uses it — the strongest possible answer to "what happens after we tune the questions?"

**Serves:** AI/ML Innovation (20%), Configurability (20%).
**The line:** *"When Maria rejects a lead, the question that produced it gets quieter. The taxonomy learns from the person who owns it."*

---

## The golden-path spine, if one is wanted

The research converges on a single narrative arc. Stated at the level of *what the audience should feel at each beat*:

1. **Recognition** — the SDR with forty browser tabs. Everyone in the room who sells has lived this. *(No product on screen.)*
2. **Relief** — the board, one screen, ranked, with reasons and citations.
3. **Suspicion** — the jury assumes it is staged. Answer it structurally: open a live source, find-on-page the phrase, leave the URL up.
4. **Respect** — the rejected company. The system says no, and explains why. This is where a sales audience decides the team understands their job.
5. **Surprise** — the juror's own question, typed live, the board re-ordering around it. **This is the peak.** It is authored by the jury, which is why it cannot be dismissed as scripted.
6. **Proof** — the bytes. A claim resolving to characters in a stored document.
7. **Ambition** — the market map, and the sentence that changes what the buyer thinks they are buying.

Steps 4 and 5 are the two that no other team will have, and they are the two that carry 45% of the rubric between them.
