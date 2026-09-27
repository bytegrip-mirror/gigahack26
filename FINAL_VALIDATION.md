# FINAL_VALIDATION — Nudge vs. the Gigahack 26 brief

**Audited:** 2026-09-26, against `main` @ `9d4b53d`, the live deployment
(`hack.pistonpunk.com` / `hackapi.pistonpunk.com`), the committed corpus
(`corpus/raw.json`, 578 companies / 4,466 documents; `corpus/signals.json`,
9,042 rows), the 17 live policy questions, and the whole `api/`, `ui/` and
`tools/` trees.

**Method:** every claim below is either (a) read out of the code with a
`file:line`, (b) measured against the live API, or (c) measured against the
committed corpus/overlay. Reproduction commands are in §9. Two independent
deep audits were run over the UI and the ingestion tooling; their findings are
folded in and the load-bearing ones were re-verified by hand.

---

## 1. The verdict, before the detail

| Criterion | Weight | Score | Weighted |
|---|---|---|---|
| Signal Relevance & Accuracy | 25% | **4/10** | 10.0 |
| AI/ML Innovation | 20% | **6/10** | 12.0 |
| Configurability | 20% | **7/10** | 14.0 |
| Usability & UX | 15% | **6/10** | 9.0 |
| Business Impact & Scalability | 10% | **5/10** | 5.0 |
| Technical Execution | 10% | **5/10** | 5.0 |
| **Total** | | | **55 / 100** |

**Can this win? Yes — but not in its current state, and not on the axis you
have been optimising.** The engineering is genuinely the best I have seen in a
hackathon build: the scoring arithmetic is defensible, the anti-fabrication
mechanism is real and works, and the configurability surface is a product, not
a demo. Those are worth a lot.

But the criterion carrying 25% of the prize — *"Quality and precision of
identified prospect signals; low false-positive rate"* — is where the system is
weakest, and it is weak in a way a juror from Orange Systems will find in about
ninety seconds. The failure is not in the scoring model. It is one layer
**below** it: **the platform does not reliably know which company a document is
about.** Everything downstream — the log-odds ledger, the theme cap, the
evidence spans, the model verdicts — is a precise machine computing over data
that is, in a material fraction of cases, about somebody else.

And the second-biggest risk is the demo itself: the single flow you will be
judged on — type a question into the Studio, watch the corpus re-rank — is the
one flow that, on the shipped default settings, produces thousands of false
positives in ten seconds.

**The single highest-leverage change:** make entity resolution real (domain
verification + a negative-name list + dropping the ATS-slug guess), and default
the ask flow to the language model. Those two changes are worth more than every
other item in this document combined.

---

## 2. What is genuinely strong — do not break these

These are real, verified, and rare. Lead the demo with them.

1. **Evidence is extractive by construction and the spans actually resolve.**
   I sampled ledger rows, fetched `/api/evidence/{id}/capture`, and sliced the
   capture at `spanStart:spanEnd`, comparing to the claimed quote: **12/12
   matched exactly**, with `spanVerified: true`. The "quote is a slice of what
   the crawler fetched, not text a model wrote" claim is **true**. This is the
   strongest asset in the codebase and no competitor will have it.

2. **Refusing to recommend vendors of the thing you sell works.** 92 companies
   are flagged competitors, and the list is right: UiPath, Nintex, Celonis,
   Appian, Automation Anywhere, Pegasystems, Accenture, Capgemini, Atos,
   Infosys, Wipro, Cognizant, EPAM, Endava, Globant, Thoughtworks, Reply,
   SoftServe, Intellias, N-iX, Amdaris, Devoteam — all correctly out of the
   queue, each with a stated reason. The brief's own trap ("the company already
   has high automation maturity") is handled.

3. **The scoring arithmetic is honest and defensible.** Additive log-odds on
   `logit(base_rate)`; `w = tier × authority × 2^(−Δt/H) × (2c−1)`; per-question
   rank discount at ρ; a theme cap **derived** from the Priority line rather than
   tuned; disqualifiers as **boolean gates outside the sum**, so a gated company
   still shows its score and its reason. Each correction has a test. A juror can
   check it with a calculator.

4. **The signal questions are written in a rep's language, not a model's.**
   "An announced efficiency programme", "Hiring for process excellence",
   "Building automation in-house", "A new security leader", "A reported security
   incident" — each with a `Counts: … / Does not count: …` rubric. This is the
   brief's "negative signals and disqualification rules" done properly, and the
   rubrics are why the model half works at all.

5. **The Why-now panel is exactly what the challenge asks for.** Verbatim from
   the live board: *"Consolidating shared service centres — computerweekly.com,
   published 10 days ago. Separately: An announced efficiency programme
   (published 4 months ago), and Hiring for process excellence (published 10 days
   ago). Two unrelated things pointing the same way is what separates this from a
   company that merely mentioned the subject once. On the evidence here it holds
   for about 109 more days."* That is an SDR sentence.

6. **The UI is honest about not knowing.** "no evidence" is tooltipped as *a gap
   in the crawl, not a finding*; failures are distinguished from empty sets;
   near-misses below the floor are shown in a `Read and not counted` panel so the
   floor can be argued with. CSV formula-injection is neutralised. Nothing is
   hover-only, nothing is below 11px.

---

## 3. Signal correctness — the findings that matter

### 3.1 BLOCKER — entity resolution is a lowercase substring test

There is no entity disambiguation anywhere in the ingestion path.
`tools/build_corpus.py:387`, `tools/fast_corpus.py:166` and
`tools/newsroom_corpus.py:222` all reduce to `if variant.lower() in text.lower()`.
Aliases are a hand-written list. There is no domain check, no negative-name
list, no country/industry cross-check.

This is not a theoretical risk. In the **shipped corpus**:

