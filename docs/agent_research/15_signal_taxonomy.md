# Buying Signal Taxonomy — Evidence, Counter-Interpretations, and Disambiguation Rules

Researched 2026-09-25. Written for the Signal Relevance & Accuracy criterion (25% of the score), which is judged on **"low false-positive rate in prospect identification."** Everything below is built to survive a jury of sales professionals attacking the logic, not to look clever.

Service vocabulary used throughout is Orange Systems' own: **Agentic Process Automation / RPA**, **InSight KYC** (customer onboarding and compliance automation — the 8-hours-to-under-1-hour product), and **Cybersecurity services**.

**The operating assumption this document is designed around:** with a ~5% base rate of in-market buyers, a signal set that fires on everything is worse than no signal set, because it burns the sales team's only scarce resource — attention.

---

## 1. What is actually known about B2B buying signals

Tagged **[E] Evidence** (named study, dataset, patent, or measured mechanism), **[A] Assertion** (vendor or consultant claim with no disclosed methodology), **[P] Practitioner** (credible operator writing from experience).

### 1.1 The base rate is brutal, and this is the single most important number

- **[E]** Only ~5% of B2B buyers are in-market in any given quarter. Source: John Dawes, Ehrenberg-Bass Institute, *B2B Report*, May 2021, commissioned by the LinkedIn B2B Institute. Derived from a ~5-year average switching cycle for a principal supplier (20%/year → "something like 5% in a quarter"). Dawes explicitly states: **"The 95% figure is not meant to be a precise rule."** No sample size published. https://marketingscience.info/news-and-insights/advertising-effectiveness-and-the-95-5-rule-most-b2b-buyers-are-not-in-the-market-right-now
- **[E]** Forrester Buyers' Journey Survey 2025: **68% of buyers begin the process with a preferred vendor already in mind, and that vendor wins 55% of the time.** Forrester's own conclusion is the sharpest sentence in the literature for this project: **"Intent signals are often the effect of preference, not the cause of it."** https://www.forrester.com/blogs/preference-matters-more-than-in-market-intent-alone-in-modern-b2b-buying/
- **[E]** Forrester's numbers are, if anything, more brutal elsewhere: **92% of buyers enter with at least one vendor already in mind and 41% with a single preferred vendor.** https://www.marketingweek.com/b2b-buyers-vendor-before/
- **[E, and the most uncomfortable finding in this document]** Forrester Q3 2022 Buying Motions Survey: **the most frequently cited trigger for contacting a vendor is prior experience with that vendor** — not a life event, not a news item, not a trigger at all. https://go.forrester.com/wp-content/uploads/2023/06/Forrester-Lifecycle-Revenue-Marketing_final.pdf
- **[E]** Bain/Google, 1,208 US B2B buyers, 2022: **86% have a "Day One List" of vendors before formal research; 93% buy from that list.**
- **[E]** Gartner: buyers spend **17% of total purchasing time meeting suppliers**, and 5–6% with any one rep (2017 Digital B2B Buyer Survey, n=750); buying groups are **6–10 stakeholders**. Note that the ubiquitous vendor claim of a "6–10 month buying journey" is a **content-marketing conflation of buying-group size with cycle duration** — Gartner never published a 6–10 month cycle. https://www.gartner.com/en/newsroom/press-releases/2020-09-15-gartner-says-80--of-b2b-sales-interactions-between-su
- **[E]** Gartner's measured cycle is far longer than the folklore: from the 2022 Functional Business Buyers Survey (**n=3,048, orgs ≥250 staff and ≥$50M revenue, 96% of purchases ≥$50k, 43% >$1M**), **the average tech buying cycle spans over 17 months, and 81% of buyers go on to express purchase regret.** Buyers with no regret closed 7–10 months faster; scope changes delayed 76% of purchases. https://emt.gartnerweb.com/ngw/globalassets/en/doc/documents/791700-b2b-tech-buying-teams-are-struggling-here-is-how-to-guide-them.pdf
  - **Two consequences.** (1) The trigger-to-purchase window is measured in *quarters*, not days — which is precisely why a platform that catches an early, dated signal has value, and why "we alert you the moment they're ready to buy" is a promise nobody can keep. (2) With 81% regret rates and 17-month cycles, **being early and credible beats being fast**; a well-timed insight is worth more than a fast call.
- **[E]** Forrester *State of Business Buying, 2026*: an average purchase involves **13 internal stakeholders and 9 external participants**; for genAI-feature purchases the buying group roughly **doubles**. https://www.biia.com/forrester-b2b-buying-groups-expand-as-they-question-ai/

**Consequence for the platform, stated plainly:** a signal detector cannot manufacture demand in the 95%. Its defensible job is to **rank a named account list the sales team already has a right to win**, and to catch the trigger that moves an account from "not now" to "now." Any pitch that claims to "discover" buyers from noise will be attacked and should be. Say the 5% number out loud before the jury does.

### 1.2 Third-party intent data: how it works and what the one peer-reviewed test found

- **[E]** Bombora's mechanism is documented in patent US20160132906A1 (Bombora Inc; now abandoned). Tags on third-party publisher pages emit events with user ID, URL, IP, timestamp; the system resolves IP → company, scores content relevance to a topic, then **trims the raw score against a historical baseline** (current week vs prior weeks), applying larger penalties when volume falls or unique users decline, and fires a surge only above a threshold. https://patents.google.com/patent/US20160132906A1/en
- **[E]** The structural failure mode is IP resolution, and it is getting worse, not better: office-network match ~85–95%, **remote worker on a home ISP 10–30%**, VPN "frequently misattributed entirely." Overall company-level resolution is estimated at ~30–60% of B2B traffic, down from >80% a decade ago; and **~7–10% of IP ranges change organisational ownership every month**, so a confident match can resolve to the previous tenant. [P, vendor-blog benchmarks, not peer-reviewed]
- **[E]** **The strongest academic evidence against off-the-shelf third-party intent segments is a null result.** Neumann, Tucker, Subramanyam & Marshall, *Quantitative Marketing and Economics* 21(4):519–571, Oct 2023, DOI 10.1007/s11129-023-09268-7: third-party IT-decision-maker segments — **both deterministic prospect lists and probabilistic inference — performed "no better at reaching the right person than random prospecting."** Deterministic attributes like job function were wrong for the *majority* of matched people. https://papers.ssrn.com/sol3/papers.cfm?abstract_id=4083057
- **[E]** **No vendor in this category publishes precision, recall, or prevalence-corrected PPV.** Not Bombora, not G2, not ZoomInfo. This absence is itself a finding and should be stated as one.
- **[A]** Bombora's co-op size is quoted inconsistently across its own materials (3,500 / 4,000 / 5,000+ sites; 70% vs 86% exclusive; 4,000 / 6,500 / 12,000 / 21,000 topics). Treat every coverage number as unfalsifiable marketing.
- **[A]** "Intent-signal leads convert 3–5x more often than fit-only-scored leads" (attributed to Bombora/SiriusDecisions) — vendor-funded, no design disclosed.
- **[A]** G2 Buyer Intent publishes conversion-rate-by-signal-type (own product page 60-day inquiry rate 5.5%; category 0.65%; competitor pricing 0.54%) and a Dreamdata benchmark (12% of closed-won deals had a G2 signal; such deals 2x average value) but **no precision or recall figure**, and Dreamdata does not disclose deal count or date range. https://documentation.g2.com/docs/buyer-intent
- **[P]** The practitioner consensus is that an intent signal means "someone, possibly, at that company read something a vendor classified under a topic more than usual." Recommended use: **ranking a fixed named list, never discovery.** And because competing sellers buy the same bidstream, **competitors get flagged the same accounts in the same week** — so intent data confers no exclusivity. https://www.marketsizer.io/blog/why-intent-data-made-b2b-outreach-worse

### 1.3 Trigger events: the most-repeated claim in sales has no primary source

This section matters because a jury of sales professionals will repeat these statistics at us, and we should be the ones who know where they come from.

- **[E, and the best available primary survey]** A 2021 WSJ Intelligence / B2B International survey asked decision-makers which of **twelve** trigger events prompted them to look for a new supplier. The article reporting it states there is **"little research about what specific kinds of events most frequently act as buying triggers."** Only one ranking outcome is legible from the public write-up: **content-consumption events (reading marketing, sales or news content) ranked near the bottom of the twelve.** The twelve items, percentages and sample size are published only inside an image and are not recoverable from text. https://customerthink.com/unveiling-the-mechanics-of-b2b-buying-triggers/
  - **Read this carefully: an intent-data event — the thing Bombora and G2 sell — ranked at the bottom of the only survey that measured trigger types directly.** That is a citable, jury-proof argument.
