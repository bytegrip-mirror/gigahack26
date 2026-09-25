# 12 — Competitive Landscape, The Real Gap, and The Wedge

**Question this file answers:** what already exists, what does not, and which differentiator survives a Q&A with a juror who knows the sales-tool market.
**Rubric targets:** Signal Relevance & Accuracy 25% · AI/ML Innovation 20% · Configurability 20%.
**Evidence rule:** every claim below carries a URL. Where I could not fetch the vendor's own page I mark it **unverified** — do not put an unverified number on a slide.

---

## 1. Incumbent map

Grouped by what they actually sell, because that is what determines whether they are a threat.

### A. Enrichment / GTM-engineering platforms

| Vendor | What it actually is | Who it serves | Pricing model | What it does NOT do |
| --- | --- | --- | --- | --- |
| **Clay** | Waterfall enrichment + Claygent AI research over 200+ providers; signals are a feature of the table builder | RevOps / GTM engineers, not reps (unlimited seats is a platform property) | Free · Launch **$167/mo** · Growth **$446/mo** · Enterprise custom; dual meter (Data Credits + Actions); FAQ on the same page says $185/$495 — a live inconsistency ([clay.com/pricing](https://www.clay.com/pricing)) | Signals gate by tier: job change/news/social at Launch, **web intent + webhooks only at Growth**. Custom signal = "turn any of Clay's 200+ enrichments or a AI agent query into a signal" — column/workflow configuration, not a plain-language question. **No weights, no negative rules, no per-signal source citation** ([clay.com/signals](https://www.clay.com/signals)) |
| **Apollo.io** | Contact database + sequencing | SMB/mid-market outbound | Free · Basic $49 · Professional $79 · Organization $119 per seat/mo annual (third-party: [docket.io](https://docket.io/resources/research/apollo-pricing)) | Intent is **Bombora resold**: pick from 15,000+ vendor topics, saved topics locked for 24h ([Apollo KB](https://knowledge.apollo.io/hc/en-us/articles/8047704465933-Buying-Intent-Overview)). Selection, not definition |
| **ZoomInfo** | Contact data + intent + Copilot | Enterprise | No public pricing; annual, negotiated (third-party entry ~$15K/yr) | 50+ **vendor-defined** signal types; intent topic is a required API parameter, not a user-authored question ([docs.gtm.ai](https://docs.gtm.ai/docs/signals-insights)) |
| **Cognism** | EU/GDPR phone-verified data | EU mid-market/enterprise | Quote-only; Standard & Pro, 5 seats included ([cognism.com/pricing](https://www.cognism.com/pricing)) | Intent is a **Pro feature sourced from Bombora Company Surge, capped at 12 topics** — selected from a library Cognism lists as "11,000+" options (Bombora's own current count is 21,600+; the two vendor numbers disagree). The cap is the product |
| **LeadIQ** | Data + light signals | SMB | Free · **Pro $200/mo up to 5 users** · Enterprise annual; email 1 credit, phone 10 ([leadiq.com/pricing](https://leadiq.com/pricing)) | Signals (10-K, podcast mentions, hiring) are a fixed list. No intent product named on its own pricing page |
| **Lusha** | Contact data | SMB | Free · Starter **$49.90/mo** · Pro $69.90 · Premium $399.90 ([lusha.com/pricing](https://www.lusha.com/pricing/)) | **Buying intent is not mentioned on Lusha's own pricing page at all.** No user-defined signals |

### B. Intent-data networks (the wholesale layer)

| Vendor | What it actually is | Pricing | What it does NOT do |
| --- | --- | --- | --- |
| **Bombora** | A **data co-op**: consent-based content-consumption data from a network of B2B publisher sites, aggregated to company-level topic surge scores | Vendr, 35 recorded purchases: **median $25,000/yr, range $13,000–$80,450**. Top cost driver is the **number of topics tracked** (~50–500+), not seats; custom API access commonly +$5,000–20,000/yr ([Vendr](https://www.vendr.com/marketplace/bombora)). Resold through ZoomInfo, Cognism, Apollo, Common Room, Warmly | Topics are **vendor-defined concepts, not customer questions**. Customers nominate topics; Bombora vets them **quarterly** against *"relevance, scale, and sustained engagement"* — so a niche service question is structurally rejectable. Approved topics join a shared taxonomy of **21,600+ topics** available to all customers ([intent-topics-we-track](https://bombora.com/intent-topics-we-track/)). Nomination paperwork states a **4–6 month** collection/QA timeline ([Topic Submission Instructions PDF](https://bombora.com/wp-content/uploads/2024/09/Bombora-Topic-Submission-Instructions-PDF.pdf)) |
| **G2 Buyer Intent** | Second-party: review/comparison activity on G2's own pages (plus Capterra, Software Advice, GetApp) | Custom; third-party reports **$10–40K/yr** mid-market ([ContactLevel](https://www.contactlevel.com/resources/g2-intent-data) — a G2 competitor, read as adversarial) | Only their own traffic. Practitioner reviews are blunt: *"Noisy signals – needs a lot of manual work to be useful"* and *"G2's intent signals are not strong"* (G2 product reviews, Feb 2026; surfaced via search index, permalink not captured). Resolves to the **organisation, not the person** |
| **6sense** | Own bidstream/co-op (Signalverse) + Bombora, predictive **6QA** model, buying-stage assignment | Quote-only; entry ~$50–60K/yr, enterprise $120–400K+ ([salesmotion](https://salesmotion.io/blog/abm-platform-cost)) | Proprietary predictive model described as *"a black box that is hard to debug when scores look wrong"*; no custom-topic builder documented |
| **Demandbase** | ABM + intent (blends Bombora, historically some ZoomInfo-sourced) | Quote-only; entry ~$50–75K/yr; media billed separately | Configuration is journey-stage tuning, not question authoring |
| **TechTarget Priority Engine / TrustRadius** | Publisher- and review-network intent | Quote-only | Same structural limit: their traffic, their taxonomy |

### C. Signal-first startups (2024–2026)

| Vendor | What it does | Pricing | Custom signals? |
| --- | --- | --- | --- |
| **Common Room** | Unified 1st/2nd/3rd-party signals to person-level profiles | Essential **$2,500/mo** billed annually, 5 seats ([pricing](https://www.commonroom.io/pricing/)) | Partial. Blog claims custom topics from internal data; docs list only native signals. **Bombora topics capped at 6/12/25 per tier.** Zoom announced an agreement to acquire it, July 2026 ([GeekWire](https://www.geekwire.com/2026/zoom-snaps-up-seattle-startup-common-room-to-bolster-ai-powered-sales-tools/)) |
| **Warmly** | Signal-based revenue orchestration for mid-market+ | Web de-anonymization **from $10,000/yr**; **GTM Signals Package +$10,000/yr** ([pricing](https://www.warmly.ai/p/pricing)) | No authoring. But it does publish a **"Weighted, Transparent, Explainable"** table (pricing-page visit = High, Bombora surge = Medium, homepage = Low) — and then says *"Warmly's ML model automatically optimizes signal weights"* ([signals](https://www.warmly.ai/p/signals)). The sales team can see weights; it cannot author negative rules |
| **Unify** | Signal catalog + sequencing, self-serve | Free (3 seats) · **$20/seat/mo** Base · $60 Pro ([pricing](https://www.unifygtm.com/pricing)) | Fixed named catalog |
| **Trigify** | Social signals | **Dead.** *"the Trigify team is joining HubSpot"* (23 Sep 2026); platform retired 22 Oct 2026; accounts not migrated ([trigify.io](https://trigify.io/)) | Its "16 signal types" were a fixed list you could only toggle ([help](https://help.trigify.io/help/articles/1828448-what-is-social-signals)) |
| **Salesmotion** | Account intelligence, self-serve | **$85/mo** Individual; "Custom signals & data sources" only on Teams ([pricing](https://salesmotion.io/pricing)) | Custom signals exist but are gated to a custom-quoted tier |
| **UserGems** | Champion job-change tracking | Core **$40K/yr** · Advanced **$75K/yr** · Elite **$150K/yr** + $3–10K implementation ([pricing](https://www.usergems.com/pricing)) | Yes, two kinds: push your own first-party data by CSV/API, and a **Research Agent** you brief "in plain English" e.g. *"find whether these companies are SOC 2 Type II compliant"* ([docs](https://help.usergems.com/article/research-agent)) |
| **Avina** | Signals agent over filings, changelogs, earnings calls | Not verified | **The strongest verified plain-language implementation:** *"You describe what an in-market trigger looks like in natural language"*; each hit scored on ICP fit, intent strength, role relevance, buying urgency; custom signals *"carry the highest weight in Avina's Account Score"* ([docs](https://docs.avina.io/signals/custom-ai-signals)). **No negative/disqualification rules documented**, and a Product Hunt thread confirms it **does not retroactively disqualify** a lead whose situation changed |
| **WhiteWhale** | Flat-fee signal monitoring, **$200/mo unlimited seats**, "up to 35 custom signal questions in plain English" with a verified source quote per alert | Third-party listings only — **unverified** ([rightaichoice](https://rightaichoice.com/tools/whitewhale)) | If real, it is the closest direct comp on price and plain-language authoring. It deliberately excludes contact data, drafting and sequencing |
| **Landbase** | IT services & systems integrators vertical | Demo-only, no public pricing ([landbase.com/it-services](https://www.landbase.com/it-services)) | **No.** It tracks *"1,500+ real-time signals"* including *"Managed Services RFP"*, *"IT Partner Contract Expiry"*, *"CIO Leadership Change"* — but the **catalog is Landbase-maintained**; the customer configures ICP criteria only |
| **Beolta** | "AI buyer-intent matching for IT service teams… evidence-backed shortlists before a formal RFP" | "Free Options"; *"Paid plans unlock data depth, never better ranking"* | Signal definition not documented. Early: launched 2026, 7 followers, no reviews, pre-seed ([Product Hunt](https://www.producthunt.com/products/beolta)) |
| **Pocus** | PLG product-usage signals | — | **Acquired by Apollo** ([pocus.com](https://www.pocus.com/)) |
| **Ocean.io / Attention / Default / Sybill** | Lookalike search / call recording / inbound routing / meeting notes | various | Not signal-authoring products. Ocean.io pricing **unverified** (403) |

### D. Adjacent, not competitors

HubSpot (CRM, now absorbing Trigify's team), Zoom (buying Common Room), Apollo (bought Pocus). The pattern for 2026: **consolidation into CRM and engagement suites.** That is an argument *for* a narrow configurable layer, not against it — but it also means "HubSpot will just do this" is a question you will be asked, so read §2 carefully.

---

## 2. The gap — what is actually left

### 2.1 Plain-language custom signals are **already shipped**. This is not the wedge.

Believing otherwise is the single fastest way to lose the room. The evidence:

- **Clay Custom Signals** — reported May 2025 ([Claymation](https://www.claymation.io/p/prompt-finding-ideal-customer-signals-for-clay-custom-signals)); Clay's own page now says "turn any of Clay's 200+ enrichments or a AI agent query into a signal" ([clay.com/signals](https://www.clay.com/signals)).
- **HubSpot Custom Signals** — reported shipped **30 June 2026**, inside the Buyer Intent app: *"You write a plain-language description of a buying moment that matters to your specific business… No prompt engineering. No boolean logic"*, with *"cited sources so you can verify the signal"* and a preview step ([Sidekick Strategies](https://www.sidekickstrategies.com/hubspot-updates/custom-signals-hubspot-buyer-intent)). Note the caveat: HubSpot's own KB documents only fixed signal types you can pause, so treat the custom flow as third-party-reported ([HubSpot KB](https://knowledge.hubspot.com/reports/use-intent-signals)).
- **Avina** ships it with weights ([docs.avina.io](https://docs.avina.io/signals/custom-ai-signals)); **UserGems Research Agent** ships it at $40K/yr ([docs](https://help.usergems.com/article/research-agent)); **WhiteWhale** reportedly ships 35 of them at $200/mo.
- **Sprouts.ai** offers a "Create a custom Signal" path with AI-refined queries ([support.sprouts.ai](https://support.sprouts.ai/en/articles/12930273-ai-signals-workflow)).

**Conclusion:** "we let you write your own signal question in plain English" is a *feature table row in 2026*, not a differentiator. It is also the exact thing HubSpot shipped 12 weeks ago and is actively consolidating around.

### 2.2 Evidence citation is also table stakes.

HubSpot: *"Every match comes with cited sources."* WhiteWhale: *"verified source quote and clickable link."* Sprouts and Avina both add a preview-before-activate step. Citing a URL per signal is **not** a differentiator either.

### 2.3 What nobody ships — verified absences

These are the load-bearing findings of this file.

**(a) Sales-team-authored negative and disqualification rules. Nobody ships this as a product feature.**
Every review of every vendor's own docs found weights that are *visible* (Warmly), *automatic* (Warmly's ML, 6sense's 6QA), or *vendor-set* (Avina puts custom signals highest) — but **not one product lets the sales team write a disqualifier that overrides a positive score.** The pattern exists only as consulting frameworks and blog design patterns: Anti-ICP scoring with `score < -50 → suppress from sales routing` ([saber.app](https://www.saber.app/glossary/anti-icp-scoring)), negative-persona overrides that fire *before* the composite score and cap it to zero ([LeadMagic](https://leadmagic.io/gtm-skills/icp-scoring)), net-score models with `3+ negative signals = auto-disqualify` ([Prospectory](https://prospectory.ai/resources/negative-persona-disqualification-framework/)). Multiple independent searches converged on the same result: these are **design patterns people implement in Clay or Salesforce, not features anyone sells.**

**(b) Per-service / per-service-line signal scoping. Nobody does it.**
A search specifically for custom signals or disqualification broken out by service line returned "no information… the nearest equivalents are per-account, per-person, or per-seat." Every vendor models one global account score. Orange Systems sells *several different services* to the same company; a single global score is the wrong abstraction and no incumbent has noticed.

**(c) Vendor taxonomies cannot be customized inside a sales cycle.**
Bombora: nominate a topic, wait for quarterly vetting against *"relevance, scale, and sustained engagement"*, then a 4–6 month collection/QA cycle, no guarantee, and the result is shared with every other customer. Cognism caps you at **12** topics selected from the Bombora library. Common Room caps Bombora topics at 6/12/25 by tier. Landbase maintains its own 1,500-signal catalog. This is not an oversight — it is the business model. A taxonomy is only valuable to a data network if it is shared.

**(d) Nobody retracts a signal that stops being true.**
Confirmed for Avina directly (a lead whose situation changed, e.g. a hiring freeze, is **not** removed from a campaign). This is a structural property of alert-based tools: they are append-only. The cost is the false-positive problem in (e).

**(e) The pain is documented and large, and it is a *precision* problem.**
- Gartner via Foundry: **71% of B2B organizations collect buyer signals, but more than half never operationalize the data** ([UserGems](https://www.usergems.com/blog/9-things-to-know-sales-signal-tools-2026)).
- 6sense's own Kerry Cunningham, Sept 2025: the term *"intent data" is one of those terms that did more harm than good* — it mostly showed *"who was digitally loafing on our website."*
- Metadata's Sept 2025 report *Chasing Ghosts*: third-party intent *"was no longer worth chasing"*; over 30% of web traffic is bots (GPTBot alone 13%) ([metadata.io PDF](https://metadata.io/wp-content/uploads/2025/09/ChasingGhosts-MatadataONE.pdf)).
- A €35,000/year Bombora deployment: 400 accounts contacted monthly → 6–8 meetings, 2 qualified opportunities, 3.5% reply, ~€3,500 per opportunity. After switching to business signals: 14.2% reply, €520 per opportunity ([Rodz](https://www.rodz.io/fr/blog/migrer-intent-data-prospection-signaux/)).
- Topic surge correlates only **5–15%** with purchase behaviour, versus 15–30% for first-party signals; only **24%** of intent-data users report exceptional ROI ([Revnew](https://revnew.com/blog/real-intent-data-signals), chain-cited — verify before quoting).
- Demand Gen Report 2024: buyers receive **36+ vendor outreaches within two weeks** of showing intent — so even a *true* signal is now a commodity.

**(f) Configurability is where the incumbents' own marketing points.**
UserGems' own guidance: *"Transparent scoring beats black-box AI"*, buyers now ask *"whether the model is customer-specific or shared"*, and *"Execution is the differentiator, not detection."* Salesmotion tells buyers to demand that a vendor *"explain how a signal was generated, audited, and routed."* **The industry's own advice column describes the product we should build.**

**The gap in one sentence:** the market has solved *detection* and *plain-language authoring*; it has not solved **the sales team owning the scoring policy — per service, with disqualifiers that outrank positives, on evidence that can be falsified and retracted.** Every verified vendor gives you a score; none gives you the scoring policy.

---

## 3. What "intent data" means commercially, and why we cannot replicate it

### 3.1 The honest mechanics

Bombora is not a signal engine. It is a **data co-op**: member publisher sites carry a proprietary JavaScript tag that captures engagement with gated, paywalled and open content — text, form fills, downloads, registrations — and that activity is pooled and scored into company-level topic surge data. Bombora's own description: *"hundreds of publishers and brands, and thousands of B2B media sites,"* 1,765+ sites added in two years, **86% of the data shared exclusively with Bombora**, built *"publisher by publisher, brand by brand, member by member"* over **more than a decade** ([bombora.com/co-op](https://bombora.com/co-op/)). The taxonomy is machine-derived — NLP models per topic, content scored for *"relevance and density"* — and now runs to **21,600+ topics**, refreshed quarterly with roughly 1,000 net-new per cycle ([topics & taxonomy](https://bombora.com/core-concepts/the-complete-guide-to-bomboras-topics-and-taxonomy/), [intent-topics-we-track](https://bombora.com/intent-topics-we-track/)). The product is *"which topics is this company researching, and is that rising above its own baseline."* It is **company/account-level, never person-level** — that aggregation is what makes the consent model work.

G2 is second-party by contrast: **its own** review and comparison traffic across G2, Capterra, Software Advice and GetApp, which G2 itself frames as complementing *"existing GTM platforms, not replacing them"* and best for *"mid-to-late stages of the buying journey"* ([sell.g2.com/buyer-intent](https://sell.g2.com/buyer-intent)). TechTarget Priority Engine and TrustRadius Intent have the same structural limit — *"signal source is limited to TechTarget's owned media properties"*, *"coverage is limited to accounts actively researching on TrustRadius specifically"* ([HG Insights](http://hginsights.com/blog/buyer-intent-data-providers/)).

### 3.2 Why a 48-hour team cannot replicate it — the four honest reasons

1. **It is a network, not a dataset.** The asset is hundreds of publisher partners who each installed a tag and accepted one shared taxonomy. A crawler of public web pages is not a substitute for consented, first-party, session-level consumption data. You cannot scrape a co-op, and no amount of LLM cleverness changes that.
2. **Consent is a hard gate with a legal stack behind it.** Bombora is a registered IAB TCF vendor (Purposes 1, 7, 8, 10), sets third-party cookies, and requires publishers to obtain consent *before* the tag fires; company-level aggregation *"does not eliminate the GDPR consent requirement,"* and under CCPA the collection is a *"sale"* or *"sharing"* requiring Global Privacy Control to be honoured ([ConsentStack](https://www.consentstack.io/vendors/bombora)). That is a compliance programme, not a build step.
3. **It is a line item, not a hack.** Realistic entry ~$13K/yr, median **$25,000/yr**, and the top cost driver is how many topics you track. You cannot buy your way to it on a hackathon budget, and neither can Orange for a niche question the taxonomy rejects.
4. **Even if replicated, it would not answer Orange's question.** Bombora tells you a company is *researching* a concept, at account level, from a shared taxonomy. Orange's brief asks whether a company is *hiring RPA developers* or *mentioning process optimisation*, with a citation. Those are different products, and Bombora's own model makes niche questions structurally impossible for it.

### 3.3 The crisp answer to "how is this different from Bombora?"

> Bombora is a panel, not an answer engine. It sells you topic surges from a co-op of publisher sites, scored against a taxonomy someone else owns — 12,000 concepts you pick from, and if your question isn't in there, you file a ticket and wait four to six months, and when it's approved it's given to every other Bombora customer too. So it can never be your edge. We don't own a panel and we don't pretend to. We read the open web for *the exact question Orange asked* — "is this company hiring RPA developers in the EU?" — and we answer it with the sentence and the link that answered it. Bombora tells you a company is *interested in automation*. We tell you the COO wrote in the Q2 report that they're cutting 15% of back-office cost, and here is the paragraph.

Add the honesty line, because it buys credibility: **"We are not trying to be Bombora. We're the layer above it — if you already buy Bombora, we can score its topics as one weighted question among yours."**

---

## 4. Three candidate differentiators, ranked

Ranked by fit against the rubric weights that matter here: **Signal Relevance 25% · AI/ML Innovation 20% · Configurability 20%.**

### #1 — Service-scoped signal policy: per-service question sets with weights and disqualifiers that outrank positives

**What it is.** Orange defines, per service (Agentic Process Automation, Cybersecurity, …), its own question set, each question weighted, plus **hard negative rules that override the positive score** — competitor/staffing-agency, sub-threshold revenue, no international footprint, "already publicly named an incumbent vendor". Critically, **the same evidence is scored with different polarity per service**: an ad for RPA developers is a demand signal for a staffing engagement and a *capability-building* flag for the automation service; "we're building our own AI team" is a negative for one service and neutral for another.

**Why it is not covered.** §2.3(a) and (b). No verified product ships authorable disqualifiers; none scopes signals per service line. The consultancy literature describes exactly this model and every vendor implements it nowhere ([saber.app](https://www.saber.app/glossary/anti-icp-scoring), [Prospectory](https://prospectory.ai/resources/negative-persona-disqualification-framework/), [LeadMagic](https://leadmagic.io/gtm-skills/icp-scoring)).

**Why it demos.** Add a negative rule live ("exclude companies that are themselves IT services firms"), watch the board drop. Switch service tabs — same company, two different scores, two different reason lists. That is the 20% configurability criterion made visible in ten seconds, and it is also a *precision* story (25%): disqualifiers are the cheapest false-positive killer that exists.

**Why it defends in Q&A.** "Clay lets you do this" → Clay lets you *build* it in a table with a formula column; it is RevOps engineering work, no plain-language questions, no per-service scoping, and the sales team cannot own it. "Warmly shows weights" → Warmly *displays* weights its ML sets; you cannot author a disqualifier. "HubSpot Custom Signals" → no weighting, no negatives, one global score.

### #2 — Evidence ledger with falsification and a measured, published false-positive rate

**What it is.** Every answered question returns: the verbatim quote, the URL, the timestamp, model confidence, **and the counter-evidence the model considered** — plus a "what would change this answer" line. On top of that, the workspace publishes its own precision, measured against the sales team's accept/reject labels ("of 40 companies you reviewed, you accepted 31 — precision 0.78"), and **retracts** signals that stop being true.

**Why it is not covered.** Citation alone is table stakes (§2.2) — so do **not** sell "we cite sources". What is missing is *falsification* (nobody shows contrary evidence; the nearest formal model is a neutrosophic audit separating favourable / indeterminate / contrary evidence, [paper](https://fs.unm.edu/nss8/index.php/111/article/download/7751/3657/22836)), *retraction* (Avina explicitly does not do it), and *a published precision number* (Salesmotion tells buyers to demand it: ask *"how many false positives it creates"* — [salesmotion](https://salesmotion.io/blog/how-to-choose-sales-intelligence-tools)).

**Why it demos.** Open a flagged company, expand **"why, and why not"**, see the contradicting snippet. Then show the precision panel and one retracted alert. It is the only differentiator that *directly attacks the 25% criterion by name* — "low false-positive rate" is literally in the rubric.

**Why it defends in Q&A.** The kill question is "everyone cites sources now." The answer is: citing the source that *supports* a claim is cheap; showing the evidence *against* it, retracting when it expires, and publishing your own error rate is not something the category does, because the category sells coverage and coverage is measured by what it catches.

### #3 — Calibration loop: the team's accept/reject labels retune the question weights, with the arithmetic shown

**What it is.** Each accept/reject/reason-code from a rep becomes a labelled example. The weights on the service's questions are re-fit (logistic regression / regularised ranking over question-hit features, with a prior so 15 labels do not produce nonsense), and the UI shows the derivation back: *"this question moved 0.4 → 0.7; 9 of the 11 leads you accepted had it."* Transparent, reversible, and owned by the sales team.

**Why it is not covered.** Everyone describes the loop; nobody ships it. The 2026 literature is unanimous that models degrade in 8–12 weeks without retraining and that teams mostly "train once and never retrain" ([Neural Base](https://theneuralbase.com/ai-for-marketing/learn/beginner/lead-scoring/), [Cufinder](https://cufinder.io/blog/machine-learning-a-better-future-for-crm/)); UserGems states a customer-specific model beats an industry benchmark. But calibration is delivered as a **monthly meeting** ([Coffee.ai](https://www.coffee.ai/articles/sales-feedback-deal-scoring-2026)), not as a product surface.

**Why it demos.** Show three rejects, then show the weight move and the resulting re-rank. Rubric hits: AI/ML Innovation 20% and Configurability 20%.

**Ranked third, deliberately.** With ~40 labels in 48 hours the statistics are thin, and an honest jury knows it. Ship it as *calibration with shrinkage, displayed honestly* — never as "our model learns". If it overclaims, it damages #1 and #2, which are stronger.

---

## 5. Sounds novel but isn't — kill these before a juror does

| Claim to avoid | Why it fails |
| --- | --- |
| **"We use AI to score leads."** | 6sense has shipped a predictive scoring model for a decade; Clay, Apollo, ZoomInfo, Keyplay all score. It is a category entry requirement, worth zero points |
| **"We generate personalised outreach emails."** | Clay, Apollo, Artisan, every AI SDR on the market. The brief marks it *optional*, it carries **no rubric weight**, and it answers the wrong pain — the jury's stated problem is *finding* the companies, not writing to them. Mention it as an export, never as a headline |
| **"Configurable signal questions in plain language."** | **Shipped.** Clay Custom Signals (May 2025), HubSpot Custom Signals (30 Jun 2026, and HubSpot just absorbed Trigify's team on 23 Sep 2026), Avina, UserGems Research Agent, WhiteWhale. A juror who knows this market will flatten it in one sentence. It is the *floor* of the product, not the pitch |
| **"We cite the source for every signal."** | HubSpot, WhiteWhale, Avina all do. Table stakes (§2.2) |
| **"We monitor public sources with LLMs."** | The brief specifies it as the expected stack. Describing your brief back to the jury is not a differentiator |
| **"Two-tier fast classifier + generative LLM to cut cost."** | This is an engineering optimisation, not a customer outcome. It also depends on the "Jev" product in the hypothesis — see risk **R1** in `00_progress.md`. Cost per row is a Clay-credit problem; the jury does not have it |
| **"Zero-shot dynamic taxonomy."** | Zero-shot classification against arbitrary labels has been the default behaviour of LLMs since 2023. Claiming it as AI innovation is a self-own in front of an AI-heavy panel |
| **"We're like Bombora but AI-powered."** | Bombora's moat is a consented publisher co-op, not a topic list. Claiming to replace it invites the one question you cannot answer (§3.2). Say clearly that you are *above* the intent layer, not a competitor to it |
| **"We cover more sources than anyone."** | Coverage is the incumbents' home turf and you will lose. Claim **precision and configurability**, never coverage. Your honest line: *"we will be worse than Clay at scale, and better at answering Orange's specific question with a policy Orange owns"* |
| **"Configurable scoring" (unqualified).** | Keyplay already offers whitebox ICP scoring with positive/negative signals weighted 1x–20x ([docs.keyplay.io](https://docs.keyplay.io/en/articles/8712837-build-your-icp-scoring-model)) — but for SaaS ICP fit, quote-based, no monitoring of public-source questions. The differentiator is not "configurable scoring"; it is **sales-team-authored disqualifiers, per service, on monitored public evidence.** Precision in the claim is the whole game |

---

## 6. Load-bearing facts for the pitch deck

Use these, with the URL, on a "why now" slide:

1. **71%** of B2B orgs collect buyer signals; **more than half never operationalize them** — Gartner via Foundry ([UserGems](https://www.usergems.com/blog/9-things-to-know-sales-signal-tools-2026)).
2. **6sense's own voice** says "intent data" did more harm than good.
3. A **€35K/year Bombora** deployment produced ~€3,500 per opportunity; a business-signal approach produced €520 ([Rodz](https://www.rodz.io/fr/blog/migrer-intent-data-prospection-signaux/)).
4. **21,600+ topics**, refreshed quarterly, and adding one is a nomination vetted on *"relevance, scale, and sustained engagement"* — a niche question gets rejected, and an approved one is handed to every other Bombora customer ([Bombora](https://bombora.com/intent-topics-we-track/)).
5. **Cognism caps intent at 12 topics**, Common Room at 6/12/25, Apollo locks your topic choices for 24h — the taxonomy is rented, not owned.
6. **Intent is mis-mapped by construction.** 6sense is *"account-level, not contact-level,"* *"produces false positives,"* and *"mistakes early research for buying readiness"*; G2 resolves to the organisation with *"no person"* ([UserGems](https://www.usergems.com/blog/is-6sense-intent-data-accurate-enough-to-trust), [ContactLevel](https://www.contactlevel.com/resources/g2-intent-data)).
6. The category's consolidation (Zoom → Common Room, Apollo → Pocus, HubSpot → Trigify, all 2026) proves the *signal* layer is becoming a feature of CRMs — which is exactly why the **configurable policy layer** is the defensible place to sit.