| Company on the board | Document filed as its evidence | Why |
|---|---|---|
| **SEB** (Swedish bank) | *"2026 49er, 49erFX and Nacra 17 European Championships… New Zealand's world champions **Seb** Menzies…"* (`yachtingnz.org.nz`, ×2) | alias `SEB` matched the first name "Seb" |
| **LPP** (Polish apparel) | *"Czech **LPP** Holding Develops Two Bigger-Warhead Narwhal Cruise Missile"* (`armyrecognition.com`) | name collision with a Czech defence firm |
| **LPP** | *"**LPP** Candidate Promises Rebates For Nigerians"* (`leadership.ng`) | a Nigerian political candidate |
| **Orange** (telecom) | *"Best **Orange** Cake Ever"* (`munatycooking.com`) | literal |
| **Nexi** (payments) | *"**NexI**mmune stock shows a 50.0 percent one-year loss"* | substring gluing, no token boundary |
| **VINCI** (construction) | 25 postings from `jobs.ashbyhq.com/vinci` = "Vinci… **building Physics AI for hardware design**" | ATS slug collision |
| **Atlas Copco** | 7 postings from `jobs.ashbyhq.com/atlas` = a US charge-card startup | ATS slug collision |
| **Maxima Grupė** | 17 postings from `jobs.ashbyhq.com/maxima` = a US accounting startup | ATS slug collision |
| **Novo Nordisk** | 11 postings from `jobs.ashbyhq.com/novo` = a fintech ("Senior Product Manager, Banking") | ATS slug collision |
| **DXC Technology** | postings from `job-boards.eu.greenhouse.io/**technology**/…` | `len(w) > 3` filter dropped "DXC", leaving `technology` |
| **Sunrise** (Swiss telecom) | *"Sunrise's intelligent robotic cells combine on-board AI…"* | a robotics manufacturer |
| **BP** | *"**Aker BP** ASA Q1 2026 Earnings Call Highlights"* | correct-ish company, wrong entity |
| **AstraZeneca** | GLEIF row `ASTRAZENECA AB PENSIONSSTIFTELSE II` | pension scheme, not the company |
| **Danfoss** | GLEIF row `Fællesklubben Danfoss Drives DK` | a staff club |
| **Smiths Group** | `smiths.com/about-us/corporate-governance/board-and-committees` | navigation page |
| **Deutsche Bahn** | `deutschebahn.com/de/presse/presse-regional` | index page |
| **AB InBev** | four `/about-us` pages containing **only an age gate** ("Please enter your birthdate below") | boilerplate |
| **Enel** | `/about-us/vision` containing raw Vue template source (`fadeUp(isVisible, entry, $refs.title, 50, .6)`) | template leak |

The LPP alias list is `["Reserved"]` — the clothing brand — which means
**every page containing "All rights reserved" satisfies the matcher for LPP.**

Quantified: **87 of 577 job postings (15%) and 42 of 233 newsroom documents (18%)
do not contain the company's own name at all**; 513 of 577 postings are hosted
off the company's own domain; 17 of the 38 companies with postings have **not one
posting that names the company**.

Why this is fatal to the demo: the SEB yacht-racing document and the LPP cruise
missile document are one click from the board. A juror who opens SEB's evidence
and sees dinghy sailing will stop trusting every other row on screen — including
the correct ones. The 25% criterion is not about the median row; it is about
whether the juror believes the system at all.

**Fix (highest priority, roughly a day):**
1. **Domain gate for news.** After a document is fetched, require that the URL's
   host is the company's own domain, *or* that a news host's article names the
   company **as a whole token** (`\b`-bounded, case-sensitive for names ≤4
   chars). This alone kills SEB/Seb, Nexi/NexImmune and Orange/cake.
2. **Negative-name list per company** — a short hand list of the other entities
   that collide (`lpp`: "LPP Holding", "cruise missile", "Narwhal", "Nigeria";
   `seb`: "Menzies", "yachting").
3. **Delete the ATS-slug guess or verify it.** `build_corpus.py:1830–1968`
   probes guessed slugs and takes the first that answers. Either fetch the ATS
   board's own company name and require a match against the target, or drop the
   ATS path to a hand-verified board list per company.
4. **Drop GLEIF rows that are not the operating entity** — require `entity.legalName`
   to match and reject `PENSIONSSTIFTELSE`, `PENSION SCHEME`, `Pensionskasse`,
   `Klubben`, `B.V.` subsidiaries filed under a parent.
5. **Tighten newsroom page selection**: require the page to contain the company
   name, and reject paths matching `/press-spokes|/contact|/board|/governance|
   /subscribe|/about-us$|/vision$|/insights/$`. Reject documents under ~600 chars.

Add a **CI gate** for it: `tools/verify_corpus.py` currently checks keys, hashes
and span resolution but has no notion of whether a document is *about* its
company. Make it fail on a document whose text does not contain the company name
or an alias as a whole token, on an ATS host whose slug is not the company
domain, and on a GLEIF legal name that is a pension/club entity. Right now the
corpus passes a verifier that would not have caught any of the rows above.

### 3.2 HIGH — the model's verdict is ignored by the scoring arithmetic

`ScoringService` weights a row by `PolicyQuestion.signedMagnitude()`
(`PolicyQuestion.java:174`), which is a function of **weight tier and polarity
only**. The signal's `verdict` is used for exactly one thing: releasing a
disqualifier. It never affects the magnitude.