- **[A, vendor — now traced to its origin]** "Sellers who reach a decision-maker first in the Window of Dissatisfaction close 74% of the time" is the single most-repeated number in trigger-based selling, and it is untraceable. It originates with **Craig Elias, *SHiFT!* (2011)**; his own 2013 post asserts the 74% figure, a "5x more likely to win", and 16% if the buyer is already "searching for alternatives", with **no study title, sample size, date or methodology**, credited loosely to "Forrester" — and elsewhere in the same body of content credited to "SBI Research". https://www.insidesales.com/leveraging-trigger-events/ **Do not put this number on a slide.** A jury member from an RPA sales team may have repeated it for a decade; if we are the team that knows where it came from, that is worth more than the number ever was.
- **[E]** The canonical trigger list has a respectable origin that is routinely over-claimed: Robinson, Faris & Wind's **BUYGRID (1967)** is descriptive, and the 30-year retrospective notes the model "does not permit inferences about behavioral cause-and-effect relationships." The trigger list was never a causal claim. https://faculty.wharton.upenn.edu/wp-content/uploads/2012/04/9802_The_BuyGrid_Model_30_Years.pdf
- **[A, aggregated]** "5x more likely to win if first to contact after a trigger"; "between one-third and one-half of all B2B sales are won by responding first." A 2026 review article attributing these to "research aggregated across multiple studies" cites **seven vendor blogs** and no primary study. https://www.recordcontext.com/blog/trigger-events-research-disappears
- **[A]** The classic trigger-event list (management change, funding, M&A, new executive, regulatory deadline, product launch, expansion, layoffs, poor earnings) is repeated by essentially every sales-consulting firm and **measured by almost none**. Treat it as a hypothesis set to be tested, not as established fact.
- **[E, academic gap]** Empirical academic work is stronger on purchase *timing* than on trigger *types*: see the University of Houston B2B selling thesis and the "Market Response Time" multiple-case study (8 cases, automotive supplier and machinery industries). https://uh-ir.tdl.org/server/api/core/bitstreams/09481742-3a93-466b-b907-d8a13918bc1c/content

### 1.4 Job postings as purchase intent — the measured answer, and it is not flattering

- **[E]** The strongest quantitative study available: **1 million B2B software purchases, March–September 2025, controlled for 200–1,000-employee companies** (Bloomberry). Eight signals, ranked by lift in software purchases:

  | Signal | Purchase lift |
  | --- | --- |
  | Enterprise AI tool purchase (ChatGPT Enterprise, Claude for Teams) | **+46%** |
  | Headcount growth 20%+ | +38% |
  | Recent software purchases | +38% |
  | VP/executive hires | +28% |
  | Recent funding round | +25% |
  | New office openings | +11% |
  | **Job posting increases (20%+)** | **+7%** |
  | SOC compliance | 0% (no correlation) |

  Companies with 20%+ more openings averaged 2.82 software purchases vs 2.64 with no increase. Bloomberry rates job-posting growth **"Low"** predictive value and calls it "a huge disappointment": postings are *intent to hire*, not hiring, and the software need arrives after the hires do. https://bloomberry.com/blog/i-analyzed-1m-software-purchases-to-find-the-strongest-buyer-intent-signals/
- **[A, vendor]** Against this, SyncGTM claims companies increasing postings 30%+ in a quarter are "2.4x more likely to buy software within six months." No methodology.
- **[P]** Where hiring does work, practitioners converge on *shape*, not volume: **first-time/founding role creation** beats backfill; **role clusters** (five related roles in two weeks) beat single postings; **velocity measured against the account's own baseline** beats absolute counts; **tool names inside the posting** beat the job title. Isolated single postings sit at the bottom. https://crustdata.com/blog/hiring-signals and https://predictleads.com/blog/hiring-as-a-buying-signal/
- **[E]** The build-vs-buy counter-argument is real and measurable for RPA specifically: **PwC found ~70% of firms in PoC/pilot intend to implement and operate RPA with internal competencies**, and firms already scaling use mainly internal specialists. UiPath/Forrester TEI: in all four interviewed customers a CoE was created and **"in all cases, the customers have strived to build internal capabilities,"** with the composite model assuming no third-party assistance in steady state (CoE scaling 5 FTEs year 1 → 24 in year 3). https://www.pwc.dk/da/publikationer/2017/rpa.pdf
- **[P, directional]** Further pressure on the hiring-to-services inference: Retool's 2026 Build vs Buy report (secondary reporting) puts **workflow automation as the #1 category moving in-house (35%)**, with 35% of respondents having already replaced a SaaS tool with an internal build. Unverified at source — treat as directional, and note that it cuts *against* reading an automation hire as a services purchase.
- **[E]** The counter-counter-argument, which is Orange Systems' actual opening: RPA programmes decay. **30–50% of initial RPA implementations fail to take off** (EdgeVerve) and **60–70% fail to meet expectations** (IRPA AI, via Fortra); **70–75% of total RPA budgets are consumed by maintenance.** The CoE "often just became the institutional home for an ever-growing repair backlog." https://www.fortra.com/blog/rpa-implementation-guide

### 1.5 Regulatory and financial triggers where evidence does exist

- **[E]** Regulatory deadlines do drive preparation spend well ahead of the date, but the spend curve is late — see §3 signal **R2**.
- **[E]** **Gartner survey, 350 global executives, $1B+ revenue, Q3 2025: ~80% of organisations piloting or deploying autonomous business capabilities reported workforce reductions, but reductions "do not appear to translate into return on investment"** — workforce-reduction rates were near-identical between high-ROI and negative-ROI firms. Gartner's Helen Poitevin: **"Workforce reductions may create budget room, but they do not create return."** Layoffs alone are therefore an evidence-backed **weak** signal. https://www.thehindubusinessline.com/info-tech/autonomous-business-and-ai-layoffs-do-not-deliver-returns-gartner/article70943654.ece
- **[E]** The KYC/onboarding pain Orange Systems sells against is quantified and current: **49 days average commercial onboarding and $14,700 per client onboarded** (Celent Global Commercial Banking Onboarding Survey 2025, via nCino); **6 of 9 core KYC steps remain manual**; **70% of financial institutions lost clients due to slow onboarding**; document collection is the #1 abandonment point (79%) and KYC/KYB the #2 frustration (65%). https://www.ncino.com/en-EMEA/blog/how-to-transform-commercial-onboarding-from-a-cost-center-to-your-competitive-advantage and https://fintech.global/2025/10/08/70-of-banks-lose-clients-due-to-slow-onboarding/
- **[E]** FCA AML enforcement over 2020–2025: £430m+ across 22 Final Notices, and **data deficiencies were a factor in 68%** — outdated customer information 45%, UBO verification failures 32%. Monzo failed to verify UBOs of 19,198 entities. **This matters because "data deficiency" is an automation-shaped finding and "late filing" is not.** https://www.kyckr.com/blog/fca-aml-fines
- **[A]** "12–24 months of conversion data and 500–1,000 completed sales processes are needed to validate a scoring model." Directionally right; no study cited.

### 1.5b Leadership change: the peer-reviewed finding contradicts the sales-training version

This is the most important correction in this document, and it should be the thing we say in the room that nobody else says.

- **[E] Intintoli, Serfling & Shaikh, "CEO Turnovers and Disruptions in Customer–Supplier Relationships," *Journal of Financial and Quantitative Analysis* 52(6), 2017, 2565–2610.** When a customer's CEO turns over, **suppliers lose substantial sales.** The effect is *stronger* when the departing CEO was entrenched, when the departure was forced, and when the new CEO is an outside appointment. https://www.cambridge.org/core/journals/journal-of-financial-and-quantitative-analysis/article/abs/ceo-turnovers-and-disruptions-in-customersupplier-relationships/0E6F10186579FFF363126AFA744DBF68
  - **The measured effect of a leadership change is a review of the inherited stack, not the release of new budget.** A new executive is a *displacement* signal, not a *greenfield* signal. That reframing changes the pitch entirely: sell as "the alternative to what is already installed", expect a defensible window measured in months — and expect the incumbent to be defending.
- **[A, no methodology]** "New executives spend 70% of their budget in their first 100 days" is attributed to UserGems, which publishes no methodology; its own documented internal data is a conversion figure (2.5x higher in the first three months), not budget. https://www.usergems.com/blog/new-hire-buying-signal
- **[A, does not exist]** There is **no Chadwick Martin Bailey or Corporate Visions study** establishing first-90-day executive buying behaviour. Corporate Visions' actual research is on *why* executives act at all — unconsidered need plus loss aversion (Riesterer with Dr. Nick Lee, Warwick Business School). http://win.corporatevisions.com/rs/413-YED-439/images/Article-What-Gets-Executives-To-Act.pdf
- **[Flag]** Vendor "act within X" windows for executive changes are mutually exclusive and all unsourced: 24–48 hours, 1–6 weeks, 30–60 days, 90 days, 100 days. Our Rule 6 uses 30–60 days because it is the median of unsourced advice and is consistent with the standard 30/60/90 onboarding plan — and we should say that it is a convention, not a measurement.

