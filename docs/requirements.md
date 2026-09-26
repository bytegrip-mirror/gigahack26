# What the desk has to do, and where each piece stands

The list this file tracks came from the operator, in their words, on 2026-09-26.
It is the specification for the next pass of work; the status column is what is
actually in the tree, not what is planned.

Read this before adding a screen or a column. `ui-architecture.md` says how the
interface is built; this says what it is for.

---

## 1. ICP profiles

| # | Requirement | Status |
| --- | --- | --- |
| 1.1 | Create an ICP profile | done — Settings → *Customer profiles* |
| 1.2 | A board of companies | exists |
| 1.3 | On the board, see clearly which ICP profile a company belongs to | done — a profile tag in the company cell |
| 1.4 | Filter the board by ICP profile | done — under `Filters` |
| 1.5 | Activate / deactivate an ICP profile | done — *Turn off* in Settings; it leaves the board and the filter |

The product had exactly one customer profile: a single `workspace_settings` row
holding the countries, the industries, the size floor and the gates. Requirement
1.1 makes it a list. A profile is a named book of business — "DACH industrials",
"UK financial services" — and a company belongs to a profile when its country,
industry and headcount satisfy that profile's own rules.

**The thresholds stay shared.** Base rate, the band cuts and the rank discount are
properties of how the desk scores, not of who you are selling to, so they stay on
the single settings row; a profile carries only the *who*. That keeps one score
comparable across profiles, which is what makes the board's ranking mean anything
when a rep switches between them.

**A company can belong to more than one profile**, because the books of business
overlap — that is the point of naming them. The board shows the first match by
profile order and says so on hover.

## 2. What the board shows

| # | Requirement | Status |
| --- | --- | --- |
| 2.1 | A signal graph to the left of the company — strength over time | done — `SignalTrace`, twelve monthly bars |
| 2.2 | The time of the last signal | done — the `Latest` column |
| 2.3 | The score of the last signal | done — the word in `Fit`, the amount on the Signals list |
| 2.4 | Rename "Why we would call" to "Signals" | done |
| 2.5 | Rename "Newest" to "Latest" | done |
| 2.6 | The signal chart is *nonchalant*: the shape only, inside the row, between the status and documents columns, with no points and no axis, and the detail on hover | done — no axis, no points, no tooltip of its own; the numbers are in the hover card |

The sparkline is a mark, not a chart. It has no axes, no labels, no points and no
tooltip of its own; hovering anywhere on it opens the company's hover card, which
is where the numbers live. A row of them is read as a shape — "this one spiked
last week", "this one has been quiet for a quarter" — and anything more would be
a chart pretending to be a row.

## 3. The company, at two depths

| # | Requirement | Status |
| --- | --- | --- |
| 3.1 | Hovering a company opens a card: name, overview, signal graph, signal list, fit | done — a card after 260ms, so passing the mouse across the board does not flash cards |
| 3.2 | Clicking opens a full page with more of the same | done — a row now navigates to `/companies/<id>` |
| 3.3 | …with tabs under the graph | done — Signals · News · History · Overview |
| 3.4 | Signals tab: the readings, in more detail than the hover | done — every reading with its quote, source, age and amount |
| 3.5 | News tab: the documents, linking out to the source | done |
| 3.6 | History tab: have we offered anything, have we tried | done — stage, times worked, last contact, channel, last draft, CRM push |
| 3.7 | AI overview tab | done, as *Overview* — see the note below |
| 3.8 | "Why is this a fit" | done — the first card |
| 3.9 | What service is suggested to offer | done — the service line's own proposition |

The hover card and the page are deliberately the *same component* at two sizes.
A rep who hovers, then clicks, must not have to re-find where things are.

## 4. Competitors

| # | Requirement | Status |
| --- | --- | --- |
| 4.1 | Add a competitor from the UI and the API | done — `POST /api/companies/{id}/competitor`, and the row's `⋯` |
| 4.2 | Remove one | done — the same endpoint, on the row and on every name in Settings |
| 4.3 | No text naming the source files the list came from | done — the disclosure is now an editable list |

A competitor is a company that sells what we sell, and one can be marked from
wherever the rep notices it: the board row, the company page, or the Customer
profile screen. It is a property of the company, so it is stored on the company
and it gates at scoring time exactly as the shipped list does.

## 5. Leads and the pipeline

| # | Requirement | Status |
| --- | --- | --- |
| 5.1 | A separate board for leads in work — contacted, replied, qualified — because they are hard to tell apart from the backlog | done — *In work* · *Backlog* · *Everything* |
| 5.2 | Sorting in the pipeline | done — last worked, company, fit, furthest along |

## 6. Copy

| # | Requirement | Status |
| --- | --- | --- |
| 6.1 | No text naming a source file, a script, a config key or the pipeline's internals | partial — see the note below |
| 6.2 | Search the whole app for more of it | partial — every screen was read; the worst of it is gone, the Settings telemetry panel is not |

The rule: **the screen explains the company, never the machinery.** If a sentence
would only make sense to someone who has read the repository, it is deleted
rather than reworded. What it was doing for the reader — telling them the fact is
trustworthy, or that something is unavailable — is said in the reader's terms or
not at all.