Consequence: a model verdict of `BUILD` ("this company is building it
themselves") or `COUNTER` ("this is not the thing the question asks about") on a
POSITIVE question is scored as **full positive evidence**.

Measured on the shipped overlay:

```
POSITIVE question + BUY      356
POSITIVE question + BUILD    234      <- all scored as positive
POSITIVE question + COUNTER    4      <- all scored as positive
                                   ────
                                   238 of 772 non-NONE rows = 30.8%
```

The concentration is `data.initiative`: **191 rows where the model said the
company is building it itself, all scored as evidence of wanting to buy it.**

Live examples from `/api/companies/{id}`:

- **Stellantis**, the rank-1 company on the live board, carries a row
  `automation.hiring` with **`verdict: COUNTER` and `contributionNats: +0.631`**
  from the quote *"Stellantis has paused white-collar hiring at its Auburn Hills
  headquarters after an unprecedented hiring push during the first half of
  2026."* Strip it and Stellantis falls to **NURTURE**. The #1 company is #1
  because a counter-verdict was paid as positive evidence.
- **Continental**, the highest-scoring company on the Data & AI board
  (0.7056), carries `data.initiative BUILD c=0.82` at **+0.675**.
- **Securitas** and **SS&C** both carry `BUILD` verdicts on `data.initiative`
  scored positive — in both cases the quote is the company *selling* an AI
  product.

The mirror image also exists: on a NEGATIVE question the local engine always
emits `COUNTER` (`LocalRubricEngine.verdictFor`), so "counter" means *"the bad
thing is present"* when the cue engine says it and *"the bad thing is absent"*
when the model says it. **One field, two opposite meanings, depending on which
engine wrote the row.** 38 `NEGATIVE + COUNTER` rows in the overlay are applying
a penalty under the model's semantics where the model probably meant "not
applicable".

**Fix:** make magnitude a function of `(polarity, verdict, tier)`. Minimum:
`BUILD`/`COUNTER` on a POSITIVE question contribute **0**; a `COUNTER` on a
NEGATIVE question contributes **0**; a `COUNTER` on a DISQUALIFYING question
releases the gate (already correct). Then re-score and re-tune the thresholds,
because the board's shape will change materially.

### 3.3 HIGH — the disqualifier removes real leads on readings the brief calls positive

119 of 578 companies (21%) are removed. 92 are legitimate competitor exclusions.
**18 are removed for "building in-house", and at least 5 of those 18 are wrong:**

| Company | The quote that removed it | The problem |
|---|---|---|
| **ING Group** | *"the company has 600 employees working in AI, including about 300 in a hub in Türkiye."* | 600 AI staff is a **budget and maturity** signal. The brief's own DHL example calls internal-plus-third-party capability a *positive*. |
| **Admiral Group** | *"including the creation of a GenAI Center of Excellence"* (c=0.82) | Standing up a CoE is the classic **buying** signal — it is a programme looking for delivery partners. |
| **KPN** | *"Cloud-native engineers … ontwerpen, implementeren, automatiseren en beheren moderne Cloudomgevingen"* — a **job ad** | The same company carries a **BUY** verdict on `automation.hiring` from a different job ad. The system simultaneously reads a hiring signal as "buy" and as "gate", and the gate has veto power. |
| **VINCI** | *"We are building Physics AI for hardware design"* | Wrong company (§3.1). |
| **LPP** | *"The missile family also retains the AI-supported visual navigation…"* | Wrong company (§3.1). |
| **Getinge** | *"Getinge's proven leadership in sterile processing equipment, automation, and digital workflow solutions"* | Being *described* as an equipment leader is not "building automation capability in-house". |
| **Festo** | *"The new module is part of Festo's CPX-AP-I decentralised I/O platform"* (c=0.90) | Festo **is** an automation vendor — the right answer for the wrong gate. It should be in the competitor list, not gated as an in-house builder. |

That is a **~28% false-positive rate on this one gate**, on top of the entity
failures. And the design decision behind it deserves challenge: the brief says of
Lufthansa that strong internal capability *"could represent a negative signal,
**or at least indicate that an external provider would need to offer a very
specific capability**"* — a caveat, not a removal. The platform turns a caveat
into a hard gate, driven by a model verdict that is wrong a third of the time.

**Fix:** demote `*.insourcing` from `DISQUALIFYING` to `NEGATIVE` and let it
lower the score and show as counter-evidence on the card (which the UI already
renders). Keep `DISQUALIFYING` for the case it was actually designed for and
where the verdict is reliable: **`data.insourcing` on a known vendor**, backed by
the name list rather than a model reading. If you keep the gate, require a
second independent document before it fires, and never let a job posting alone
gate a company.

### 3.4 HIGH — 30.8% of signals come from two questions, and two questions are the same question

`automation.programme` — *"Has the company announced an efficiency, cost-reduction
or process-optimisation programme with a quantified target?"* (theme `efficiency`)
and `automation.cost_programme` — *"Has the company announced a cost-reduction or
efficiency programme with a quantified target?"* (theme `efficiency`) are
word-for-word the same question. The live board shows both firing on the same
document at the same span (ABN AMRO, span `2349:2927`, twice). The Studio lists
both, adjacent.

The rank discount is applied **per question**, so duplicated questions are not
discounted against each other; only the theme cap limits the damage. In a live
sample, **every company in Priority carried the same two rows in nearly the same
order** ("An announced efficiency programme" then "Hiring for process
excellence"), which makes a 578-row board look like a two-signal product.

**Fix:** delete one of the pair. Then generalise the correlation correction from
per-question to **per-document**: within a company, rows sharing an
`evidenceId` should be rank-discounted together, because one article answering
three questions is one piece of evidence, not three. (Reckitt's live card takes
`+0.406`, `+0.208` and `+0.047` from a single Yahoo article across three themes,
and Reckitt's #2 slot depends on it.)

### 3.5 HIGH — the ask flow, on default settings, is a false-positive generator

This is the demo. `/api/policies/derive` for *"companies hiring RPA developers"*
returns:

```
['companies hiring', 'hiring rpa', 'rpa developers', 'companies', 'hiring', 'rpa', 'developers']
```

`LocalRubricEngine.deriveCues` (`LocalRubricEngine.java:158`) emits every token of
length ≥ 6 that is not in a hardcoded `GENERIC` blocklist. That blocklist does not
contain "companies", "hiring", "banks", "cutting" or "firms". *"firms with a new
CISO"* derives the cue **`firms with`**.

The arithmetic then makes it worse: a single two-word cue has weight
`2^0.85 = 1.802`, and confidence is `sigmoid(0.6 × (score − 1.35)) = 0.567`,
which clears the floor of 0.5. **One two-word cue hit is a signal.**

I ran this on the live instance. `POST /api/ask` with that question produced
**1,913 signals from 4,466 documents — 43% of the corpus** — and moved the board
from 2 Priority companies to 10, with every company carrying the same rows. The
live response also confirms it re-classified *all seven* Intelligent Automation
questions with the local engine, not just the new one (`RescoreService.rescore`
builds `target` from `serviceLineQuestions(...)` and calls `reclassify(target,
true, …)` with `replace=true` — i.e. **asking one question deletes and rewrites
the whole service line**).

The UI shows the derived cues as chips under *"IT WILL MATCH"* with the note
*"Nothing is configured, so the rubric derives these from the wording"*, then
offers **`Score the corpus`** with the engine defaulted to **`Local rubric`** —
even though `meta.languageModelAvailable` is `true`. A juror will type a
sentence, see the chips, press the button, and watch the board become nonsense.

**Fix, in order of effort:**
1. **Default the engine select to `Language model` whenever it is available.**
   One line in the ask panel. This is the demo.
2. **Refuse to derive a cue that is a single generic noun.** Require derived
   unigrams to be in a domain lexicon (the `DOMAIN_TERMS` set already exists) or
   to be capitalised/acronymic; require bigrams to be ≥ 2 content words with at
   least one non-generic. Cap to 6 cues, not 14.
3. **Warn on the chip row** when a derived cue matches more than, say, 15% of the
   corpus, and mark it removable — the count is already computable client-side
   from the preview endpoint.
4. If the language model is genuinely unavailable at the venue, **fall back to
   asking the juror to pick from the 17 curated questions** rather than deriving.

### 3.6 MEDIUM — the published accuracy measurement is stale and does not describe its own file

`signals.json` ships a `sampling` block reading `cueSilentSampled: 6866,
cueSilentSignals: 229, cueSilentYield: 0.0334, reportedSignals: 746`. The same
file holds 9,042 rows and 772 non-`NONE` verdicts. Re-running the committed
`tools/overlay_report.py` against the committed corpus reproduces **7,415 /
0.0270 / 772**, not the shipped numbers. `SettingsView.svelte:657` prints the
stale figure verbatim to the juror: *"The cue matcher was silent on 6,866 pairs
sampled at random; the model found a signal on 3.34% of them."*

Separately, `overlay_report.py` and `model_overlay.py` fetch the question set
from `https://hackapi.pistonpunk.com/api/policies` at run time, so the denominator
moves with a live service and the number is not reproducible from the repo.

**Fix:** regenerate the block from the shipped files with the questions pinned to
a committed JSON, and print the sample size next to the rate on screen. A juror
who recomputes a number you put on screen and gets a different one is a juror you
have lost.

### 3.7 MEDIUM — measured precision of the rows you show

I sampled 22 random non-`NONE` overlay rows and judged each against its question's
rubric and the brief's definition of a buying signal. This measures the precision
of *what the board displays*, which is what the criterion asks about.

- **Clearly correct (9/22, 41%)** — ABN AMRO's quantified 5,000-headcount
  reduction; thyssenkrupp's confirmed breach; Repsol's *"60 use cases involving
  generative AI have been implemented"*; Vivacom's cyberattack; Diageo's NIS2
  programme; CaixaBank's corporate AI Office; BBVA's EU AI Act alignment; UiPath's
  vendor self-description; Reply's supplier job ad.
- **Clearly wrong (8/22, 36%)** — BNP Paribas "data governance" from a
  parenthetical in a Google Cloud story; Heidelberg Materials "cost programme"
  from a clinker-supply logistics note; Securitas and SS&C "AI initiative" from
  their own product marketing; TCS "cost programme" from a client-facing
  savings claim; Sunrise from a different company; Getinge's vendor-blurb gate;
  Bouygues Telecom's *cybersecurity* offering filed under a *Data & AI* question.
- **Weak/ambiguous (5/22, 23%)** — AXA's *"driven the launch of our AI roadmap"*
  (no named use case, which the rubric requires); a compliance cue inside a job
  ad; a partnership read as vendor lock.

**≈41% precision on displayed signals.** The NONE decisions (8,270 of 9,042) are
mostly correct and are not the problem — the problem is that the 772 rows the
board is built from are roughly 40% right. Fixing §3.2's verdict handling alone
should move this meaningfully, because 238 of those rows are verdicts the model
already flagged as not-a-buying-signal.

---

## 4. Data sources — what the brief asked for vs. what ships

| Brief requirement | Status | Evidence |
|---|---|---|
| **Crunchbase** — profiles, **key people**, **corporate events** | **Absent.** Zero code; `grep -rn crunchbase` hits only `docs/` | company attributes are **hand-typed** into `COMPANIES` (`build_corpus.py:528`): 578 rows of name/domain/country/city/sizeBand/industry |
| **Google News / NewsAPI / GDELT** | **1 of 3, and not the one you think.** No NewsAPI. No GDELT. Google News is 125 of 3,056 news docs (2.8%); the rest is **Bing News RSS**, which the brief never names | `fast_corpus.py:1-8` records the Google `batchexecute` 429 |
| **Company websites / newsrooms / strategy publications** | **Partial.** 292 documents across **32 of 578 companies**; 62 of those 292 are navigation/governance/contact pages | `newsroom_corpus.py:342` — the relevance filter is vacuous |
| **Annual reports** | **US only.** SEC EDGAR, 145 docs, 74 companies. No PDF or report crawling anywhere | `build_corpus.py:2049` |
| **Corporate career pages** | **Absent.** Only ATS APIs are read | |
| **Public job boards** | **Absent.** Greenhouse/Lever/Ashby/SmartRecruiters only | |
| **LinkedIn** | **Correctly absent** — the brief forbids depending on it | |
| Google News / career pages normally | | |

**Concrete composition of the shipped 4,466 documents:**

| Bucket | Documents | Share | Signals | Share |
|---|---|---|---|---|
| General web/news | 2,397 | 53.7% | 465 | 60.2% |
| **Market / financial data pages** (stock quotes, bond pages, fund factsheets, aggregator rewrites — `finance.yahoo.com`, `ad-hoc-news.de`, `sharewise.com`, `tradersunion.com`, `simplywall.st`, `ainvest.com`, …) | **992** | **22.2%** | **167** | **21.6%** |
| Job boards | 538 | 12.0% | 137 | 17.7% |
| **GLEIF registry rows** (LEI identity JSON — cannot carry a signal) | **394** | **8.8%** | **0** | **0.0%** |
| SEC filings | 145 | 3.2% | 3 | 0.4% |

**~31% of the corpus is structurally incapable of carrying a buying signal, yet
it is exactly what the coverage metrics count.** `/api/telemetry` reports
`documentsPerCompany: 7.73` and `/api/status` reports 4,466 captures; strip the
registry rows and the financial data pages and the real evidence base is ~3,000
documents. Meanwhile registry documents carry **authority 1.0**
(`SourceKind.REGISTRY`), higher than a company's own press release.

Two more source findings worth knowing:

- **`SourceKind` has only four values** — `JOB_POSTING(0.9), NEWS(0.85),
  REGISTRY(1.0), FILING(1.0)`. `AGENTS.md` and `CLAUDE.md` both claim
  `NEWSROOM` and `STRATEGY` were added "with authority 0.9 and 0.95". They do
  not exist, and `CorpusSeeder.parseKind` maps both to `NEWS`. **The documented
  authority differentiation is fiction**, and the 292 documents described to the
  team as the strongest evidence in the corpus are weighted *below* a GLEIF
  identity record.
- **The crawl is impolite and the gate does not check it.** No `robots.txt`
  handling in any tool (`grep -rni robots tools/*.py` → zero hits).
  `fast_corpus.py` runs `WORKERS = 40` with `FETCH_WORKERS = 40` and **no
  per-host semaphore**, while `newsroom_corpus.py` fires 23 guessed paths at one
  host simultaneously. Both present a Chrome user-agent, and `browser_fetch.py`
  exists to defeat WAF fingerprinting. The SSRF guard in `browser_fetch.py` is
  genuinely good work — but it makes the bypass *safe*, not *polite*. A technical
  juror may ask about this, so have the answer ready: add `robots.txt` compliance
  and a per-host delay; it is an hour of work and it removes the objection.

### 4.1 Additional data-quality items

- **Size is decorative.** `sizeBand` is `10001+` for **401 of 578** companies
  (69%); there is no company under 200 employees. `employeesFor()` invents a
  midpoint (`"10001+" → 20000`), and the live `icpMinEmployees` is **10** with
  `sizeGate: false`. The brief's "ICP by company size" dimension does not bind on
  anything.
- **Two of four ICP gates are off live**: `sizeGate: false`, `industryGate:
  false`, and `icpIndustries: []`. A juror opening Qualification sees the
  capability with most of it switched off.
- **Nothing expires.** `MAX_AGE_DAYS = 548` is enforced as 1,096
  (`fast_corpus.py:235` compares against `MAX_AGE_DAYS * 2`); 175 documents exceed
  548 days and the oldest is 3,830 days (a 2016 LVMH posting). 207 documents have
  no `publishedAt` at all, so decay has nothing to work from.
- **No cross-source dedup.** One URL fetched for two companies becomes two scored
  rows (11 cases). A press release and the news story about it are two documents.
- **A 306 MB cache is committed** under `tools/.cache/` (commit `ee70ff8`,
  1,038 files), and `signals.json`'s `supplemented` block has **no producer in the
  repository** — the 43-company top-up was folded in by an uncommitted process.
  The shipped overlay's provenance is not reproducible from the repo.

---

## 5. Operator validity — would a sales rep actually use this?

The brief's operator loop is **ICP → target account list → public data → signals
→ interpretation → score → prioritised accounts → outreach**. Walking it:

1. **Define an ICP** — exists and is decent, but is one global ICP for every
   service line, takes countries as a hand-typed comma-separated ISO textarea,
   has no size *band* (only a floor) and no industry multi-select.
2. **Build a target account list** — **this is the biggest functional gap.**
   There are **no people anywhere in the system**: no contact, no
   decision-maker, no title, no email. The brief's step 2 explicitly names
   *"CIO, COO, Head of Digital Transformation, Head of Automation or Process
   Excellence"*, and the data layer asks for *"Crunchbase … key people"*. The
   closest the app gets is `Aimed at COO or Head of Process Excellence`
   (`OutreachService.java:17`) — a hardcoded per-service-line role constant. **A
   rep cannot send the email the system drafts, because the system does not know
   who to send it to.** An operator will say exactly this in the first minute.
   Even a stubbed `person` table populated from public leadership pages would
   close it; consider a `security.leadership`/`data.hiring`-style question that
   captures *named* appointments into a contact row.
3. **Signals** — see §3. The mechanism is best-in-class; the inputs are not.
4. **Interpretation** — the Why-now panel and the counter-evidence block are
   genuinely excellent. The gate reason is good.
5. **Score** — defensible arithmetic, but "Fit" is presented as a bare
   probability of buying when it is a sigmoid on top of a **base rate the
   operator typed by hand** (0.25). The tooltip says *"Probability this company
   is worth calling"*. It is not that.
6. **Prioritise** — the 74%-empty-board problem (§6) is the issue here.
7. **Outreach** — **currently broken on the live instance.** Every live draft
   returns `engine: "template-v2, model call failed"` while
   `languageModelAvailable` reports `true`. What the rep sees is:

   > Subject: `Stellantis — An announced programme`
   >
   > *Hi,*
   >
   > *I have been looking at Stellantis. It looks like you have announced a
   > efficiency programme.*
   >
   > *We run the automation programme end to end so your team keeps the process
   > knowledge and we carry the delivery.*
   >
   > *Would twenty minutes next week be good?*
   >
   > *Best regards,*
   > *Orange Systems*

   Three defects in one message: no recipient; `"a efficiency programme"`; the
   **subject line is the raw internal question text truncated**
   (`Reckitt — Is the company consolidating shared service centres or bus…`); and
   the value proposition is byte-identical for every company, so
   *"customized value propositions mapped to specific company situation"* is not
   happening. Investigate the model failure before the demo — the most likely
   cause is that `DEEPSEEK_MODEL=deepseek-flash` is not a model the deployed key
   can reach (the public DeepSeek API exposes `deepseek-chat` /
   `deepseek-reasoner`), which would also mean **the live "escalate to the
   model" path cannot work at the venue either**. Verify with a bounded call and
   read the container log for `deepseek returned 4xx`. If it cannot be fixed,
   **hide the outreach panel** — a broken draft is worse than an absent feature.

**Lingo check.** The vocabulary is mostly right and clearly written by someone
who has read a sales playbook: *signal, buying signal, ICP, disqualifier,
counter-evidence, decay, corroborated, priority/nurture/watch, not contacted
yet, generate outreach, push to HubSpot*. The failures are where an internal
implementation detail leaks to a rep: `nats` appears in three places
(`Cap +1.299 nats`, `cap 1.299 nats`, `High · 1.5 nats`), the ledger prints
`High +1.5 × 1.30 × 0.983 × 0.85` with none of `authority`, `decay` or `shrink`
named anywhere, `Quotes struck` is shown as a percentage with no explanation,
`Fit rank — the order the ledger produces` tooltips the `#` column, and a
capture sheet shows a content `Hash` and `chars 1200–1330`. **"Rejected" means
three different things on one screen** (band filter, lead stage, and the
`DISQUALIFIED` short label), and `Thin` on the board is `Insufficient evidence`
on the card.

---

## 6. The board's shape is a product problem

Pre-perturbation live state, Intelligent Automation:

```
PRIORITY 2 · NURTURE 13 · WATCH 16 · THIN 428 · DISQUALIFIED 119   (of 578)
```

- **74% of the board shows nothing.** 428 companies carry zero signals. A juror
  scrolling the board sees an empty table.
- **21% of the board is rejected.** Two of every ten rows are struck out.
- **Cybersecurity has zero Priority companies** — its top row is a *disqualified*
  company at 0.6966.
- **`signalCount` is service-line-scoped**, so the same company looks populated
  on one tab and empty on another (Stellantis: 3 signals on Automation, 0 on
  Data & AI). Without explaining that, it reads as a bug.

The cause is a chain worth stating plainly: 4,466 documents × 17 questions =
75,922 document-question pairs; **9,042 of them (11.9%) are ever read by the
model**; on the pairs it does not read, the overlay has already asserted `NONE`
for everything the cue engine would have fired on, so **the precision layer
deletes the recall layer**; and of the pairs the model *does* read, 91.5% are
`NONE`.

So the honest diagnosis is the one the team already reached in the backtest:
**this is a crawl-depth problem wearing the costume of a scoring problem.** 7.7
documents per company — a third of them financial data pages and LEI records — is
not enough evidence to rank 578 companies on. The fix is not more questions or a
better model; it is **fewer companies, deeper**, and a news acceptance test that
requires the *signal term* to appear in the article (currently **1,326 of 2,933
Bing news documents — 45% — contain no signal vocabulary at all**).

**Concrete recommendation:** cut the target list to the 120–150 accounts that are
actually in the ICP, and crawl each one to 25–40 documents from its own domain
plus career page plus press releases. A board of 140 companies where 90 carry
real evidence beats a board of 578 where 150 do.

---

## 7. Demo-day risks, ranked by how likely they are to lose the prize

| # | Risk | Trigger | Fix |
|---|---|---|---|
| 1 | Juror types a question, board floods with 1,913 junk signals | "Ask a question" → `Score the corpus` with `Local rubric` | Default to the language model; fix `deriveCues` (§3.5) |
| 2 | Juror opens SEB or LPP evidence and sees yacht racing / a cruise missile | One click on any evidence row | Entity resolution (§3.1) |
| 3 | Juror asks "who do I email?" | Any conversation about outreach | No people data (§5.2) |
| 4 | Juror clicks *Generate outreach* and reads the draft | One click | Model call is failing live (§5.7) |
| 5 | Juror asks why the #1 company is #1 | "Show me the ledger" on Stellantis | `COUNTER` scored as positive (§3.2) |
| 6 | Juror sorts by "Rejected" and cannot tell band from stage | Toolbar | Rename the stage filter (§5) |
| 7 | Juror finds `\n\t\t\t\t\t` rendered on the Market/Settings panels | Scrolling the backtest caveat | `MarketView.svelte:128`, `SettingsView.svelte:657` |
| 8 | Juror pushes a company to HubSpot, opens Pipeline, sees nothing | HubSpot token absent (normal) | `PipelineView.svelte:15` omits the `NEW` stage |
| 9 | Juror presses Escape while a draft is open | Habit | Draft and scorecard both close, work is lost (`OutreachModal.svelte:64`, `BoardView.svelte:816`) |
| 10 | Juror recomputes the published 3.34% figure | Diligence | Regenerate the sampling block (§3.6) |

---

## 8. Prioritised fix list

**Before anything else — today**

1. **Restore the live instance** (§10). I perturbed it; the Intelligent
   Automation line is currently serving local-cue classifications.
2. Default the ask-panel engine to **Language model** when available.
3. `deriveCues`: drop generic single nouns and junk bigrams (§3.5).
4. Verdict-aware magnitude in `ScoringService` (§3.2). Re-tune thresholds after.
5. Demote `automation.insourcing` / `security.insourcing` / `data.insourcing`
   from `DISQUALIFYING` to `NEGATIVE` (§3.3), or require two documents.
6. Delete the duplicate `automation.cost_programme` question (§3.4).

**This week — the ones that win or lose the 25%**

7. Entity resolution: domain gate, token-bounded name match, negative-name list,
   drop the ATS-slug guess, reject pension/club GLEIF rows, tighten newsroom page
   selection (§3.1).
8. Add an attribution check to `tools/verify_corpus.py` and fail the build on it.
9. Fix the outreach model call or hide the panel (§5.7).
10. Re-crawl: fewer companies, deeper, with signal-term-gated news acceptance
    (§4, §6).
11. Regenerate the overlay's `sampling` block from the shipped files with the
    questions pinned to a committed JSON (§3.6).

**Before the demo — cheap and visible**

12. Fix the two literal `\n\t\t\t` artefacts (`MarketView.svelte:128`,
    `SettingsView.svelte:657`).
13. Add `NEW` to `PipelineView.svelte` `STAGES`.
14. Guard the Escape handlers so a modal eats the key before the scorecard.
15. Rename the stage filter to "Lead stage" and the band filter to "Band".
16. Render `gateLine`, `weakestLink`, `sensitivity` and `coverageNotes` — they are
    already on the wire and rendered nowhere; `gateLine` is the single best
    "why is this company not on my list" artefact in the system.
17. Suppress the backtest lift metric when `powered === false`.
18. Put a plain-English gloss next to `nats`, `authority`, `decay`, `shrink`,
    `struck`, and the word `Fit`.
19. Add `robots.txt` compliance and a per-host delay; then say so in the pitch.

---

## 9. Reproduction

```sh
cd /home/daniel/work/gigahack26

# Board shape and engine mix
curl -s https://hackapi.pistonpunk.com/api/status | jq
curl -s https://hackapi.pistonpunk.com/api/companies | jq '[.[]|.signalCount]|add'
curl -s https://hackapi.pistonpunk.com/api/telemetry | jq

# The derived-cue flood (read-only, no side effects)
curl -s --get --data-urlencode 'text=companies hiring RPA developers' \
  https://hackapi.pistonpunk.com/api/policies/derive | jq

# Verdict/polarity mismatch in the shipped overlay (238 of 772)
python3 - <<'PY'
import json,collections,urllib.request
pol={q['key']:q['polarity'] for q in json.load(urllib.request.urlopen(
    "https://hackapi.pistonpunk.com/api/policies"))}
o=json.load(open('api/src/main/resources/corpus/signals.json'))
c=collections.Counter((pol.get(r['questionKey']),r['verdict'])
    for r in o['signals'] if r['verdict']!='NONE')
print(c)
PY

# Entity failures in the shipped corpus
python3 -c "
import json;raw=json.load(open('api/src/main/resources/corpus/raw.json'))
print([e['title'] for e in raw['evidence'] if 'yachtingnz' in e['sourceUrl']])
print([e['title'] for e in raw['evidence'] if e['companyKey']=='orange' and 'cake' in e['sourceUrl']])
print([c['aliases'] for c in raw['companies'] if c['key']=='lpp'])"

# Corpus composition by source quality
python3 tools/verify_corpus.py

# The stale accuracy figure
python3 tools/overlay_report.py
```

⚠ **`POST /api/ask` and `POST /api/rescore` are destructive on the live
instance** — they delete and rewrite every signal for the service line, on the
local engine, and there is no route back without a redeploy. Use
`/api/policies/derive` and `/api/policies/preview` to explore; do not press
`Score the corpus` on the deployed board.

---

## 10. Disclosure — I modified the live instance, and it needs restoring

While verifying the ask flow I made three `POST /api/ask` calls against
`hackapi.pistonpunk.com`. That endpoint calls `RescoreService.rescore`, which
re-classifies **every question on the service line** with the configured engine
(`local`) and replaces the stored signals — including the language-model overlay
rows for Intelligent Automation. I deleted the two questions the calls created,
but **the overlay rows for Intelligent Automation are gone** and cannot be
restored through any API route.

Before → after:

| | before | after |
|---|---|---|
| signals in DB | 838 | 1,710 |
| policy questions | 17 | 17 (restored) |
| `signalsByEngine` (IA) | ~66 local / ~772 model | 1,163 local / 547 model |
| Priority band (IA) | 2 | 9 |
| board `engine` column | `deepseek-flash@deepseek` for 99 companies | `local-rubric-v1` for 463 |

Other service lines are untouched. **This exposes a real product defect, not just
an accident**: `reclassify(target, true, …)` where `target` is the whole service
line means *one question typed in the Studio silently replaces the entire
service line's classifications with the cheaper engine*, and no screen warns
about it. This is the same class of bug the team already fixed on the boot path
(`refreshSignals` re-applies the overlay) — the ask path still has it.

**Restore (needs Daniel, ~5 minutes):** the shipped `signals.json` is intact, so
a full re-seed puts it back. `CorpusSeeder` re-applies the overlay when
`corpusChanged` or `forceReseed`, but *not* on a plain restart — `overlayUnapplied()`
returns false because other service lines still carry model rows. So:

1. Add `-e CORPUS_FORCE_RESEED=true` to the `docker run` for `${API_CONTAINER}`
   in `Jenkinsfile` (or bump a byte in `corpus/raw.json` to change the
   fingerprint), push, let the pipeline redeploy.
2. Verify: `/api/status` → `signals` back to ~838; `/api/companies` → the
   `engine` column back to `deepseek-flash@deepseek`; Priority back to 2.
3. Remove the flag and push again.

I could not do this myself — the Jenkins token is not available to me and
`ADMIN_ENABLED=false` closes the in-app route.

---

## 11. What was fixed in this pass

Appended after the remediation pass of 2026-09-26. Everything below is on `main`,
built, tested and deployed to the live host.

**Signal correctness**

- **A reading only carries weight when it asserts what the question asks**
  (`PolicyQuestion.magnitudeFor`, `LedgerRow.asserted`). `BUILD` or `COUNTER` on a
  positive question contributes nothing and is shown on the card under *Read, not
  evidence* instead. This removed 238 of the 772 rows that were scoring positive
  while saying the opposite. Verified live: **Stellantis fell from rank 1 to
  Nurture** — its top slot rested on a `COUNTER` reading paid +0.63.
- **No shipped question removes a company on a model reading.** All three
  `*.insourcing` questions are `NEGATIVE` counter-evidence. Vendor exclusion runs
  on the curated name list, which is right, instead of on a model verdict that was
  wrong a third of the time. The 18 "building in-house" removals are gone.
- **Entity resolution is real.** `tools/attribution.py` holds the shared rules —
  whole-token, diacritic-folded, case-sensitive for names that are also given
  names, ATS board slug verified against the company's domain. The crawlers, the
  sanitiser and the CI gate all use it. `tools/sanitize_corpus.py` removed **282
  documents** that could not be about the company they were filed under (SEB's
  yachting reports, LPP's cruise missile and Nigerian election stories, Orange's
  cake recipe, VINCI's physics-AI job ads, 109 stock and fund listings, 53
  wrong-company ATS postings, 15 pension-scheme registry rows), and
  `tools/verify_corpus.py` now **fails the build** on any document that would be
  rejected, proven against a deliberately corrupted corpus.
- **The derived-cue flood is fixed.** `deriveCues` refuses a phrase that opens on
  the subject of the sentence and a single word that is not a domain term. Asking
  *"companies hiring RPA developers"* now derives `hiring rpa`, `rpa developers`
  rather than `companies`, `hiring`; it went from **1,913 signals to a set of
  phrases that actually say something**.
- **A re-score no longer throws the model away.** `SignalOverlayStore` is read by
  the seeder and by `RescoreService`, so asking one question classifies that
  question and leaves the rest alone. Verified live: after an ask the board still
  carries **748 model rows**. `overlayUnapplied()` now notices a partial loss.

**The measurement now describes the artefact**

- `tools/overlay_report.py --write` recomputes the sampling block and the strike
  rate **from the two shipped files**, reads the question set from the committed
  `tools/questions.json`, and drops the provenance keys that no committed tool
  writes. The published figures are now the reproducible ones: **8,516 pairs
  answered, 761 signals, cue matcher fires on 1,768 pairs and the model keeps 541
  (30.6%), the cheap engine's miss rate is 0.41%, 0 of 761 quotes fail
  containment.** The old 3.34% does not survive recomputation.
- `SourceKind` gained the `NEWSROOM(0.9)` and `STRATEGY(0.95)` values its own
  documentation claimed, so a company's own strategy page is now weighed above a
  wire's rewrite of it.
- `tools/sanitize_corpus.py` and `tools/questions.json` are committed, so all of
  the above can be re-run from a clone.

**The interface stops showing the operator the machinery**

- Removed from the screens a rep reads: the `nats` unit everywhere, the formula
  `High +1.5 × 1.30 × 0.983 × 0.85`, the bare `×1.6` odds figure, `Cap 1.299` and
  `cap 1.299 nats`, the `Quotes struck` percentage's original label, `Authority
  ×1.30`, `drives decay`, the content `Hash`, `c 0.72 · chars 1200–1330`,
  `logit(0.25)` and `logit → fit`, `base 0.25`, the raw engine identifier, a
  `HUBSPOT_TOKEN` name in a toast, and two literal `\n\t\t\t` artefacts that were
  rendering as text on the Market and Settings panels. The arithmetic is not gone:
  it sits behind a disclosure on each ledger row, where a juror can still check it
  and a rep is not asked to.
- Added where there was nothing: the **Why-now headline on every board row** (the
  API was computing it for all 578 rows and the board was discarding it), the
  gate breakdown (`gateLine`), the weakest link, the coverage note, and a *Read,
  not evidence* group on the scorecard showing which readings the model answered
  by denying the question.
- **The ask panel now defaults to the language model** and says so, marks the
  derived cues that are too general to be a signal, and reports how many answers
  were kept from the model rather than re-read. The engine options read
  *"Reads the documents (recommended)"* and *"Matches words"* instead of *"Local
  rubric"* and *"Model"*.
- **The Studio's empty state is fixed** (it said `No question matches “”.` when
  there were no questions at all), **Save can no longer PATCH
  `/api/policies/null`**, and unsaved edits prompt before being discarded.
- **The Pipeline can show a lead in stage `New`** — the state every lead is in
  right after a HubSpot push, which used to be invisible while the header counted
  it.
- **Escape is consumed by the outreach modal**, so a written draft and the board
  position behind it survive; the modal also says whether the body was written by
  the model or fell back to a template.
- Naming: the band filter is *Out of the queue* / *Too little evidence* rather
  than *Rejected* / *Thin* (which collided with a lead stage of the same name on
  the same screen), the stage select says *Any lead stage*, `Count as a qualified
  lead` became `Count as a good fit` (it writes feedback, not a qualification),
  and the source kinds read *company newsroom*, *regulatory filing*, *job posting*.
- **The outreach draft actually works.** The live failure was diagnosed to the
  root: the configured model is a reasoning model, and `max_tokens: 1100` was
  consumed entirely by reasoning, so the content came back empty and the writer
  fell back to a template **silently**. Budget raised to 6,000, an empty answer is
  now logged with its finish reason and reasoning-token count, `Voice.describe()`
  now carries the configured sign-off and signature (the model had been inventing
  a team name), and the subject line no longer prints a truncated internal
  question — it uses the question's own short title.

**Still open, honestly**

- **Crawl depth.** The board carries 1 Priority company and 12 Nurture. The
  thresholds are not the lever: the theme cap is derived from the Priority line, so
  lowering the threshold shrinks the cap with it and the funnel barely moves. The
  fix is the one in §6 — fewer companies crawled deeper, with the signal term
  required to appear in the article. That is a crawl, not a code change.
- **No people data.** Unchanged. A rep still cannot address the email.
- **Crunchbase, NewsAPI, GDELT, career pages and public job boards** are still
  absent; the news layer is Bing News RSS.
- **No `robots.txt` handling**, and `fast_corpus.py` still runs 40 concurrent
  fetches with no per-host delay.
- The overlay covers 11.9% of document-question pairs; the rest is the cue
  matcher.

---

## 11. One-page summary for the team

**Keep and lead with:** the extractive evidence chain (12/12 spans resolved), the
competitor exclusion list, the Why-now panel, the log-odds ledger with its three
corrections, and the configurability surface. Those are genuinely excellent and
they are what the "AI/ML Innovation" and "Configurability" criteria pay for.

**Fix or the prize goes to another team:** the platform does not reliably know
which company a document is about (yacht racing filed under a Swedish bank; a
cruise-missile article filed under a Polish clothing retailer), 30.8% of the
signals it shows are the model saying "they build it themselves" and being paid
as if they wanted to buy it, 21% of the board is disqualified on readings the
brief calls positive, and the demo's headline flow turns 43% of the corpus into a
signal in one click.

**The sentence to say to the jury**, and to make true: *"We refuse to recommend a
company we cannot prove something about. Every row resolves to a byte range in a
document we fetched — and we are ruthless about whether that document is about
the right company."* The first half is already true and impressive. The second
half is the work.