### 1.5c The two timing claims that survive scrutiny: regulatory deadlines (weak, then suddenly strong) and M&A integration

- **[E]** GDPR is the natural experiment, and the result is unflattering to "deadline-driven" selling. IAPP meta-analysis of **12 surveys, 10,247 respondents, Sept 2016 – July 2018** (Fazlioglu): only **6% were fully compliant before 25 May 2018**; 36% were compliant by the deadline; 9% were non-compliant or had not started; 17% did not know. Budgets were real but spent late: 27% ≤$1M, 29% $1–5M, **38% >$5M**. https://iapp.org/media/pdf/resource_center/timelines_for_gdpr_compliance.pdf
- **[E]** **The EU AML package is the exception that makes R2 worth scoring, because the readiness gap is measured and public.** PwC EMEA AML Survey 2026 (**531 institutions, 40 countries**): **only about one third expect to be ready by the 10 July 2027 application date**; **62% of insurers plan new CDD technology and 60% will focus investment on CDD onboarding**; 61% of banks plan new transaction-monitoring technology; about a third expect compliance costs to rise 10–30%. KYC/CDD failings drove a **394% year-on-year rise in penalty value**, with **>$4B in financial-crime penalties issued in 2025**. https://www.pwc.lu/en/anti-money-laundering/docs/pwc-emea-aml-survey-2026.pdf
  - This is the rare case of a **dated public deadline + a measured readiness gap + stated technology budget intent in the same survey**, and 2026–2027 is exactly when the buying has to happen. It is why R2 is scored at 4 rather than 3, and why it pairs so well with R1.
- **[E]** M&A integration spend is real and large: Bain finds systems-integration estimates **overrun by 20–50%**; four Canadian banks spent roughly **CA$1.8B in 2024** merging acquired technology stacks (RBC alone $1.06B on HSBC Canada); **60% of synergy initiatives are IT-related and 84% of IT integrations hit material issues** [P, Synoptek]. This is the evidence behind X1.

### 1.6 What this means for the design (the four commitments that follow from the evidence)

1. **Rank, don't discover.** Signals prioritise a named account list. Discovery claims invite two separate refutations: the QME-2023 null result (third-party segments perform "no better than random prospecting"), and Forrester's finding that **prior experience with a vendor is the most frequently cited reason for contacting one**. Buyers start with a list; our job is to tell the team *when* to work it.
2. **Prefer hard, dated, primary-source signals over inferred interest.** A TED notice or an SEC 8-K Item 1.05 is a fact with a timestamp. A surge score is a statistical lift over a baseline we cannot audit. Where a jury asks "how do you know," the first kind has an answer.
3. **Never report "accuracy."** With a ~5% base rate, 90% accuracy is compatible with near-useless precision. Report **precision at a fixed recall**, with the threshold and the number of accounts reviewed. If we have no labelled outcomes yet, say so and report signal counts instead of inventing a metric.
4. **Every signal ships with its counter-interpretation visible in the UI.** Not in a methodology appendix — on the card, next to the score. This is the single highest-leverage design decision for the 25% criterion, because it makes the false-positive control *demonstrable* rather than asserted.

---

## 2. The taxonomy

**Scoring convention.** `Strength` = how much a *true* instance of this signal raises P(genuine Orange Systems opportunity), 1–5. `Detect` = how reliably the team can surface it from **free public data inside 48 hours**, 1–5. `S×D` = ranking score. Detectability was assessed only against sources actually reachable this weekend — verified ones are marked ✔ in §5.

Service codes: **APA** = Agentic Process Automation / RPA · **KYC** = InSight KYC onboarding & compliance automation · **CYB** = Cybersecurity services.

### A. Hiring signals

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| H1 | **Founding automation / process-excellence leadership role** — first-ever "Head of Automation", "Head of Process Excellence", "RPA CoE Lead", "Head of Business Process Transformation" | APA | 4 | 4 | **16** |
| H2 | **Automation engineering role cluster** — ≥3 RPA/automation engineer postings within 60 days | APA | 2 | 5 | 10 |
| H3 | **Postings naming a specific platform** — UiPath, Automation Anywhere, Blue Prism, Power Automate, Selenium, Appian | APA | 3 | 5 | **15** |
| H4 | **Compliance/onboarding operations volume hiring** — KYC Analyst, CDD/EDD Officer, Onboarding Specialist, Financial Crime Operations, back-office processing | KYC | 4 | 4 | **16** |
| H5 | **Manual-process pain language inside the posting** — "reduce manual effort", "migrate from legacy", "eliminate spreadsheet-based", "streamline onboarding" | APA/KYC | 3 | 5 | **15** |
| H6 | **Security or financial-crime function/leadership creation** — first CISO, first MLRO, first fraud-ops function | CYB/KYC | 4 | 4 | **16** |

### B. Leadership changes

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| L1 | **New CIO / COO / CDO / Head of Operations, appointed ≤180 days ago** | all | 4 | 3 | 12 |
| L2 | **New CISO / MLRO / Head of Financial Crime, or the incumbent's removal following a finding** | CYB/KYC | 4 | 3 | 12 |

### C. Regulatory and compliance triggers

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| R1 | **Company-specific AML/KYC enforcement action, fine, or supervisory notice** naming the entity | KYC | 5 | 4 | **20** |
| R2 | **Dated regulatory obligation the company is in scope for, with a measured readiness gap** — AMLR application 10 Jul 2027 (only ~⅓ of institutions expect to be ready; 62% of insurers plan new CDD technology); AMLD6 Art. 11–15 10 Jul 2026; eIDAS 2.0 wallet acceptance ~Dec 2027 | KYC/CYB | 4 | 3 | 12 |
| R3 | **Public breach or material cyber-incident disclosure naming the company** — SEC 8-K Item 1.05, California AG / Maine AG breach lists, state regulators | CYB | 4 | 5 | **20** |
| R4 | **FATF grey/black-list change touching their market**, or a national AML regime change forcing re-documentation of a client book | KYC | 2 | 5 | 10 |

### D. Operational-pain signals

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| O1 | **Public automation/efficiency programme with a quantified target and NO named vendor** — "cut onboarding from X to Y by 2027" | all | 5 | 4 | **20** |
| O2 | **Complaint/outage surge naming processing, paperwork, claims or onboarding delays** (app-store reviews, Trustpilot, support-forum volume) | APA/KYC | 3 | 3 | 9 |
| O3 | **Glassdoor/review complaints about manual process and understaffing in a specific back-office function** | APA | 2 | 2 | 4 |

### E. Financial signals

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| F1 | **Cost-reduction programme + concurrent automation hiring in the same function, within 90 days** | APA | 5 | 4 | **20** |
| F2 | **Capital raise with operations, compliance or technology named in the stated use of proceeds** | all | 3 | 4 | 12 |
| F3 | **Earnings-call language: cost-to-serve, back-office productivity, headcount-driven margin pressure** | APA/KYC | 3 | 4 | 12 |

### F. Technology-adoption signals

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| T1 | **Incumbent RPA platform already in production** — UiPath/Automation Anywhere named in postings, a case study, or a conference talk | APA | 3 | 3 | 9 |
| T2 | **ERP / core-system migration announced or hired for** — SAP S/4HANA, core banking replacement, CRM migration | APA/KYC | 4 | 4 | **16** |
| T3 | **Technographic immaturity** — legacy customer-facing stack, no self-service onboarding or portal | APA/KYC | 2 | 5 | 10 |

### G. M&A and expansion

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| X1 | **M&A close with a disclosed integration or synergy programme** — duplicate KYC files, two onboarding systems | KYC/APA | 4 | 4 | **16** |
| X2 | **New market entry, licence application, or new shared-services / capability centre** | KYC/CYB | 5 | 4 | **20** |

### H. Public tenders and procurement

| # | Signal | Svc | Strength | Detect | S×D |
| --- | --- | --- | --- | --- | --- |
| P1 | **Open public tender for RPA / onboarding-KYC / cybersecurity services, participation window still open** | all | 4 | 5 | **20** |
| P2 | **Prior award notice for automation services, 0–24 months old** | APA | 2 | 5 | 10 |

### Anti-signals (not candidates — these subtract)

