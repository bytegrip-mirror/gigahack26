# Live Signal Experiment — measured, not assumed

Run 2026-09-25 on this machine against the open internet. Every number below is measured output, not an estimate. This is the evidence base for the "Signal Relevance & Accuracy" claim, which is 25% of the score — the single heaviest criterion.

## What was tested

Three applicant-tracking-system APIs, because a job posting is a dated, structured, self-published buying signal that no competitor can dispute:

| API | Endpoint | Result |
| --- | --- | --- |
| Greenhouse | `https://boards-api.greenhouse.io/v1/boards/<token>/jobs?content=true` | **200, no auth**, full descriptions |
| Lever | `https://api.lever.co/v0/postings/<company>?mode=json` | **200, no auth** (404 means wrong token, not blocked) |
| Ashby | `https://api.ashbyhq.com/posting-api/job-board/<company>` | **200, no auth**, 13.8MB for one board |

Verified working board tokens during the probe: `gitlab`, `canonical`, `datadog`, `cloudflare`, `stripe`, `monzo`, `gocardless`, `n26`, `affirm`. Tokens that 404'd: `checkoutcom`, `starlingbank`, `transferwise`, `netflix` (Lever). A missing token is a wrong slug, not a block — the board slug is discoverable from the company's own careers page.

**No API key, no registration, no cost.** This is the cheapest defensible data path available to the team.

## The precision finding — the important result

Scanning **12 boards, 2,646 postings** with four signal regexes, splitting where the keyword appears:

| Where the keyword matched | Count |
| --- | --- |
| In the job **title** | **22** |
| Only in the job **body** | **418** |

**A 19:1 noise ratio against description scanning.** The body-level matches are dominated by boilerplate — a "Business Development Representative" and a "Customer Experience Manager" posting both matched "process automation" because every careers page contains generic corporate language about processes and efficiency. A platform that scores on body keywords will show a sales team roughly 95% garbage, and a sales team that gets 95% garbage stops opening the tool. This is the failure mode the brief warns about when it says *low false-positive rate*, and it is the exact thing a jury of sales professionals will probe.

**This single measurement justifies the entire classifier layer of the architecture.** Keyword scanning on descriptions is not a strawman — it is what a naive build does, and it fails by a factor of nineteen.

## The second finding — even titles are not enough

The 22 title-level hits are hand-checkable, and they split cleanly:

**High value — KYC, AML and financial-crime roles at regulated fintechs (9 hits):**
- Stripe — `KYB/KYC Operations Associate`, `Operations Associate, Financial Crimes (AML Investigations)`, `Risk Operations Analyst, Financial Crimes`
- Monzo — `Senior Financial Crime Investigator`, `Data Science Manager, Financial Crime`
- GoCardless — `KYC Specialist`
- N26 — `KYC Specialist – French Market`, `KYC Team Lead - EDD/ODD`

**Low value — sales, revenue and legal operations roles (10 hits):** `Sales Operations Analyst`, `Revenue Operations Analyst II`, `GTM Strategy & Operations Analyst`, `Legal Operations Analyst`, `Monetization Operations Analyst`.

The low-value group matched because "Operations Analyst" contains the word *operations*. These companies are not buying process automation services; they are hiring analysts. If these were scored as leads the demo would be embarrassing in front of an audience that includes people who sell this for a living.

**The actionable conclusion: precision comes from role-family specificity, not keyword breadth.** "Operations Analyst" is noise; "KYC Specialist" / "Financial Crime Investigator" / "AML Investigations" is signal. The signal taxonomy must be built from *role families that only exist when a specific pain exists*, not from words that appear in many job titles.

## Why this matters disproportionately for this client

Nine of the twenty-two title-level hits — and effectively all of the unambiguous ones — are **financial-crime and KYC operations roles at fintechs**. That is precisely the pain Orange Systems' flagship product (InSight KYC) sells against: manual onboarding and compliance operations, cut from 8 hours to under 1 hour. The strongest detectable signal on the open web maps directly onto the thing the challenge provider is best known for selling.

That alignment should be the spine of the product, not a footnote.

## Reproducible evidence card

Every hit carries a live, dated, third-party URL a juror can open on their own phone:

```
company : n26
signal  : kyc_aml
posting : KYC Team Lead - EDD/ODD
dated   : 2026-09-18
source  : https://boards-api.greenhouse.io/v1/boards/n26/jobs
```

The full probe is ~40 lines of Python using only `urllib` and `re`, and it ran end-to-end in seconds. The build does not need a crawler farm; it needs a slug resolver and a classifier.

