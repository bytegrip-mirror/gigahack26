# 11 — Data Sources: What a 48-Hour Team Can Actually Obtain

**Question this answers:** for each source named in the brief, what is reachable today
(2026-09-25) with **no budget and no paid API key**? Everything below was tested live unless
explicitly marked *not tested*.

**Verification method:** live HTTP requests against the real endpoints, plus the vendors' own
documentation. Status codes and response fragments quoted below are real. Two findings in
this document (the Moldova column set and GDELT's throttle behaviour) **contradict the first
reports from the research pass and were corrected by re-testing at the data level** — they
are flagged inline.

---

## Headline result

The strongest source category in this brief is **public ATS job-board APIs**, which the brief
treats as an afterthought. Greenhouse, Lever, Ashby, SmartRecruiters and Recruitee all serve
**unauthenticated JSON with a real published date per posting**, stable schema, no key, no
registration. A job posting is a dated, structured, verifiable buying signal — the opposite of
a news article, which needs interpretation before it becomes evidence.

The second strongest is the **Moldovan open company register**: free, weekly, 302,954 companies,
with **director names, ownership percentages, activity codes and liquidation dates**. For a
hackathon in Chișinău whose client is Orange Systems (Moldova), this replaces a large part of
what the brief hoped Crunchbase would provide.

The weakest premises in the brief are **Crunchbase, LinkedIn, and the search/news stack**.
Crunchbase has no free API. The entire free web-search layer — DuckDuckGo, Bing, Google CSE,
SearXNG — is blocked, retired or closed. **GDELT is throttled to roughly a one-in-three success
rate.** The plan must be built around that, not on top of it.

---

## 1. Job-posting signals — public ATS APIs

| Source | Free? | Auth | Limits | Example call (paste today) | Signal value | Risk |
|---|---|---|---|---|---|---|
| **Greenhouse** | Yes | **None** — docs: *"Job Board data is publicly available, so authentication is not required for any GET endpoints"* | None documented. **25 rapid sequential requests → 25× `200`, zero `429`** (empirically tested) | `curl "https://boards-api.greenhouse.io/v1/boards/anthropic/jobs"` | **High.** `first_published` + `updated_at`, ISO-8601. `?content=true` adds full HTML description for keyword/LLM classification | Board-token discovery is the bottleneck (§1.3). Large boards reach 1–15 MB with `content=true` |
| **Lever** | Yes | **None** for reads | None documented for reads. `429` documented only for *application POSTs* (>2/s). 25 rapid GETs → all `200` | `curl "https://api.lever.co/v0/postings/palantir?mode=json"` | **High.** `createdAt` (epoch ms) present live, plus `categories.{team,location,commitment}`, `workplaceType`, `country` | `createdAt` is **absent from the documented field table** but returned in practice — don't hard-depend without a fallback. EU tenants live on `api.eu.lever.co` |
| **Ashby** | Yes | **None** — docs show a bare `curl`, no headers | None documented. 25 rapid GETs → all `200` | `curl "https://api.ashbyhq.com/posting-api/job-board/ramp?includeCompensation=true"` | **High.** `publishedAt` (ISO), `department`, `team`, `address`, `isRemote`, `workplaceType`, compensation ranges | `publishedAt` is *last* published, not first created. Filter `isListed:false` |
| **SmartRecruiters** | Yes | **None** | Not documented. 25 rapid GETs → all `200` | `curl "https://api.smartrecruiters.com/v1/companies/BoschGroup/postings?limit=100"` | **High.** `releasedDate` with full timestamp, plus `industry`, `function`, `experienceLevel`, `location` with lat/long | **Token is case-sensitive** — `bosch` → 0 results, `BoschGroup` → 4,797. Paginated (`offset`/`limit`) |
| **Recruitee** | Yes | **None** | Not documented | `curl "https://channable.recruitee.com/api/offers/"` | **Medium.** `published_at`/`created_at`, `department`, `city`, `employment_type_code`, `salary` | Smaller vendor, fewer enterprise prospects |
| **Workday** | Yes | **None** | Not documented | `curl -X POST "https://cisco.wd5.myworkdayjobs.com/wday/cxs/cisco/Cisco_Careers/jobs" -H 'Content-Type: application/json' -d '{"appliedFacets":{},"limit":20,"offset":0,"searchText":"automation"}'` | **High for enterprise.** Cisco 476 hits, Salesforce 352. Returns `title`, `externalPath`, `locationsText`, `postedOn` | **Date is prose** (`"Posted 2 Days Ago"`), not ISO. Method is **POST**. Site token must be discovered (`nvidia` → `S404`; `Cisco_Careers` worked) |
| **Workable** | **Effectively no** | None, but pointless | — | `curl "https://apply.workable.com/api/v1/widget/accounts/tink?details=true"` | **None.** Returns account metadata with `"jobs":[]` for **every one of 11 boards tested** (Toggl, Hotjar, Pipedrive, Bolt, Revolut, iFood, Trustpilot, Personio, Tink, Cabify, TransferWise) | **Trap.** Responds `200` and looks healthy while returning zero jobs |
| **Personio** | Partial | None | — | `curl "https://personio.jobs.personio.de/xml"` | Low–medium. XML `<workzag-jobs><position>` with `<id>`, `<subcompany>` | Most `*.jobs.personio.de` hosts now **307-redirect to personio.com** (verified: sennder, flix, auxmoney, wefox). Assume dead per-company unless tested |

### 1.1 Documentation URLs

- Greenhouse Job Board API — https://docs.greenhouse.io/job-board.html *(live: `developers.greenhouse.io` 301s here)*
- Lever postings API — https://github.com/lever/postings-api
- Ashby public job posting API — https://developers.ashbyhq.com/docs/public-job-posting-api
- SmartRecruiters posting API — https://developers.smartrecruiters.com/docs/posting-api
- Recruitee — https://docs.recruitee.com/
- Workday CXS — undocumented/private; discoverable per tenant

### 1.2 Live proof — this is demo material

Pulled live on 2026-09-25:

```
GREENHOUSE anthropic — newest 6 of 624 postings
  2026-09-25 | Product Designer, Safeguards        | San Francisco, CA
  2026-09-25 | Finance & Strategy, Deal Strategy   | London, UK
  2026-09-25 | People Legal Counsel, APAC          | Sydney, Australia
  2026-09-24 | Partner Manager, Microsoft Co-sell  | San Francisco, CA

ASHBY ramp — newest of 155 postings
  2026-09-24 | Software Engineering Intern, iOS    | New York, NY
  2026-09-23 | Machine Learning Engineer            | New York, NY

SMARTRECRUITERS BoschGroup — 4,797 postings
  releasedDate 2026-09-25T16:49:15.834Z
```

**Postings dated today, from a keyless endpoint, in a stable schema.** A demo showing a job
posted *hours ago* is a genuinely live signal and a judge can verify it in one click.

### 1.3 Keyword detection works — but precision is the real problem

A naive regex (`rpa|process autom|automation engineer|process mining|digital transformation|
ai engineer|…`) over title **plus full description** on live boards:

| Board | Jobs scanned | Keyword "hits" |
|---|---|---|
| greenhouse/anthropic | 624 | 18 |
| greenhouse/stripe | 698 | 28 |

Inspection shows **many are false positives** — `Account Executive - Public Sector (ASEAN)`
and `Accounting, Revenue Internal Controls` matched only because the body text contains
automation-adjacent words. This is exactly the failure mode the rubric punishes hardest
(**Signal Relevance & Accuracy = 25%**, *"low false-positive rate"*).

**Implication:** match on **title first**, treat description matches as weaker evidence, and
put a classifier between the raw match and the score. A raw keyword grep over descriptions
will not survive a judge's scrutiny.

### 1.4 The real bottleneck: finding a company's board token

The brief does not acknowledge this, and it is the hardest part of the category. Tested:

| Discovery method | Result |
|---|---|
| **Slug guess + verify** (company short name vs. all three APIs at once) | **9/20 = 45% hit rate.** Hits: `adyen` (211 GH), `n26` (67 GH), `monzo` (71 GH), `wise` (16 GH), `gocardless` (28 GH), `celonis` (250 GH), `uipath` (99 Ashby), `camunda` (39 Ashby), `spotify` (79 Lever) |
| **Cheap existence check** — `https://boards.greenhouse.io/embed/job_board/js?for=<token>` | **Works.** `200` for a real token, `404` for a fake one, without downloading the board. Use as the validator in a guess-and-check loop |
| **Redirect guessing** (`careers.<domain>`, `jobs.<domain>`, `<domain>/careers`) | **0/33 found an ATS.** Not viable |
| **Grep careers-page HTML for ATS domains** | **Failed on every site tried** (orange.md, sap.com, endava.com, orange.com). Careers pages are JS-rendered and the guessable URLs 404 |
| **Search-engine `site:` query** | **Unreliable — see §6** |

**Practical answer:** do not attempt automatic discovery. Have the sales user name target
accounts and resolve each token **once, by hand, at setup**, using the 45% slug guess plus a
browser. A 48-hour demo needs ~10–30 curated accounts, not 10,000 discovered ones. Store them
in a seed table `(name, ats_provider, board_token)`. This is honest, reliable and demonstrable.

Worth noting: **UiPath** (RPA) and **Celonis** (process mining) both have fully open boards —
the vendors of the exact category Orange Systems sells around are themselves reachable.

---

## 2. News

The honest summary: **one source works well (Google News RSS), one works badly (GDELT), the
rest are retired, closed or blocked.**

| Source | Free? | Auth | Limits | Example call | Signal value | Risk |
|---|---|---|---|---|---|---|
| **Google News RSS** | **Yes** | **None** — no key, no UA needed | ~100 items per feed | `curl "https://news.google.com/rss/search?q=%22digital+transformation%22+when:7d&hl=en-US&gl=US&ceid=US:en"` | **High — the best free news source here.** `pubDate` to the minute, `<source url>` per item, `when:` operator works (`when:1d`/`7d`/`30d`) | **The `<link>` does not resolve with curl.** The 302 points back at itself plus `&hl=…`; the old base64 decode trick now yields encrypted bytes. A real browser resolves it — **Playwright reached the publisher URL** (`breakingdefense.com/2026/09/...`). So: headless browser for URLs, or match on `<source url>` + title |
| **GDELT DOC 2.0** | Yes | **None** | **Severely throttled.** Error text says *"limit requests to one every 5 seconds"*; in practice **2 of 3 attempts returned `429`** even with 7-second gaps, and successful calls took **11–14 s** | `curl "https://api.gdeltproject.org/api/v2/doc/doc?query=%22digital+transformation%22&mode=ArtList&maxrecords=5&format=json&timespan=7d"` | Medium — global press, `seendate`, `sourcelang`/`sourcecountry` filters, 15-minute resolution | **Unreliable for a live demo.** `ArtList` succeeded on retry but never on the first call. Results are globally noisy — a `digital transformation` query returned a Macedonian article. Use as **slow background enrichment with retry**, never in the demo's critical path |
| **NewsAPI** | Yes (Developer) | API key | **100 req/day, 24-hour article delay**, search ≤1 month, dev/staging only | `curl "https://newsapi.org/v2/everything?q=...&apiKey=..."` | Low | **Two disqualifiers:** 24 h staleness and a licence that forbids production use. Not a live-demo source |
| **RSSHub** (`rsshub.app`) | **No** | — | — | `curl "https://rsshub.app/github/issue/DIYgod/RSSHub"` → `403` | **None as a hosted service** | **Trap — the brief names it.** Verbatim: *"Due to cost considerations, we will gradually restrict access to rsshub.app for some feed readers… rsshub.app is intended for testing purposes only and should not be used as a production RSS source."* Self-hosting is the only route, and that is hours of the 48 |
| **Press-release wires — PR Newswire** | Yes | None | 20 items per feed snapshot | `curl -sL "https://www.prnewswire.com/rss/news-releases-list.rss"` | Low–medium. Real `pubDate` from today | A firehose, multilingual, **not searchable by company** — you filter client-side. A keyword pass over one snapshot gave 2 "hits", both false positives |
| **Business Wire / TechCrunch / EU-Startups RSS** | Yes | None | — | `curl -sL "https://techcrunch.com/feed/"` | Low–medium | Business Wire's feed returned only ~1 KB. Accesswire and EINPresswire RSS → `404` (moved) |
| **Regional / target-market RSS** | Yes | None | — | `curl -sL "https://unimedia.info/rss/news.xml"` | Medium **for Moldovan coverage** | Mixed results: `unimedia.info` (109 KB), `newsmaker.md`, `economica.md`, `tv8.md` → `200`; `agora.md/rss`, `mold-street.com/rss` → `404`. **Validate the body starts with `<rss`** or you will ingest HTML |

### 2.1 Which news source can carry a live demo

| Source | Fresh + dated? | Judge-verifiable? |
|---|---|---|
| **Google News RSS** | **Yes** — `pubDate` to the minute | Yes, **but only after browser-based URL resolution** |
| GDELT | Theoretically (15-min `seendate`) — throttled to ~1/3 success | Not in practice |
| NewsAPI free | Dated but 24 h stale | Licence forbids it |
| PR wires / regional RSS | Yes | Yes |

---

## 3. Company websites, newsrooms, annual reports

| Source | Free? | Auth | Limits | Example call | Signal value | Risk |
|---|---|---|---|---|---|---|
| **`robots.txt` → `Sitemap:`** | Yes | None | Politeness | `curl -sL https://www.endava.com/robots.txt` | **Discovery.** Every site tested published its sitemap here | Some `robots.txt` disallow the paths you want |
| **`sitemap.xml` with `lastmod`** | Yes | None | 1.3 MB for endava | `curl -sL https://www.endava.com/sitemap.xml` | **Medium-high, genuinely dated.** `lastmod` observed: `2026-09-22` (endava), `2026-09-08` (orange.md) | Tells you *that* a page changed and *when*, not *what*. Needs a stored snapshot and a diff |
| **Sitemap index → sub-sitemaps** | Yes | None | — | `curl -sL https://www.orange.md/sitemap.xml` | Reveals section structure | Nested one level; recursive fetch |
| **Newsroom RSS (guessed paths)** | Mixed | None | — | `/news/rss`, `/feed`, `/rss`, `/blog/rss.xml`, `/en/newsroom/rss` | **Low.** Most guesses failed | **Trap — soft 404s.** `orange.com/en/newsroom/rss` returned `200` with `<html` content: the SPA served its shell for a nonexistent URL. **Status-code-only checks will silently poison a pipeline.** Always assert the body starts with `<rss`/`<feed` |
| **Annual reports / investor relations** | Yes but painful | None | — | e.g. `https://investors.orange.com/` | Low for a 48 h build | Probes failed (`investor.sap.com`, `investors.orange.com` → `000` connection failure; `endava.com/en/Investors` → `404` on the guessed path). Content is PDFs on JS-heavy portals. **Deprioritise** |
| **WAF / bot protection** | — | — | — | `curl -sL https://www.sap.com/sitemap.xml` | — | **Real.** SAP → `403 Access Denied`. Bosch, orange.md, endava served fine. Blocking is *patchy*, which is worse — it fails per-company at runtime |
| **Tech-stack signals from own HTML** | Yes | None | — | `curl -sL https://www.endava.com/ \| grep -iE "salesforce\|hubspot\|servicenow"` | **Low-medium.** Endava's raw HTML revealed Salesforce + ServiceNow + HubSpot | Low recall — most sites load obfuscated CDN bundles. Bonus signal only |

**Practical crawling approach:** `robots.txt` → sitemap index → sub-sitemaps → filter to
newsroom/press/blog paths → fetch each → store `(url, fetched_at, sha256, extracted_text)`.
**The diff between snapshots is the signal, not the page.** That means your first crawl
produces zero signals — you need two crawls separated in time. Plan for that in the demo
script, or seed the first snapshot before the demo starts.

---

## 4. Company registries and filings

| Source | Free? | Auth | Limits | Example call | Signal value | Risk |
|---|---|---|---|---|---|---|
| **Moldova — `dataset.gov.md` company register** | **Yes** | **None** | None observed; 38 MB XLSX | see §4.1 | **Very high — the single best asset found in this research** | "License Not Specified". No employee count, no revenue |
| **UK Companies House** | Yes | **HTTP Basic**, API key as username, password blank. Self-service, generated immediately | **600 req / 5 min**, then `429` | `curl -sS -u "$CH_API_KEY:" "https://api.company-information.service.gov.uk/search/companies?q=orange"` | **High.** `/company/{number}/officers` carries `officer_role`, `appointed_on`, `resigned_on` — a *dated leadership-change signal* | Key required, so **the endpoint body was not live-verified** — only the `401` proves it exists. Its docs URLs 404'd |
| **SEC EDGAR full-text search** | Yes | **None**, but a **contact User-Agent is mandatory** | 10 req/sec (documented) | `curl -sS -A "Team name email@example.com" "https://efts.sec.gov/LATEST/search-index?q=%22digital+transformation%22&forms=8-K&startdt=2026-09-01&enddt=2026-09-25"` | **High for US targets.** Full-text across filings with `file_date`, `form`, `display_names`, `biz_locations` | **US-listed issuers only.** Without a UA you get `403`, not a helpful error |
| **GLEIF LEI** | **Yes** | **None** | Not documented | `curl -g "https://api.gleif.org/api/v1/lei-records?filter[entity.legalName]=Orange&page[size]=2"` | Medium — entity resolution, corporate-group hierarchy | No financials, no headcount. Use `curl -g` — brackets are shell globs |
| **EU VIES (VAT)** | Yes | None | Not documented | `curl "https://ec.europa.eu/taxation_customs/vies/rest-api/ms/IE/vat/6388047V"` | Low — a **validator**, not a discovery source | Field exposure varies per country; some members mask name/address |
| **Wikidata SPARQL** | Yes | None | Fair-use | `https://query.wikidata.org/sparql` | Low. Google/Siemens employee counts resolve, but coverage is sparse and duplicates appear | Do not treat as a register |
| **EU BRIS** | **No** | — | — | — | **None.** The Commission states BRIS provides *"no batch/API data retrieval functionality"* | Browsable per-company only; documents fee-based per member state. **Strike it from the plan** |
| **OpenCorporates** | Key-gated | API token | Reported ~50/day, 200/month, open-data use only; commercial from £2,250/yr | anonymous calls → `401 Invalid Api Token` | Low | Free-tier numbers come from third-party write-ups, not their own 2026 pricing page (which would not load). **Treat as unusable** |
| **Overpass / OSM** | Yes | None | — | — | **None.** `office=*` coverage of Moldova is near-empty; one public instance returned `504` then `200` | Not a firmographics source |

### 4.1 The Moldova open register — verified at the data level

The real portal is **`dataset.gov.md`**, a working CKAN instance (`data.gov.md` → `404`;
`date.gov.md` is a landing page whose `/api` is also `404`; `rsud.gov.md` does not resolve).
Published by the **Agenția Servicii Publice**, refreshed **weekly** — `metadata_modified` was
`2026-09-21`, and the portal keeps 404 historical resources, i.e. a free weekly time series.

```bash
curl -sSL "https://dataset.gov.md/api/3/action/package_show?id=11736-date-din-registrul-de-stat-al-unitatilor-de-drept-privind-intreprinderile-inregistrate-in-repu" \
 | python3 -c "import json,sys;d=json.load(sys.stdin)['result'];print(d['resources'][-1]['url'])"
# -> https://dataset.gov.md/dataset/.../download/company_2026.09.21.xlsx   (38 MB, downloaded OK)
```

**Correction to the first research pass:** the file contains **302,954 data rows** (not
211,836), and there is **no `Statutul` column** — the last column is **`Data lichidării`**
(a liquidation *date*), which is a stronger, dated negative signal. The full, verified column
set (sheet 1) is:

| Column | Real sample value |
|---|---|
| `IDNO/ Cod fiscal` | `1026023146461` |
| `Data înregistrării` | Excel serial `46283` (= 2026-09-14) |
| `Denumirea completă` | `Societatea cu Răspundere Limitată WINDRI` |
| `Forma org./jurid.` | `Societate cu răspundere limitată` |
| `Adresa` | `MD-2045, CHIŞINĂU, RÎŞCANI CHIŞINĂU, str…` |
| `Codul unităţii administrativ-teritoriale (CUATM)` | `150` |
| **`Lista conducătorilor (cu indicarea rolurilor)`** | `PUȘCA VLADIMIR [Administrator]` |
| **`Lista fondatorilor (cu indicarea cotei părţi în capitalul social %)`** | `LISNIC MAXIM (60,00%), LISNIC ALA (35,00%), CURAȘ DANIEL (5,00%)` |
| `Genuri de activitate nelicentiate` (CAEM) | `6201, 6202, 6209` |
| `Genuri de activitate licentiate` | (usually empty) |
| **`Data lichidării`** | empty for live companies; a date for the rest |

Sheet 2 (2,129 rows) is the **CAEM activity-code dictionary** — free industry mapping. Sheet 3
(111 rows) is the licensed-activity dictionary.

**Concrete counts, computed from the file during this research:**

| Measure | Value |
|---|---|
| Total companies | **302,954** |
| With a `Data lichidării` value (i.e. **disqualified**) | **133,506 — 44% of the register** |
| With IT/software CAEM codes (6201/6202/6203/6209) | **11,565** |
| …of which **not** liquidated (live ICP candidates) | **10,159** |

**Why this matters for this brief specifically:** the client is Orange Systems, the venue is
Tekwill in Chișinău, and the ICP is defined *by market and geography*. This is a free, current,
machine-readable national register with **named directors, ownership percentages, activity
codes and liquidation dates** — the firmographics + leadership layer the brief asks Crunchbase
and LinkedIn to supply, for the home market, at zero cost and with no registration. It also
gives **real negative signals** (44% of rows are liquidatable/disqualified) to drive the
disqualification rules the brief requires, and it gives the team a genuine number to quote for
ICP sizing in Moldova (10,159 live IT companies).

**Caveats:** no employee count and no revenue, so ICP criteria on "company size" cannot be met
from this source alone. `datastore_search` is **not** available for this resource — you must
download and parse the XLSX (38 MB, a few seconds; needs openpyxl or similar). The licence
reads "License Not Specified" — credit ASP and do not redistribute the raw file.

### 4.2 SEC EDGAR, live-tested

A full-text search for `"digital transformation"` across `8-K` filings from 2026-09-01 to
2026-09-25 returned **25 hits**, top result `FLEXSTEEL INDUSTRIES INC (FLXS) (CIK 0000037472)`,
form `8-K`, `file_date 2026-09-11`. The identical request **without** a User-Agent returns
**`403`**. Legitimate, dated, official-province signal — for US-listed companies only.

---

## 5. Crunchbase and firmographics alternatives

**Crunchbase: no free API.** Their docs state *"Access to the Full API Requires an Enterprise
or Applications License."* The pricing page returns `403` to curl. There is no free tier to
plan around. Anything in the original hypothesis that says "crawl Crunchbase" is either a
licence violation or a scraper that will be blocked.

**LinkedIn: excluded by the brief itself**, and correctly so — there is no free API and
scraping violates their terms. The brief's instruction not to depend on it should be treated
as a hard architectural constraint, not a preference. Notably, **the job-posting APIs in §1
recover most of what LinkedIn would have been used for** (hiring signals), and the **Moldova
register (§4.1) recovers leadership names** for the home market.

| Alternative | Free? | Auth | Gives | Gaps / honest reliability |
|---|---|---|---|---|
| **Wikidata SPARQL** | Yes | None | Employee count (P1128), industry (P452). Tested: Google 187,000 employees; Siemens 199,652 | Coverage thin for startups and for Moldova; duplicate rows; counts often stale. **Not a register** |
| **GLEIF LEI** | Yes | **None** | Legal name, address, country, registration status, legal form. `filter[entity.legalName]=Siemens` → 597 records | **No employee count, no industry, no funding.** A legal-entity registry, not firmographics |
| **Moldova `dataset.gov.md`** | Yes | None | Legal form, address, CAEM industry codes, directors, founders, status | No headcount, no revenue. Moldova only |
| **GLEIF + VIES + Companies House** | Yes | mixed | Entity resolution and validation across jurisdictions | Each covers a different slice; none gives size |
| **Tech-stack (BuiltWith / publicwww)** | **No** | key | Technology adoption signals | No verified free API tier. Wappalyzer's free surface is a browser extension. For a hackathon, the only free route is grepping the company's own HTML (§3), which has low recall |

**Bottom line on firmographics:** there is no free Crunchbase substitute that provides
headcount and funding at scale. Design the ICP filters around what *is* free — country,
industry (CAEM/SIC/LEI), legal form, and hiring behaviour — rather than around employee count.
Be explicit about this in the pitch; a jury that knows Crunchbase is closed will respect the
substitution, and the rubric rewards configurability, not data volume.

---

## 6. Search-as-a-service on a budget

**The honest answer: the free web-search layer does not exist any more.** Every scrapable or
free-tier option was tested and failed. This is the single biggest architectural constraint in
this document.

| Source | Result | Detail |
|---|---|---|
| **DuckDuckGo** (`html.` and `lite.`) | **BLOCKED** | `HTTP 202` with a CAPTCHA — *"Unfortunately, bots use DuckDuckGo too… Select all squares containing a duck"*. Fires on the **first** request, not a rate limit. It is a datacenter-IP block; **a hackathon server will not pass.** The Instant Answer API (`api.duckduckgo.com`) returns real JSON but is an entity/topic lookup — `site:` queries return zero results. **Not a web search API** |
| **Bing Web Search API** | **RETIRED** | Retired **11 August 2025**: *"Any existing instances… will be decommissioned completely, and the product will no longer be available to be used or new customer signup."* — https://learn.microsoft.com/en-us/lifecycle/announcements/bing-search-api-retirement. `api.bing.microsoft.com/v7.0/search` → `401`; `bingcustomsearch` → `404`. Migration target ("Grounding with Bing Search") returns summaries, not raw results |
| **Google Custom Search JSON API** | **CLOSED to new customers** | *"The Custom Search JSON API is closed to new customers"*; existing users transition by 1 Jan 2027. It also **cannot search the whole web** — it only queries a Programmable Search Engine you configure. Unavailable to a new team |
| **SearXNG public instances** | **Unusable** | ~20 tested: `429` (searx.be, priv.au, opnxng.com, paulgo.io, …), Cloudflare/bot challenge (baresearch.org, search.inetol.net, …), `418` (search.mdosch.de), `403` (searxng.site). `searx.be/search?q=…&format=json` returned **HTML, not JSON** — JSON is disabled by default |
| **Jina Reader** (`r.jina.ai`) | **BLOCKED** | `HTTP 403`, Cloudflare "Just a moment…", on plain, browser-UA and `Accept: text/plain` variants. Keyless use is not viable from a server |
| **Brave Search API** | **Best legit option** | **$5 free credits/month ≈ 1,000 searches**. 50 qps. **Requires a credit card** as anti-fraud (not charged). Signup approval time not documented |
| **Tavily** | **Strong** | 1,000 credits/month, **no credit card** |
| **Exa** | **Strong** | $10 credits ≈ ~1,400 searches, resets to $10 on the 1st; **no payment method required** |
| **Firecrawl** | Good | 1,000 credits/month ≈ ~500 searches or 1,000 pages; 10 req/min |
| **SerpAPI** | Limited | **250/month** (not 100 — changed). 50/hr |
| **SearchApi.io** | Trial | 100 free requests only |
| **ScrapingBee** | Trial | 1,000 trial credits, **no permanent free tier** |

### 6.1 So can scraping be avoided entirely?

**No.** You cannot substitute search APIs for scraping on a zero budget, because the free
search tier effectively does not exist. But the good news is that **you barely need web search
at all**, because the highest-value sources in this document are *structured APIs that are
themselves searchable*:

- **ATS APIs (§1)** are already a structured, queryable index of hiring signals — no search
  engine involved. This is why the ATS category matters so much: it removes the dependency.
- **Google News RSS (§2)** is a per-company news query with `when:` filters — no search engine.
- **SEC EDGAR FTS (§4.2)** is full-text search over filings — no search engine needed.
- **Sitemaps (§3)** enumerate a company's own pages — no search engine needed.

Search is only genuinely required for **board-token discovery (§1.4)**, and that is solved
once per company by hand for a curated seed list. **Design so that search-as-a-service is a
convenience, never a dependency.** If the team wants a real search API, **Tavily (1,000/month,
no card) is the correct pick**, with Brave as the fallback if someone has a card.

**Honest reliability rating for `site:` searches against ATS boards:** low from a server IP.
If a team does get a key, `site:jobs.lever.co "<company>"` is a genuinely useful *setup-time*
discovery aid — but it must never sit in the runtime path of the demo.

---

## 7. Traps

Ordered by how badly each one hurts if hit during the build.

1. **Silent-success APIs.** Workable's widget returns `200` with `"jobs":[]` for every board
   tested. GDELT returns `429` with a *message body*, not an error status your HTTP client
   will throw on. **Never treat `200` as "has data" — assert on content.**
2. **Soft 404s from JS-rendered sites.** `orange.com/en/newsroom/rss` → `200` with an HTML
   shell. Any feed ingester that checks only the status code will ingest garbage. **Assert the
   body starts with `<rss` or `<feed`.**
3. **GDELT is throttled far beyond its documented limit.** The error says one request per 5
   seconds; measured behaviour is ~1 success in 3 with 11–14 s latency. **Do not put GDELT in
   the demo's critical path.** Background enrichment with retry only.
4. **Google News RSS links do not resolve to publishers.** The `<link>` is a self-referential
   redirect and the old base64 decode trick now returns encrypted bytes. **You need a headless
   browser to get the publisher URL** — budget for it, or display the `<source url>` + title.
5. **The free search layer is gone.** DuckDuckGo CAPTCHAs server IPs on the first request; Bing
   is retired; Google CSE is closed to new customers; SearXNG instances rate-limit or challenge;
   Jina 403s. **Any plan whose architecture assumes "we'll just search the web" is dead on
   arrival.**
6. **RSSHub's public instance is deliberately restricted** and says so: *"intended for testing
   purposes only and should not be used as a production RSS source."* The brief names it;
   self-hosting is the only route.
7. **Keyword matching over job descriptions produces false positives** — 18–28 "hits" on live
   boards with many irrelevant matches. With **Signal Relevance weighted 25%** and the rubric
   explicitly rewarding a *low false-positive rate*, this is the most expensive trap to leave in.
8. **Crunchbase has no free API** and LinkedIn is excluded by the brief. Any leftover plan text
   saying "crawl Crunchbase/LinkedIn" is a liability in front of a jury.
9. **Board-token discovery is unsolved at scale** (45% by slug guess, 0/33 by redirect, failed
   by HTML grep). Do not promise automatic company onboarding; promise a curated account list.
10. **Sitemap diffs need two crawls.** The signal is the *change*, so a cold start yields
    nothing. Seed a baseline snapshot before the demo, or the "we detect website changes"
    feature will show an empty screen.
11. **Silent schema drift.** Lever returns `createdAt` although it is absent from the documented
    field table; SmartRecruiters' token is case-sensitive (`bosch` → 0, `BoschGroup` → 4,797);
    Workday returns relative dates. **Write a per-source fixture test** so a change fails loudly.
12. **WAF blocking is patchy, not universal.** SAP `403`s while Bosch and Endava serve fine —
    so crawler failures will appear per-company at runtime, not uniformly at build time.
13. **SEC EDGAR requires a contact User-Agent** or returns a bare `403`. Set it once, globally.
14. **Personio has largely moved** (`307` → personio.com on 4/5 hosts tested). Verify per-company.
15. **Moldova's register is "License Not Specified".** Credit ASP, do not redistribute the raw
    file, and do not build a product claim on owning the data.
16. **A note on the hypothesis document:** `docs/INITIAl_HYPOTHESIS.md` describes a "Jev"
    classifier from "TypeSafe AI" and an hourly Crunchbase/LinkedIn crawl. Both premises are
    unverified and the crawl is not legally or technically available. See `10_jev_reality_check.md`.

---

## 8. Live versus historical: what can carry the demo

| Source | Live, fresh, dated? | Judge-verifiable in one click? |
|---|---|---|
| **Greenhouse / Ashby / Lever / SmartRecruiters** | **Yes — postings dated today** | **Yes** — public URL per posting |
| Workday CXS | Yes, but the date is prose (`"Posted 2 Days Ago"`) | Yes |
| **Google News RSS** | Yes — `pubDate` to the minute | Yes, **after** browser-based URL resolution |
| **SEC EDGAR full-text search** | Yes — filings dated (e.g. 8-K `file_date 2026-09-11`) | **Yes** — official government source |
| **Moldova register** | Weekly; latest `2026-09-21` | Yes — but the artefact is a 38 MB XLSX, not a per-company page |
| PR wires / regional RSS | Yes — `pubDate` from today | Yes |
| GDELT | Theoretically; unreliable in practice | No |
| Sitemap `lastmod` | Yes, but only "a page changed" | Weakly — meaningless without your diff |
| UK Companies House | Yes — filings are dated | Yes, once a key is held |
| Crunchbase / LinkedIn | n/a | n/a |

---

## Ranked: top 5 to build on

**1. Public ATS job-board APIs — Greenhouse + Ashby + Lever + SmartRecruiters.**
The only category that is simultaneously keyless, structured, freshly dated to the hour,
free of legal risk, and *directly* answers a signal question the brief itself poses ("Is the
company hiring RPA developers, business analysts, automation engineers, AI specialists?").
A posting dated today is not an inference — it is evidence, and a judge can click the URL.
It also removes the dependency on web search that kills every other news-based approach.
*Build:* a seed table of 20–30 curated accounts with resolved board tokens, a poller, and
title-weighted classification.

**2. The Moldovan open company register (`dataset.gov.md`).**
Free, weekly, 302,954 companies, no registration, with **director names, ownership
percentages, activity codes and liquidation dates**. It is the only source found that supplies
firmographics *and* leadership *and* negative signals in one artefact, and it is the home
market — which makes it uniquely defensible for this specific brief, this client and this
venue. The 44% liquidated share gives the disqualification rules real work to do, and the
10,159 live IT companies give the pitch a credible ICP size.
*Build:* a one-off XLSX ingest into PostgreSQL, CAEM→industry mapping from sheet 2, and a
`Data lichidării IS NULL` filter as the first disqualification rule.

**3. Google News RSS.**
The only free, keyless, per-company news query that returns a real `pubDate`. It answers the
brief's "recent company-related news and announcements" requirement and supports `when:7d`
windows. Its one flaw — links need a headless browser to resolve — is a known, bounded cost,
and Playwright is already in the brief's expected stack.

**4. SEC EDGAR full-text search.**
Free, no key, official, dated, and full-text searchable across filings with form-type and
date-range filters. It is the highest-*credibility* signal available: a government filing is
unimpeachable evidence, and it directly serves the brief's "digital transformation
initiatives" and "leadership changes" questions for US-listed targets. Scope it honestly as
US-only.

**5. UK Companies House.**
Free API key, generated immediately, 600 req/5 min, and the officers endpoint is the cleanest
dated **leadership-change** signal available anywhere in this research. It is ranked fifth
only because it requires a signup step and its endpoint bodies could not be live-verified
during this pass — worth one team member's first 30 minutes to confirm the key arrives and
capture the real response shape.

*Honourable mention:* **Tavily** (1,000 searches/month, no credit card) if the team wants a
genuine web-search API for setup-time token discovery. It is not a dependency — it is a
convenience for the one task (§1.4) that search would genuinely help with.

---

## Documentation URLs

| Source | URL |
|---|---|
| Greenhouse Job Board API | https://docs.greenhouse.io/job-board.html |
| Lever postings API | https://github.com/lever/postings-api |
| Ashby public job posting API | https://developers.ashbyhq.com/docs/public-job-posting-api |
| SmartRecruiters posting API | https://developers.smartrecruiters.com/docs/posting-api |
| Recruitee API | https://docs.recruitee.com/ |
| GDELT DOC 2.0 API | https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/ |
| Google News RSS | https://news.google.com/rss/search?q=TEST |
| NewsAPI pricing / terms | https://newsapi.org/pricing |
| RSSHub self-hosting | https://docs.rsshub.app/deploy/ |
| UK Companies House auth | https://developer.company-information.service.gov.uk/authentication/ |
| SEC EDGAR full-text search | https://www.sec.gov/edgar/search/ |
| SEC EDGAR APIs | https://www.sec.gov/search-filings/edgar-application-programming-interfaces |
| Moldova open data portal | https://dataset.gov.md |
| GLEIF LEI API | https://www.gleif.org/en/lei-data/gleif-api |
| Wikidata SPARQL | https://query.wikidata.org/ |
| EU VIES REST | https://ec.europa.eu/taxation_customs/vies/ |
| Bing Search API retirement | https://learn.microsoft.com/en-us/lifecycle/announcements/bing-search-api-retirement |
| Google CSE overview | https://developers.google.com/custom-search/v1/overview |
| Brave Search API | https://api-docs.search.brave.com/ |
| Tavily | https://docs.tavily.com/ |
| Sitemap protocol | https://www.sitemaps.org/protocol.html |