| # | Anti-signal | Effect |
| --- | --- | --- |
| A1 | **Generic "digital transformation / innovation / efficiency / customer-centricity" language** with no entity, number, function or date | **0 information.** Present in essentially every corporate press release. Must be stripped at extraction, not weighted low. |
| A2 | **Company has publicly announced a completed automation outcome WITH a named vendor** (e.g. "onboarding 70 min → 25 min with [vendor]") | **Hard negative.** The purchase happened; a competitor is entrenched. Record as competitive intel + set a 24–36 month re-tender reminder. |

---

## 3. Counter-interpretations and disambiguators

The required column. For each signal: what it looks like when the company **will buy**, what it looks like when it **will not**, and the evidence that separates the two. The eight highest-scoring signals are expanded; the rest are in §4.

### H1 / H2 / H3 — Hiring signals (the most dangerous cluster)

**Will buy:** a *founding leadership* role whose charter includes choosing and managing delivery partners. Language in the posting: "select and manage implementation partners", "own vendor relationships", "define the automation roadmap with external delivery partners", "build the business case for automation investment". The role reports into a **business** function (COO, operations, shared services), the platform is not yet chosen, and there is exactly one such role.

**Will not buy:** a cluster of hands-on build roles. ≥3 developer postings in 60 days, a **named platform already licenced**, roles sitting inside IT, "build our CoE from scratch", "citizen developer enablement", "RPA developer — 3+ years UiPath". This company has decided to insource. PwC measured it: **~70% of firms in PoC/pilot intend to operate RPA with internal competencies.** UiPath's own Forrester TEI assumes no third-party assistance in steady state.

**Disambiguator (the "who builds" test):** read the JD for the verb. *Choose/manage/govern* → buy. *Build/develop/code* → build. Then check the count: one leader is a buying shape; three developers is a building shape. And check where the role sits — business-owned automation budgets buy services, IT-owned ones hire.

**Second-order point to bring in the room, because the jury will raise it:** even a build-shaped account is not worthless — RPA programmes decay (**30–50% of initial implementations never take off; 70–75% of budgets go to maintenance**). The build-shaped account's realistic entry is a **CoE remediation / managed-service** conversation 12–18 months later. Flag it as a *nurture* account, not as a lead.

### H4 — Volume hiring of manual compliance staff

**Will buy:** 5+ KYC analyst / onboarding officer roles in 90 days, especially in a bank or insurer with a new regulatory obligation, an acquired book, or a new market. This is a company **throwing bodies at a volume problem** — the exact profile behind InSight KYC's 8-hours-to-under-1-hour pitch. Supporting benchmark: **6 of 9 core KYC steps remain manual**, and manual KYC review costs ~$2,598 per corporate client against a commercial onboarding cost of **$14,700 per client**.

**Will not buy:** the postings are **backfill/replacement** (same headcount, same title, ongoing for years — a steady-state manual operation that has decided manual is its model), or the roles are **contract/temporary remediation staff** attached to a dated consent order, which means the remedy is already chosen and funded outside automation.

**Disambiguator:** is the hiring *growth* or *steady state* against the company's own baseline (a 12-month posting history from the same ATS board)? And is a **senior KYC-transformation or onboarding-automation role** posted alongside the analysts? Analysts + a transformation lead = buying. Analysts alone at steady state = not now.

### H5 — Manual-process pain language in a posting

**Will buy:** the JD describes the *problem* the hire must solve in Orange Systems' own terms — "eliminate manual data entry", "reduce onboarding turnaround", "replace spreadsheet-based reconciliation". The company has named its own pain in public.

**Will not buy:** boilerplate JDs. "Work in a fast-paced environment" tells us nothing; a generic "process improvement mindset" line is present in half of all corporate postings.

**Disambiguator:** require a **named process, a named system, or a number**. "Reduce manual effort in claims intake" qualifies. "Process improvement mindset desirable" does not.

### H6 — Security / financial-crime function creation

**Will buy:** the **first** CISO, MLRO, Head of Financial Crime or fraud-operations function. A first hire means no incumbent tooling, no incumbent vendor, and a mandate to build — a genuine greenfield for cybersecurity or KYC services.

**Will not buy:** the Nth SOC analyst in a mature, tooled security function. That company has a stack and a vendor; a new analyst is capacity, not a purchase.

**Disambiguator:** is this the *first* role of its kind at the company (check the ATS board's history and the company's own newsroom)? First = buy. Nth = not now.

### R1 — Company-specific AML/KYC enforcement action (highest value, hardest to argue with)

**Will buy:** a published fine, final notice or supervisory finding where the **fact pattern is data-shaped** — customer files out of date, UBO verification incomplete, ongoing monitoring not performed, remediation backlog. FCA data over 2020–2025: **data deficiencies were a factor in 68% of AML Final Notices** (outdated customer information 45%, UBO verification failures 32%). Monzo failed to verify UBOs of **19,198** entities. A regulator telling a bank to fix its customer-file data is a purchase order for KYC automation with a board-level deadline attached.

**Will not buy:** the finding is **reporting-shaped** (a late SAR filing, a notification failure, a governance/registration lapse). The remedy for a late filing is process discipline and a compliance hire, not an automation platform. Also: the company may be under a **hiring freeze with a mandatory remediation budget already committed to an incumbent consultancy** appointed by the regulator's timeline.

**Disambiguator:** classify the finding against three buckets before scoring — **data quality / file currency / UBO verification → strong buy**; **monitoring execution → buy**; **reporting timeliness / governance → do not score.** Then check for a concurrent remediation-hiring cluster or a named remediation partner. The date matters too: the remediation budget is live for roughly 12–18 months after the notice, then it is spent.

### R2 — Dated regulatory obligation with a measured readiness gap

**The general rule, and it is a warning:** regulatory deadlines do **not** produce early spending. The GDPR is the cleanest natural experiment available, and the answer is unambiguous — IAPP's meta-analysis of **12 surveys and 10,247 respondents** across the whole 2016–2018 preparation window found only **6% of organisations were fully compliant before the 25 May 2018 deadline**; 36% made it by the date, 9% were non-compliant or had not started, 17% did not know. Budgets were genuinely large (38% above $5M) but were released **late**, against the wall. https://iapp.org/media/pdf/resource_center/timelines_for_gdpr_compliance.pdf
**Therefore: a distant deadline by itself is a weak signal, and any pitch built on "the deadline is coming" is a pitch the jury has heard from every vendor for a decade.**

**The exception — and this is why R2 is scored at 4 — is when a deadline is paired with a *measured, published* readiness gap and *stated* technology budget intent.** The EU AML package currently qualifies, and it is the single best-evidenced regulatory trigger available to Orange Systems:

- **Deadline: 10 July 2027** (AMLR (EU) 2024/1624 general application).
- **Readiness gap: PwC EMEA AML Survey 2026 (531 institutions, 40 countries) — only about one third expect to be ready by July 2027.**
- **Stated budget intent: 62% of insurers plan new CDD technology and 60% will focus investment on CDD onboarding; 61% of banks plan new transaction-monitoring technology; roughly a third expect compliance costs to rise 10–30%.**
- **Urgency evidence: KYC/CDD failings drove a 394% year-on-year rise in penalty value, with >$4B in financial-crime penalties issued in 2025.**
https://www.pwc.lu/en/anti-money-laundering/docs/pwc-emea-aml-survey-2026.pdf

**Will buy:** an in-scope institution (bank, insurer, payments firm, CASP) with a **large manual KYC/CDD function**, in a jurisdiction where the 2027 date bites, showing **either** a hiring cluster (H4) **or** a technology-intent statement **or** a recent supervisory finding (R1). That institution is inside the 24-month window in which the money must be spent, and PwC's own survey says two thirds of them are not ready.

**Will not buy:** the obligation is nominally in scope but the institution is **small, low-volume, or already automated** (the marginal cost of compliance is trivial); or the board has decided to **absorb the cost with temporary compliance contractors** rather than build capability; or a **group-level platform already covers them**, in which case there is no local decision.

**Disambiguator:** three tests. (1) **Volume** — does the company process enough customers for manual CDD to be a material cost? (2) **Gap evidence** — is there any public sign of unpreparedness (a hiring cluster, an executive statement, a supervisory finding, or a vendor case-study absence)? (3) **Decision locus** — local or group? Compare against Orange Systems' own reference: InSight KYC cut onboarding from 8 hours to under 1 hour and saved over 2 million hours annually across 5 countries. The account qualifies when the same arithmetic clearly applies to them.

### R3 — Public breach or material cyber-incident disclosure

**Will buy:** a disclosed material incident, especially one with a stated remediation programme, a named accountable executive, or a stated security budget increase. **Verified live and free this weekend** — see §5.

**Will not buy:** the same problem from the other direction — an incident often produces the *opposite* of a new vendor: a **freeze on new vendors during forensic containment**, a decision to consolidate onto an existing MDR provider, or a reputational crisis that consumes all management attention for two quarters. A company four weeks into a breach is not buying; it is triaging.