## What this changes in the plan

1. **Two-stage detection is not an optimisation, it is a correctness requirement.** Wide recall from cheap scanning, then a classifier that reads the whole posting and decides whether the role *is* the pain, with a confidence score. Measured evidence for why: 19:1.
2. **Score on role families with negative examples.** The classifier needs to know that "Sales Operations Analyst" is explicitly NOT the signal — a few negative examples per family is what stops the 10-hit noise class.
3. **The demo's undeniable moment has real ammunition.** Live, dated postings from named companies with openable URLs exist right now; nothing needs to be staged.
4. **Slug resolution is the boring critical path.** Finding a company's board token from its careers page is the step that turns a company name into evidence, and it is worth more engineering time than any model choice.

## Experiment 2 — slug resolution fails on plain HTTP (the pipeline's real bottleneck)

The plan assumed the easy path: fetch a company's careers page, regex out the ATS token, call the API. Tested against eight plausible target companies (fintechs and SaaS vendors matching Orange Systems' ICP):

| Outcome | Count | Detail |
| --- | --- | --- |
| Token resolved from the careers page | **0 / 8** | — |
| HTTP 403 | 2 | revolut.com, personio.com — bot-blocked outright |
| HTML returned, no token found | 6 | wise, mollie, bunq, checkout.com, klarna, backbase |

**Careers pages are JavaScript-rendered SPAs.** The ATS embed is injected client-side, so the token is not in the HTML that `urllib` receives — the page is 190–618KB of framework code with the board loaded after hydration. Two of the eight blocked a plain user-agent outright.

This is why the brief lists **Playwright** first among the crawling tools. It is not a preference, it is a requirement.

**Consequences for the build, in priority order:**

1. **Do not sink time into a general slug resolver.** It is a rabbit hole with a low ceiling: for every company solved, the next one uses Workday or SuccessFactors and needs a different resolver.
2. **Seed a curated token list for the demo** — ten to twenty companies whose tokens are already known to work (the probe above confirmed nine). This converts a fragile discovery step into a reliable one, and the demo's credibility rests on ten correct signals, not on universal coverage.
3. ~~**Add a search-based fallback** (`site:jobs.lever.co "<company>"`)~~ — **withdrawn, see the correction below.**
4. **State coverage honestly in the pitch.** "Works for companies on Greenhouse, Lever and Ashby — roughly X% of the mid-market — with a roadmap for Workday" is a stronger answer to a jury than an implied universal crawler that visibly fails on the first blocked site.

A jury that names a company on stage and watches the resolver fail is the worst possible demo moment. A curated list makes that scenario impossible; a resolver makes it likely.

### Correction (17:20) — the search fallback does not exist

This file originally recommended falling back to a search engine (`site:jobs.lever.co "<company>"`). **That recommendation is withdrawn**: tested directly from this machine, the free programmatic search layer is gone.

| Engine | Result |
| --- | --- |
| DuckDuckGo (html and lite endpoints) | HTTP 202 with a challenge page — not real results |
| Startpage | HTTP 303 redirect — blocked |
| Bing | HTML page returns 200, but the API was retired in Aug 2025; this is scraping, not a documented interface |
| Google News RSS | **works** — HTTP 200 with real `pubDate` values (verified: items dated 21 and 24 Sept 2026) |
| GDELT DOC 2.0 | HTTP 429 on first attempt: *"Please limit requests to one every 5 seconds"* |

**Consequence for the build:** the pipeline must be built on *direct* sources with documented interfaces — ATS job APIs, company registries, Google News RSS, sitemaps, GDELT with aggressive throttling — and must not assume it can search the open web at runtime. Any component described as "the agent looks it up" is unbuildable as stated. The curated token list is now the *only* reliable route to a company's job board, which raises its importance from convenience to critical path.

## Caveats, stated honestly

- Greenhouse worked for 9 of 12 attempted tokens; the failures were guessed slugs. Real coverage depends on the target company using one of these ATSs — large enterprises often do not, and use Workday or SuccessFactors, which are harder.
- The probe used regexes, not a classifier. The 19:1 figure measures the *problem*, not the solution's accuracy; the classifier's own precision must be measured separately on a hand-labelled sample, which `13_scoring_design.md` covers.
- Posting dates are the ATS's `updated_at`, which reflects an edit, not necessarily first publication. The review cadence for "is this still fresh" should be confirmed against a second source where the distinction matters.