**Disambiguator:** **time since disclosure.** 0–90 days = containment, do not approach as a services lead (approach with empathy, no pitch). 90–270 days = remediation budget is being defined and this is the window. Beyond 270 days = budget allocated. Look for a concurrent **security leadership change** (L2) or a job posting for a remediation-specific role as the confirmation that budget is moving.

### O1 — Public automation programme with a quantified target and no named vendor (the cleanest single signal)

**Will buy:** a press release, annual-report strategy section, investor-day slide or award submission that states a **number, a function, and a deadline** with **no vendor named**. Example shape: "reduce client onboarding from 8 hours to under 1 hour by 2027" — which is, not coincidentally, the exact shape of Orange Systems' own InSight KYC result and the shape they can credibly sell against. Also: a stated target implies an unfunded mandate, which is what a services partner is for.

**Will not buy:** the announcement is **aspirational and unquantified** ("committed to digital excellence"), or it **names a vendor** — in which case the purchase has already happened (see anti-signal A2).

**Disambiguator:** three tests, all required. (1) Is there a **number** (hours, days, %, cost)? (2) Is there a **named function or process** (onboarding, claims, KYC review)? (3) Is there **no vendor named**? All three = highest-quality lead in the taxonomy. Any one missing = downgrade by half.

### F1 — Cost-reduction programme + concurrent automation hiring in the same function (the combination signal)

**Will buy:** a restructuring or cost programme announced, **and within 90 days** automation/process roles posted **in the function that was cut**. That is not a layoff, it is a **reallocation** — the saving is being recycled into automation. This is the pattern visible in ClickUp (22% cut, 3,000 internal agents), Cloudflare (~20% cut with "measurers" in audit, finance, legal, compliance, middle management and operations explicitly targeted), LG Electronics (factory buyouts alongside an "accelerating transition to automated smart factories"), and Salesforce (customer service 9,000 → 5,000).

**Will not buy:** the cost-cutting is **survival**. Budget freeze, no hiring of any kind, no digital budget line, margin pressure in a business with no growth. Evidence against naive layoff-signal use: **Gartner's 350-executive survey found ~80% of firms deploying autonomous business capabilities cut staff, but the cuts did not translate into ROI** — reduction rates were near-identical between high-ROI and negative-ROI firms. **Layoffs alone carry near-zero information. Layoffs plus reallocation hiring carry a lot.**

**Disambiguator:** the same-function test. Cut in function X **and** hiring automation roles in function X = buy. Cut in function X and hiring in function Y = ordinary restructuring, do not score. No hiring at all = distress, do not score — and note the distress flag explicitly so the sales team doesn't waste a call.

### X1 — M&A close with a disclosed integration or synergy programme

**Will buy:** an acquisition where the integration programme is public, because two customer bases mean **duplicate KYC files, two onboarding systems, two document standards** — a compliance-shaped integration problem with a board-level synergy deadline. The spend is real and large: Bain finds systems-integration estimates **overrun by 20–50%**; **four Canadian banks spent roughly CA$1.8B in 2024** merging acquired technology stacks (RBC alone $1.06B on HSBC Canada); **~60% of synergy initiatives are IT-related and 84% of IT integrations hit material issues** [P, Synoptek].

**Will not buy:** the integration is a **financial** merger with no systems consolidation mandate (common in bank holding structures that run acquired brands on their own stacks for years); or a **tuck-in acquisition** of a small target absorbed into the acquirer's existing platform; or the acquirer already has systems-integration incumbents on retainer and the compliance workstream is theirs.

**Disambiguator:** is there a **named systems-integration or synergy programme with a date**? A synergy target with a number and a deadline is a buying signal. A press release that says "complementary cultures and cross-sell opportunities" with no integration detail is not. Then ask which entity holds the buying decision — the acquirer usually does, even when the pain sits in the target.

### X2 — New market entry, licence application, or new shared-services/capability centre

**Will buy:** a new banking/EMI/payments licence application, a new-country branch, a new shared-services centre opening, or a newly acquired book of customers. These require **building onboarding and compliance capability from zero**, on the regulator's clock, in a jurisdiction with unfamiliar document requirements. Worked example: Coris Bank International formally began CEMAC banking-authorisation procedures for Cameroon and had already held a launch meeting to integrate its future subsidiary into the regional Gimacpay payment platform **before approval completed** — the integration workstream preceded the licence. That is the buyer. Regulatory workstreams also carry hard external deadlines (Yellow Card's market-entry guidance puts approval timelines at **6–18 months** and argues compliance must be sequenced before launch, not after).

**Will not buy:** the new entity is a **branch of a parent with an existing, mandated group platform**. If the group already runs a KYC/automation stack (common with Orange Group entities, for instance), the subsidiary inherits it. The decision is not local.

**Disambiguator:** ask whether the new entity must **stand up its own compliance/onboarding capability** (buy, and usually fast) or **inherit a group platform** (no local decision). Evidence for the first: local hiring for compliance roles in the new market, local regulator filings, local vendor registration.

### P1 — Open public tender (the most verifiable signal in the set)

**Will buy:** an **open** notice for RPA, onboarding/KYC automation or cybersecurity services whose participation deadline has not passed. This is a budget that is already approved, published, dated and machine-readable. **Verified working, keyless, live:** searches return notices published the previous day. Real 2025–2026 examples found in one query: Stadt Nürnberg — framework agreement for **UiPath RPA platform services and managed services, awarded to Exelentic GmbH (winner selected 15/12/2025)**, framework value in the low seven figures (the notice's maximum-value field is high, in the €1.3m–€13.1m range); Hamburger Stadtentwässerung AöR — **UiPath automation process services, awarded to cronos automation GmbH, contract concluded 20/02/2026, 48 months**; Concello de Vigo — RPA service design and rollout, estimated €165,289; Hull University Teaching Hospitals / Humber Health Partnership — RPA administration services, **£434,550**, awarded to E18 Consulting Ltd; ITEBO GmbH — **open** EU-wide tender for RPA software including services, 48 months, TED notice 208204-2026.

**Will not buy — and this is the trap:** by the time a notice is **published**, the buying process is often 6–12 months old, the requirement has been written around a known supplier, and the award is frequently a **framework call-off or a single-supplier justification**. Note that of the five examples above, three are *award notices* — a competitor has already won. The Nürnberg and Hamburg cases were effectively closed before we could see them.

**Disambiguator:** the notice **type**. `cn-standard` / open procedure with a live submission deadline = real lead. `can-standard` award notice = **competitive intel, not a lead** — record the winner, the platform and the contract end date, and set a re-tender reminder for 24–36 months out. Also check the procedure type: single-supplier justification or framework call-off = do not score as a lead.

### L1 / L2 — Leadership changes (the classic trigger, and the one the evidence contradicts)

**The sales-training version:** a new CIO/COO/CDO spends their honeymoon releasing budget, so call them in the first 90 days. Measured support for the hiring half of this is modest: Bloomberry found **VP/executive hires at +28%** purchase lift — real, but half the lift of an AI-tool purchase.

**What the peer-reviewed evidence actually says:** *Intintoli, Serfling & Shaikh, JFQA 52(6), 2017* found that when a customer's CEO turns over, **suppliers lose substantial sales** — the effect being stronger when the departure was forced or the appointment is an outsider. **A leadership change produces a review of the inherited stack, not a release of new budget.** This is a **displacement** signal, not a greenfield signal, and the correct pitch is "the alternative to what is already installed", not "congratulations on the new role, here's what you need."

**Will buy (as displacement):** a new COO/Head of Operations at a company running a **tired or failing** automation or KYC programme, where the incoming executive has a mandate to fix operational cost and comes from an organisation that ran automation. They have a reason to replace something, a honeymoon mandate to do it, and — critically — first-hand knowledge of what "good" looks like.

**Will not buy:** the new executive spends 90 days listening and changes nothing — the standard 30/60/90 plan dedicates days 1–30 to inventorying applications, contracts and spend, and days 31–60 to reviewing vendor performance, *not* to new purchases. Many bring their own incumbent vendors from a previous employer, which is a threat to us, not an opportunity. And a CIO appointed explicitly to **cut** IT cost is a headwind: Bain notes legacy systems can consume 85%+ of IT outlays, leaving ~15% discretionary, with operating costs needing to fall 20–30% to free anything.

**Disambiguator — two checks, both doable without LinkedIn:**
1. **The predecessor's fate.** Forced/abrupt departure, or the appointment follows a public incident, fine or audit finding → displacement window is open. Ordinary retirement or internal promotion → the stack is safe and the trigger is weak.
2. **The incoming executive's prior employer's stack.** Came from a company publicly running UiPath/RPA or a vendor KYC platform → high-probability re-buy or displacement — prioritise. Came from a company with no automation footprint → much weaker. Checkable from a press-release bio or a trade-press interview.

**And be honest about the timing convention:** the "act within 30/60/90 days" guidance is unsourced and mutually contradictory across vendors. We use 30–60 days because it is the median of unsourced advice and matches the standard onboarding plan. Present it as a convention, not a measurement.

### T1 — Incumbent RPA platform already in production

**Will buy:** the platform is in production and the *programme* is struggling — postings for "RPA maintenance", "bot support", "automation governance", a CoE lead hired after 2–3 years of an existing programme, or public commentary about a stalled programme. This is Orange Systems' **CoE remediation and managed-service** pitch, backed by hard numbers: **30–50% of initial RPA implementations never take off; 60–70% miss expectations; 70–75% of budget goes to maintenance.** An account with an installed platform and a decay problem is a better prospect than one with no platform at all.

**Will not buy:** the platform is in production and healthy, with a scaled internal CoE and a named platform partner. Then Orange Systems is the vendor being *displaced*, not added, and there is no entry without a decay signal.

**Disambiguator:** does the evidence describe **installation** (weak-buy) or **deterioration** (strong-buy)? Look for maintenance/support/governance postings, "stabilise", "rescue", "re-platform", or a CoE leadership role created 2+ years *after* the first automation role.

### T3 — Technographic immaturity

**Will buy:** no self-service onboarding, no customer portal, legacy forms — a company whose customer-facing operations are still paper-and-PDF. Manual onboarding at scale is exactly the InSight KYC problem.

**Will not buy — and this is the important half:** low digital maturity is **also the leading reason RPA programmes fail.** Cited failure causes are unstructured data, unclear business rules, and half-baked process definition. A company with messy data and undefined rules cannot successfully automate, and its first project will fail regardless of who delivers it. This signal is double-edged and must be scored with the data-readiness question, not in isolation.

**Disambiguator:** pair T3 with a **process-definition signal** — a process-excellence or business-analysis hire (H1), a named process owner, or an ISO/quality programme. Immature stack **plus** process discipline = good ICP. Immature stack **alone** = likely a failed engagement and a damaged reference.

---

## 4. Ranked shortlist (strength × detectability in 48 hours)

Ranked by `S×D`, ties broken by strength. **Bold** = build these first.

| Rank | Signal | S×D | Why it's here / what it costs us |
| --- | --- | --- | --- |
| 1 | **R1 — Company-specific AML/KYC enforcement action** | 20 | Highest strength in the set. Free registers exist but need scraping; no free enforcement API. Fine fact-pattern classification is the work. |
| 2 | **O1 — Automation programme, quantified target, no named vendor** | 20 | Cleanest logic, and it speaks Orange Systems' own language (hours saved, onboarding time). Detection is LLM-extraction over news + annual reports. |
| 3 | **F1 — Cost reduction + same-function automation hiring** | 20 | Evidence-backed counter to the naive layoff signal (Gartner's null ROI finding). Requires joining two source types on one company — this is the platform's actual value-add. |
| 4 | **X2 — New market entry / licence / new capability centre** | 20 | Very high precision; strong fit with Orange's AMEA footprint. Detection is news + regulator registers. |
| 5 | **R3 — Public breach disclosure** | 20 | **Fully verified free API** (SEC EDGAR full-text search, keyless). The most machine-readable strong signal available. |
| 6 | **P1 — Open public tender** | 20 | **Fully verified free keyless API** (TED v3), returning notices published the previous day. Must filter notice type or it converts into competitor intel. |
| 7 | H1 — Founding automation/process-excellence leadership role | 16 | Cheap to detect via ATS JSON; the "who builds" test does the disambiguation. |
| 8 | H4 — Compliance/onboarding volume hiring | 16 | Directly maps to InSight KYC. Needs a posting-history baseline to distinguish growth from steady state. |
| 9 | H6 — First security/financial-crime function creation | 16 | Greenfield = no incumbent vendor. Easy to verify from an ATS board's history. |
| 10 | T2 — ERP / core-system migration | 16 | Large integration programmes pull process automation behind them. |
| 11 | X1 — M&A close with integration programme | 16 | Duplicate KYC files and two onboarding systems is a KYC automation conversation with a deadline. |
| 12 | H3 — Postings naming a specific platform | 15 | High detectability, but reads as *build* as often as *buy*. Needs H1/H5 to become a lead. |
| 13 | H5 — Manual-process pain language in a posting | 15 | Cheap, and it is the company naming its own pain. Vulnerable to JD boilerplate without a named process. |
| 14 | R2 — AML 2027 obligation **with a measured readiness gap** | 12 | Upgraded from 9. A dated public deadline, a published readiness gap (only ~⅓ expect to be ready) and stated technology budget intent in one survey (PwC, 531 institutions). Nothing else in the taxonomy has all three. |
| 15 | L1 / L2 — New CIO/COO/CISO/MLRO | 12 | Real but modest (+28% per Bloomberry), detection without LinkedIn depends on press releases, and the JFQA evidence says treat it as **displacement, not greenfield**. |
| 16 | F2 — Funding with operations/compliance in use of proceeds | 12 | +25% lift, measured — weaker than AI adoption or headcount growth. The "spend within 6–12 months" rule is folklore: the 6–12 month figure is the seed-stage interval to the *next round*. |
| 17 | F3 — Earnings-call efficiency language | 12 | Free transcripts exist (Investing.com, AlphaStreet, company IR PDFs). Very high base rate — only quantified, function-specific statements count. |
| 18 | H2 — Automation engineering role cluster | 10 | Detectable everywhere, but *the weakest measured signal in the literature* (+7%). Keep it as a tiebreaker, never as a trigger. |
| 19 | T3 — Technographic immaturity | 10 | Double-edged: also predicts automation failure. |
| 20 | R4 — FATF list change | 10 | Country-level, not company-level. Use as a market filter. |
| 21 | P2 — Prior automation award notice | 10 | Not a lead. Competitive intel and re-tender timing. |
| 22 | O2 — Complaint/outage surge | 9 | Noisy, hard to attribute to a process problem rather than a product bug. |
| 23 | T1 — Incumbent platform in production | 9 | Strong *if* decay evidence exists; otherwise it's the competitor's account. |
| 24 | O3 — Glassdoor manual-process complaints | 4 | Biased sample (the thrilled and the furious write, the middle stays silent), small-N, and **must never be referenced in outreach** — it reads as surveillance. Keep for discovery questions only, or cut it. |
| — | A1 / A2 — Anti-signals | — | Strip A1 at extraction. A2 is a hard negative. |

**Cut from the shortlist deliberately:** standalone job-posting growth (H2) as a trigger; generic digital-transformation language (A1); employee-review sentiment as a scored signal (O3). All three are the kind of signal that looks smart in a demo and would be dismantled by a jury member who has run an RPA sales team.

---

## 5. Verified data reachability (checked live on 2026-09-25)

Detection claims above are only worth what the sources are worth, so the top ones were tested rather than assumed.

| Source | Status | Evidence from the test |
| --- | --- | --- |
| **TED v3 search API** ✔ | **Free, keyless, works** | `POST https://api.ted.europa.eu/v3/notices/search` returned a notice published **2026-09-24** (Śląski Urząd Wojewódzki, CPV 72212460). Supports `FT~"robotic process automation"`, `buyer-country`, `classification-cpv`, date ranges, `scope: ALL` back to the mid-2010s, `limit` ≤100. Only *submitting* notices needs auth. TED Open Data Service adds SPARQL at https://data.ted.europa.eu. |
| **SEC EDGAR full-text search** ✔ | **Free, keyless, works** | Query for 8-Ks mentioning Item 1.05 with a date range returned **8 material cybersecurity incident filings since 2026-08-01** (e.g. Astrana Health, filed 2026-09-23, incident 2026-09-22). 10 req/s; a descriptive `User-Agent` is required or the request 403s. Item 1.05 is due **4 business days from materiality determination**; compliance began 18 Dec 2023. https://efts.sec.gov/LATEST/search-index |
| **Greenhouse job board JSON** ✔ | **Free, keyless, works** | `https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true` returned full postings. The binding constraint is **slug discovery** — there is no cross-company directory, so slugs must come from careers-page links or sitemaps. |
| **Lever postings JSON** ✔ | **Free, keyless, works** | `https://api.lever.co/v0/postings/{slug}?mode=json` returned Spotify's postings. Caveat found: an unknown slug returns `{"ok":false,"error":"Document not found"}` — **the same response as a company with zero openings**, so absence is not evidence of absence. |
| **GDELT doc API** ✔ with a limit | Free, keyless, **rate-limited** | A single query triggered a "limit requests to one every 5 seconds" response. Usable, but the pipeline must throttle. |
| **FCA / FinCEN enforcement pages** ⚠ | Free but **bot-protected — verified 403** | A plain request to https://www.fca.org.uk/publication/final-notices/ returned **HTTP 403**. Needs a real browser (Playwright) and rate limiting, or a cached/alternative route. FinCEN: https://www.fincen.gov/news/enforcement-actions. **R1's detectability of 4 assumes Playwright is available; without it, drop R1 to 3.** |
| **CA / Maine AG breach lists** ⚠ | Free, but **unreached from this environment** | https://oag.ca.gov/ecrime/databreach/list returned no response in the test window (timeout), so treat its reachability as unproven from here — check before the demo. https://www.maine.gov/agviewer/content/ag/985235c7-cb95-4be2-8792-a1252b4f8318/list.html — Maine uniquely publishes **date of breach, date of discovery and date of notification**. SEC EDGAR full-text search (verified working) already covers the material-incident subset, so R3 does not depend on these. |
| **GDPR Enforcement Tracker** ✔ | Free, **verified HTTP 200** | https://www.enforcementtracker.com/ — each fine carries a permanent ID, but coverage is incomplete because DPA disclosure regimes vary (full disclosure in e.g. IE, ES, NL, FI; exceptional/none in AT, SK, DE). |
| **Technographics (BuiltWith / Wappalyzer)** ⚠ | Mostly paywalled | BuiltWith's free API (`api.builtwith.com/free1/`) works but returns **only category counts, no technology names** — which is the binding constraint. Wappalyzer's free tier is a browser extension; its lookup is server-side and **misses client-rendered frameworks**. **Critically, RPA platforms are back-office and invisible to homepage scanners entirely** — T1 can only be inferred from job postings and case studies. |
| **LinkedIn** ✘ | Excluded by the brief | The brief forbids depending on it. L1/L2 detection must route through press releases, company newsrooms, regulator registers and Crunchbase's public pages. |
| **Crunchbase / ZoomInfo / Bombora / G2** ✘ | Paid, closed | Named in the brief but unusable this weekend. The design must degrade gracefully to free sources. |

**The honest detectability conclusion:** the signals that are both strong and cheap to detect are exactly the ones with **primary-source, dated, machine-readable evidence** — tenders, breach filings, enforcement actions, news announcements, ATS postings. The signals that require inference about *interest* (intent data, surge scores, technographics) are the ones we cannot detect for free, cannot validate, and — per the one peer-reviewed test — do not beat random prospecting anyway. **That alignment is the argument to make to the jury: the free signals happen to be the defensible ones.**

---

## 6. Disambiguation rules

These convert raw signals into a verdict. They are the artefact the jury should be shown, because they are what makes the false-positive claim testable.

**Rule 0 — ICP gates run before any signal is scored.**
No signal can rescue a company that fails the structural gates: in-scope industry (financial services, insurance, telco, healthcare admin, public sector — large manual back-office volume), a real back-office function, a deliverable geography, and a size where a services contract is meaningful. A brilliant trigger on a 40-person company is noise.

**Rule 1 — The "who builds" test (hiring).**
Read the job description for the verb. *Choose / manage / govern / build the business case* → **buy**. *Build / develop / code* → **build**, move to nurture. Corroborate with count (one leader = buying shape; ≥3 developers = building shape) and ownership (business-owned = buy; IT-owned = build).

**Rule 2 — The "data-shaped or reporting-shaped" test (enforcement).**
Classify the finding before scoring. Customer-file data quality, file currency, UBO verification, monitoring execution → **buy**. Reporting timeliness, registration and governance lapses → **do not score**. FCA data: data deficiencies feature in 68% of AML Final Notices; outdated customer information 45%, UBO verification 32%.

**Rule 3 — The "number, function, no vendor" test (announcements).**
An automation announcement is a lead only if it states a **quantified target**, names a **function or process**, and names **no vendor**. All three → highest-quality lead. Any one missing → halve the score. A vendor named → **hard negative** (A2), record as competitive intel and set a re-tender reminder.

**Rule 4 — The "same function" test (financial).**
Cost-cutting alone scores zero. It scores only when a reduction and automation hiring co-occur **in the same function within 90 days** — that is reallocation. Different function = ordinary restructuring. No hiring at all = distress, flag it, do not call it a lead. Evidence: Gartner's 350-executive survey — ~80% of autonomous-business adopters cut staff; **the cuts did not produce ROI.**

**Rule 5 — The two-source rule (the core false-positive control).**
**No account reaches the top of the queue on one signal class.** Require **≥2 independent signal classes**, and at least one must be a **non-hiring** class (regulatory, operational, financial, procurement, M&A/expansion). Rationale: hiring is the weakest measured category (+7% for posting growth; +28% for exec hires) and the easiest to over-read, so it may inform but never carry a verdict. Two hiring signals are one signal.

**Rule 6 — Recency windows (a signal past its window scores zero, regardless of strength).**

| Signal | Live window | Reasoning |
| --- | --- | --- |
| Enforcement action (R1) | ≤18 months | Remediation budget is committed and spendable |
| Breach disclosure (R3) | Approach at 90–270 days | 0–90 is containment; after 270 the budget is allocated |
| Executive appointment (L1/L2) | ≤180 days; approach at 30–60 | The 90-day listening phase, then a review of the inherited stack (JFQA 2017) |
| Funding (F2) | ≤90 days; approach at 2–8 weeks | Waiting out the immediate noise outperforms |
| Cost programme (F1) | ≤90 days from the cut | The reallocation decision is contemporaneous |
| Tender (P1) | Submission deadline still open | After the deadline it becomes P2, competitive intel |
| Job posting (H1–H6) | ≤60 days from posting | Stale postings are backfill or ghost jobs |

**Rule 7 — The subtractive rules (what removes an account from the queue entirely).**
1. A **named vendor** has already delivered the outcome the account would buy (A2).
2. The account is a **competitor** — a BPO/IT services firm with automation at the core of its own offering. They sell what Orange Systems sells.
3. The account already operates a **scaled, healthy automation CoE** with a named platform partner. No entry without a decay signal.
4. The account shows **distress**: insolvency/administration proceedings, a blanket hiring freeze, or a cost programme with no reallocation hiring.
5. The **only** evidence is generic transformation language (A1) with no entity, number, function or date.
6. The **only** hiring evidence has a **build-shaped** job description (Rule 1).
7. The signal attaches to a **subsidiary that is not the buying entity** — group-level platforms are usually mandated by the parent, so a local trigger does not imply a local decision.
8. The account is an **existing Orange Systems or Orange Group client** (MasOrange, Orange Jordan and similar). That is account expansion, and it must not contaminate new-business scoring.

**Rule 8 — Reporting discipline (how we present the model so it cannot be attacked).**
- Report **precision at a fixed recall**, with the threshold and the number of accounts reviewed. **Never report "accuracy"** — at a ~5% base rate, 90% accuracy is compatible with near-useless precision.
- Until labelled outcomes exist, report **signal counts and coverage** (how many accounts matched how many independent signal classes) and say explicitly that conversion data does not exist yet. Claiming a validated conversion rate in a 48-hour build is the fastest way to lose the room.
- **Display the counter-interpretation on every prospect card**, next to the score. A card that says "hiring RPA developers (score 0.71) — could mean in-house build; no leadership or vendor-selection role found; single signal class, capped below the outreach threshold" is worth more to this rubric than any model architecture.
- **Cap the daily outreach list.** A ranking that returns 400 "hot" accounts has not prioritised anything. Show the team the top N with the reason, and let the cap be a visible, configurable number.
- Never reference Glassdoor content, employee reviews or individual browsing behaviour in generated outreach. It reads as surveillance and it will be noticed.

**The one-line summary of the whole document, for the pitch:** *we do not claim to find buyers in the 95% who are not buying; we claim to catch the small number of dated, public, primary-source events that move an account from "not now" to "now" — and we show, on every card, the reading of that event under which it means the opposite.*

---

## 7. Four objections to have an answer ready for

1. **"Hiring an RPA developer means they'll build in-house."**
   Agreed, and our model says so: the "who builds" test (Rule 1) disqualifies build-shaped postings, and hiring is barred from carrying a verdict alone (Rule 5). PwC's ~70% internal-operations figure and Bloomberry's +7% for posting growth are the two numbers behind that design choice.

2. **"Job postings are weak — so why are half your signals hiring signals?"**
   They are not the verdict; they are the *corroborator*. Of the six top-ranked signals, none is a hiring signal. Hiring signals appear in the taxonomy because sales teams ask about them and because they are cheap, but Rules 5 and 7 stop them from driving the queue. The measured evidence (weakest of eight signals at +7%) is why.

3. **"A regulator's fine means they're already fixing it with someone else."**
   Sometimes, and our Rule 2 and Rule 6 handle it: only data-shaped findings score, and only for ~18 months. But a data-shaped finding on customer-file quality — 68% of FCA AML Final Notices, with outdated customer information in 45% and UBO verification failures in 32% — is a board-level, dated obligation to fix exactly what InSight KYC does, and it is public, which means we see it the same week the market does.

4. **"A new CIO means new budget — you should be calling in the first 90 days."**
   The peer-reviewed answer is the opposite, and it is our best differentiator: *Intintoli, Serfling & Shaikh, JFQA 2017* found suppliers **lose sales** after a customer's CEO turns over, with the effect strongest for forced departures and outside appointments. A leadership change produces a **review of the inherited stack**, not a release of new budget — so we score it as a **displacement** signal and generate displacement-shaped outreach ("the alternative to what you inherited"), not congratulations. The unsourced "70% of budget in 100 days" claim is not something we put on a slide.

---

## 8. Sources

Primary research and data
- Dawes / Ehrenberg-Bass, 95:5 rule (2021) — https://marketingscience.info/news-and-insights/advertising-effectiveness-and-the-95-5-rule-most-b2b-buyers-are-not-in-the-market-right-now
- Forrester, Buyers' Journey Survey 2025 — https://www.forrester.com/blogs/preference-matters-more-than-in-market-intent-alone-in-modern-b2b-buying/
- Forrester, buyers enter with a vendor in mind (92% / 41%) — https://www.marketingweek.com/b2b-buyers-vendor-before/
- Forrester Q3 2022 Buying Motions Survey, prior experience is the top trigger — https://go.forrester.com/wp-content/uploads/2023/06/Forrester-Lifecycle-Revenue-Marketing_final.pdf
- Forrester, State of Business Buying 2026 (13 internal + 9 external participants) — https://www.biia.com/forrester-b2b-buying-groups-expand-as-they-question-ai/
- Gartner, B2B Tech Buying Teams Are Struggling (G00791700), 2022 survey n=3,048, 17+ month cycle, 81% regret — https://emt.gartnerweb.com/ngw/globalassets/en/doc/documents/791700-b2b-tech-buying-teams-are-struggling-here-is-how-to-guide-them.pdf
- Gartner press release, 17% supplier time (2017 survey, n=750) — https://www.gartner.com/en/newsroom/press-releases/2020-09-15-gartner-says-80--of-b2b-sales-interactions-between-su
- Robinson, Faris & Wind, BUYGRID (1967) and the 30-year retrospective — https://faculty.wharton.upenn.edu/wp-content/uploads/2012/04/9802_The_BuyGrid_Model_30_Years.pdf
- Craig Elias, SHiFT! (2011) / "Window of Dissatisfaction" 74% claim, as cited — https://www.insidesales.com/leveraging-trigger-events/
- Intintoli, Serfling & Shaikh, CEO Turnovers and Disruptions in Customer–Supplier Relationships, JFQA 52(6):2565–2610 (2017) — https://www.cambridge.org/core/journals/journal-of-financial-and-quantitative-analysis/article/abs/ceo-turnovers-and-disruptions-in-customersupplier-relationships/0E6F10186579FFF363126AFA744DBF68
- IAPP GDPR compliance-timing meta-analysis (12 surveys, 10,247 respondents) — https://iapp.org/media/pdf/resource_center/timelines_for_gdpr_compliance.pdf
- PwC EMEA AML Survey 2026 (531 institutions, 40 countries) — https://www.pwc.lu/en/anti-money-laundering/docs/pwc-emea-aml-survey-2026.pdf
- UserGems new-hire signal (unsourced "70% of budget in 100 days") — https://www.usergems.com/blog/new-hire-buying-signal
- Corporate Visions / Riesterer & Lee, what gets executives to act — http://win.corporatevisions.com/rs/413-YED-439/images/Article-What-Gets-Executives-To-Act.pdf
- CEB/Gartner "57% before contacting sales" and its takedown — http://www.jvminc.com/Clients/JVP/57percent.pdf
- Neumann, Tucker, Subramanyam & Marshall, *Quantitative Marketing and Economics* 21(4):519–571 (2023), DOI 10.1007/s11129-023-09268-7 — https://papers.ssrn.com/sol3/papers.cfm?abstract_id=4083057
- Bloomberry, 1M software purchases, 8 intent signals — https://bloomberry.com/blog/i-analyzed-1m-software-purchases-to-find-the-strongest-buyer-intent-signals/
- Bombora surge-detector patent US20160132906A1 — https://patents.google.com/patent/US20160132906A1/en
- Gartner B2B buying journey (17% supplier time) — https://www.gartner.ca/en/sales/insights/b2b-buying-journey
- Gartner autonomous-business / layoff ROI survey, 350 executives — https://www.thehindubusinessline.com/info-tech/autonomous-business-and-ai-layoffs-do-not-deliver-returns-gartner/article70943654.ece
- WSJ Intelligence / B2B International 2021 trigger survey (reported) — https://customerthink.com/unveiling-the-mechanics-of-b2b-buying-triggers/
- Trigger-event folklore audit — https://www.recordcontext.com/blog/trigger-events-research-disappears
- Celent Global Commercial Banking Onboarding Survey 2025 (via nCino) — https://www.ncino.com/en-EMEA/blog/how-to-transform-commercial-onboarding-from-a-cost-center-to-your-competitive-advantage
- Fenergo / FinTech Global, slow onboarding and client loss — https://fintech.global/2025/10/08/70-of-banks-lose-clients-due-to-slow-onboarding/
- FCA AML fines analysis (data deficiencies 68%) — https://www.kyckr.com/blog/fca-aml-fines
- PwC, RPA adoption and internal-capability findings — https://www.pwc.dk/da/publikationer/2017/rpa.pdf
- UiPath / Forrester Total Economic Impact (CoE, internal capability) — https://start.uipath.com/rs/995-XLT-886/images/Forrester_TEI_of_UiPath_Automation_2021.pdf
- RPA failure and maintenance rates — https://www.fortra.com/blog/rpa-implementation-guide
- G2 Buyer Intent documentation — https://documentation.g2.com/docs/buyer-intent
- Dreamdata, G2 intent benchmark — https://dreamdata.io/blog/benchmarks-report-measuring-g2-intent-data-impact-b2b-buyer-journeys
- Intent data critique (practitioner) — https://www.marketsizer.io/blog/why-intent-data-made-b2b-outreach-worse
- Hiring-signal practitioner hierarchy — https://crustdata.com/blog/hiring-signals · https://predictleads.com/blog/hiring-as-a-buying-signal/

Regulatory
- AMLR (EU) 2024/1624, application 10 July 2027 — https://eur-lex.europa.eu/eli/reg/2024/1624/oj
- AMLD6 (EU) 2024/1640, staggered transposition — https://eur-lex.europa.eu/eli/dir/2024/1640/oj
- AMLA — https://www.amla.europa.eu
- DORA (EU) 2022/2554, applicable 17 January 2025 — https://eur-lex.europa.eu/eli/reg/2022/2554/oj
- NIS2 (EU) 2022/2555, transposition deadline 17 October 2024 — https://digital-strategy.ec.europa.eu/en/node/13186
- EU AI Act (EU) 2024/1689, staged application — https://artificialintelligenceact.eu/article/113/
- FATF high-risk and monitored jurisdictions — https://www.fatf-gafi.org/en/topics/high-risk-and-other-monitored-jurisdictions.html

Detection sources (verified live in §5)
- TED v3 — `POST https://api.ted.europa.eu/v3/notices/search` · open data https://data.ted.europa.eu · docs https://docs.ted.europa.eu
- SEC EDGAR full-text search — https://efts.sec.gov/LATEST/search-index and https://www.sec.gov/edgar/search/
- Greenhouse board API — https://boards-api.greenhouse.io/v1/boards/{slug}/jobs
- Lever postings API — https://api.lever.co/v0/postings/{slug}?mode=json
- GDELT doc API — https://api.gdeltproject.org/api/v2/doc/doc (throttle to 1 request / 5 s)
- GDPR Enforcement Tracker — https://www.enforcementtracker.com/
- FCA Final Notices — https://www.fca.org.uk/publication/final-notices/ · FinCEN — https://www.fincen.gov/news/enforcement-actions
- California AG breach list — https://oag.ca.gov/ecrime/databreach/list · Maine AG — https://www.maine.gov/agviewer/content/ag/985235c7-cb95-4be2-8792-a1252b4f8318/list.html
