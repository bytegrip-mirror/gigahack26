# The interface, as it is built

A reference for the shell, the token scale and the shared components. Read this
before adding a screen, a column or an action, so the new work lands in the same
system rather than beside it.

Two gates enforce everything here. `pnpm run check` must report zero errors: it
runs `svelte-check` and then the design lint from section 2. Every row of the
board must survive a 1024×768 projector: nothing load-bearing below 11px, and
nothing that only appears on hover.

---

## 1. The shell

One rail, one scroll region per pane, no document scroll. `+layout.svelte` owns
the rail and mounts the command palette; every screen below it is a `View` that
fills the remaining width.

```
┌──────────┬────────────────────────────────────────────────────────────┐
│ rail     │ Topbar       identity · metrics · one primary action · ⋯   │
│ 232px    ├────────────────────────────────────────────────────────────┤
│          │ Toolbar      the lens: filters, search, scope              │
│ brand    ├────────────────────────────────────────────────────────────┤
│ search   │ Panes                                                      │
│ nav      │  ┌──────────────────────────┬─────────────────────────┐    │
│ corpus   │  │ .list  (scrolls)         │ .detail (scrolls)       │    │
│          │  │  the ranked table        │  the scorecard          │    │
│ region   │  └──────────────────────────┴─────────────────────────┘    │
│ refresh  ├────────────────────────────────────────────────────────────┤
│          │ Pager        1–50 of 578 · page size · prev/next           │
└──────────┴────────────────────────────────────────────────────────────┘
```

The rail reads top-down as identity, navigation, then the facts about the corpus
and the two workspace-wide settings, pushed to the bottom together so they sit in
the same place on every screen.

When a company is open, the view becomes two columns and the detail pane takes the
**full height of the window** — the topbar, toolbar, list and pager are the left
column and stop at the divider, rather than running over the top of the pane.
Below 1100px the pane lays over the view instead of beside it.

Rules that hold across every screen:

- **The topbar carries identity, the numbers that summarise the screen, and at
  most one prominent button.** Everything else is a `Menu` behind a `⋯`.
- **The toolbar carries filters and nothing else.** It never holds a primary
  action.
- **The panes are the only part that scrolls.** Its children carry `min-h-0`
  so a grid child cannot push the view taller than the viewport.
- **Selection lives in the URL** (`/?co=<id>`), so a company stays linkable.
- Below 1100px the detail pane becomes an overlay inside the panes rather than a
  second column, so the table keeps its width on a projector.
- `Panes` is a one-column grid. A screen that wants a plain block stack (the
  pipeline) passes `class="block"`.

## 2. The design system

The interface is Tailwind v4, and Tailwind owns the design system rather than
sitting next to it. Three parts:

1. **The theme.** `ui/src/app.css` opens with `@import 'tailwindcss'` and defines
   every colour, space, type size and z-index in one `@theme` block. The block
   starts with `--*: initial`, so Tailwind's own scale does not exist: `p-7`,
   `text-gray-500` and `rounded-lg` produce no CSS, and an off-scale value cannot
   be written by accident. The tokens are also plain CSS custom properties, which
   is what the existing rules read.
2. **The utilities.** Every shared class — the shell, the controls, the tags, the
   table — is an `@utility` in the same file, written against those tokens. One
   place decides what a button's padding, colour and type are, and the markup
   asks for it by name. Element-qualified rules (`table.tbl th`,
   `th[aria-sort] .icon`) live in a `@layer utilities` block beside them, because
   a rule that has to beat a utility cannot itself sit in a lower layer.
3. **The views.** A view keeps only what is true of that view alone, and it is
   scoped CSS in the component. Nothing here re-declares what a token already
   says.

**A class name is not free.** Tailwind generates static utilities whatever the
theme says, so a project class called `grid`, `block`, `flex`, `border`,
`hidden`, `static`, `transform` or `transition` silently acquires Tailwind's
declarations — `class="grid"` on a `<table>` sets `display: grid` and collapses
every column. The table class is `tbl` and the two modifiers that collided are
`stretch` and `panel-block` for that reason. Check a new name against Tailwind's
utility list before adding it.

**The cascade has an order.** `@theme` and `@layer base` are lowest, then the
component styles a view scopes for itself, then `@layer utilities`, where both
Tailwind's utilities and this project's live. A design-system class therefore
beats a component's own rule of the same specificity, which is what the
interface was built on: every rule in the old `app.css` was unlayered and loaded
last, so it won every tie. Moving one of those rules into a lower layer changes
the rendering, and the way to notice is a screenshot diff, not a reading.

### 2.1 Tokens

| Group | Utilities | Values |
| --- | --- | --- |
| Spacing | `p-*`, `m-*`, `gap-*`, `inset-*` … | `0`, `px`, `0.5` 2, `1` 4, `2` 8, `3` 12, `4` 16, `5` 24, `6` 32 |
| Sizes | `h-*`, `w-*`, `size-*`, `min-h-*` … | `ctl-sm` 22, `ctl` 28, `row` 34, `bar` 44, `head` 67, `rail` 232 |
| Colour | `text-*`, `bg-*`, `border-*` … | `orange`, `orange-deep`, `orange-ink`, `orange-wash`, `orange-line`, `accent`, `accent-wash`, `accent-line`, `oxblood`, `oxblood-wash`, `oxblood-line`, `ink`, `ink-2`, `ink-3`, `ink-4`, `paper`, `surface`, `sunken`, `rule`, `rule-strong` |
| Type size | `text-*` | `micro` 11, `sm` 11.5, `base` 12.5, `md` 13.5, `lg` 15.5, `xl` 18, `title` 22 |
| Family | `font-*` | `display` (Space Grotesk), `ui` (Archivo) |
| Leading | `leading-*` | `none` 1, `tight` 1.1, `snug` 1.3, `normal` 1.45, `relaxed` 1.6 |
| Tracking | `tracking-*` | `tight`, `num`, `normal`, `wide`, `caps` |
| Depth | `z-*` | `sticky` 2, `menu` 200, `sheet` 260, `toast` 300 |
| Pane header | `min-h-head` | 67px, shared by every pane header |

Text styles are utilities too: `dsp` (display face, tracked tight), `label`
(uppercase tracked label), `sub` (helper line), `key` and `num` (tabular
figures). Layout helpers are `scroll`, `fields`, `meta` and `tbl`. Radii,
leading, tracking and breakpoints are not tokens: they are written as literals in
the utility that needs them, which is why `--*: initial` costs nothing there.

**Surfaces arrive.** Panels, sheets, popovers and toasts enter with a short fade
and a few pixels of travel. A surface that attaches to a control (a menu, a
listbox) uses `animate-rise` at 120ms. A surface that takes the screen (a dialog,
the detail pane, a toast) uses `animate-arrive` or `animate-enter-right` at 160ms.
All of them use the same curve as every other transition. Nothing scales and
nothing bounces. The primitives already apply these, so a view does not add them.

Motion is on entry only. An exit animation keeps a component mounted while it
leaves, and that is where flicker and double-render bugs occur. A panel that
closes instantly is not a defect.

Every keyframe declares only its `from` state, so it ends on the element's default
state. If an animation is skipped, nothing stays invisible.
`prefers-reduced-motion` sets every animation to 0.01ms.

The board title rule and the detail pane title rule are on the same line, because
`Topbar` and the scorecard header both use `min-h-head`.

**One state has one name.** The panels had drifted into four vocabularies for the
same facts, which is how a product starts feeling like it has too many labels:
`THIN` was "Thin" on the board, "Insufficient evidence" on the band and "Unproven"
on the fit; `DISQUALIFIED` was "Rejected" in Settings and "Out of the queue" in
the filter; a polarity was "Counter" in the editor, "counter" on the row and
"in-house" on the map. The words are now:

| State | The word |
| --- | --- |
| `PRIORITY` / `NURTURE` / `WATCH` | Priority / Nurture / Watch |
| `THIN` | Unproven |
| `DISQUALIFIED` | Out of the queue |
| `POSITIVE` polarity | Counts for the company |
| `NEGATIVE` polarity | Counts against the company |
| `DISQUALIFYING` polarity | Removes the company from the queue |
| lead stage `REJECTED` | Rejected — a rep's decision, not the board's |

A group of readings is deduplicated by question before it is printed, in the
evidence panel and on the board's row alike: one question firing on six documents
is one reason, not six, and the same title twice in one list reads as a bug.

Type has three roles, and a size belongs to exactly one role:

- **`title` and `xl`** are for page titles and panel headings, in `font-display`.
- **`lg`, `md` and `base`** are for row data, field values and body copy.
- **`micro` and `sm`** are for labels, tags and helper lines. 11px is the floor.
  Nothing load-bearing goes below it.

Numbers always carry `num`, so that columns align. Radii stay at 2px. There are
no shadows and no gradients. A popover shows its edge with a full-contrast ink
border against paper.

## 3. The shared components

All of them are in `ui/src/lib/components/`. Read the file for the exact props.

| Component | Contract |
| --- | --- |
| `Icon` + `icons.ts` | One stroke set. `size`: `sm` 13, `md` 15, `lg` 17, `xl` 22. Add a path to `PATHS`, never an inline `<svg>` in a view. |
| `Menu` + `menu.ts` | The overflow pattern. Takes `items: MenuItem[]` and `onselect(id)`. `text` renders a labelled button, otherwise an icon button; `stretch` makes it fill its container. An item with `heading: true` is a section label inside the panel, not something to choose. |
| `Tag` | The only badge. `variant` selects the colour, `band` renders the semantic band colours. |
| `Th` | A sortable header cell. Renders `aria-sort` and a direction arrow that shows without hover. `class` takes the column's hide rule. |
| `Metric` | A number above its tracked label, with an optional `tone` and `hint`. Use it for every top-level summary, never a sentence. |
| `Pager` | Range, page size, prev and next, over the fetched array. |
| `Star` | The bookmark toggle. |
| `CompanyMark` | The company's icon, 20/26/34px, or a monogram when `tools/fetch_logos.py` found none. Always the same square, so a row without one does not go ragged. |
| `CaptureSheet` | The stored document with the marked span, opened from any quote. |
| `OutreachModal` | The drafted message, full window. Consumes Escape so a draft and the board position survive it. Mounts through `use:portal` — see section 8. |
| `LoadingVeil` | Sits over its pane, so a load never moves the layout. |
| `Hubspot` | The HubSpot mark. |
| `Logo` | The wordmark. |

`CommandPalette.svelte` (⌘K / Ctrl-K) stays in `lib/components/`, with the other
components that are specific to this product. It searches companies through the
API and jumps to views, service lines and regions. The layout mounts it once.

State lives in three stores, all `.svelte.ts`:

- `session.svelte.ts` — service line and region, persisted to localStorage.
- `workspace.svelte.ts` — the one `Meta` and `Status` fetch for the whole app,
  plus per-line Priority counts. Views read `workspace.meta`; nothing else calls
  `fetchMeta`.
- `command.svelte.ts` — whether the palette is open.

`lib/export.ts` holds `csv`, `save` and `copy` so every export path produces the
same file. Exported cells are written by a rep into a spreadsheet, and the values
include text the crawler took from the open web — so `cell` neutralises anything
a spreadsheet could read as a formula (a leading `=`, `+`, `@`, tab or carriage
return) while leaving genuine negative numbers as numbers. Route new exports
through `csv` rather than building rows by hand.

## 4. Action hierarchy, screen by screen

One prominent action per screen. Everything a rep might reach for less often is
one `Menu` away, and every row carries the same `⋯` in the last column.

| Screen | Primary | In the `⋯` |
| --- | --- | --- |
| Board | — every action is in the `⋯` | export the board, copy a link, clear filters, re-score; per row: open the scorecard, open the quoted capture, copy a link, export the row, set the lead stage |
| Company pane | *Generate outreach*, then *Push to HubSpot* | copy the summary, open the website, count as a lead, count as rejected, close |
| Pipeline | per-row *advance to the next stage* | open on the board, copy a link, export the row, move to any stage; screen-level: export the pipeline, open the board |
| ICP Studio | *New profile* | per row: open it, switch it on or off, delete it; on the open profile: save, discard |
| Signal map | *Table* (a reading, not an action) | — |

| Settings | *Save and re-score* | read the writer brief; per service line: enable/disable, delete |

Both buttons on the company pane are full width and stacked: the outreach draft is
the work, and the HubSpot push is the hand-off, so it carries the HubSpot mark.

The outreach editor takes the whole window. It gives the draft two independently
scrolling columns — the message on the left, its sources on the right — so reading
a citation never scrolls the text you are editing. Above them sits the steer bar:
six preprompts that rewrite the draft on one click, and a box for an instruction in
the rep's own words, both of which reach `ModelDraftWriter` through
`Brief.instruction`. The subject and body stay editable, and **Send** hands them to
the rep's mail client through a `mailto:` built with `encodeURIComponent` (not
`URLSearchParams`, which writes `+` for a space and mails it literally). The
channel is a small selector beside the draft rather than two equal buttons in the
header, because the channel changes the text: InMail drops the sign-off and
signature for something under 90 words, and its action is *Copy for LinkedIn*
because there is no `mailto:` for it. When no writing model is configured the
preprompts are disabled and the sheet says so, rather than returning the same
draft as though the instruction had landed.

The company pane has its own hierarchy: the header carries identity — mark, name,
then domain and firmographics — and a tab strip sits directly under it,
**Overview** (the default) and **Documents** *(n)*. Each tab replaces the whole
body below, so the document list gets the full height of the pane rather than a
slot at the bottom of a scroll. There is no Ledger tab: it printed one row per
reading with its weight and its arithmetic, which is a spreadsheet, and the export
produces exactly that. The derivation is not on the screen at all — the evidence
ledger leaves through `⋯ → Export`, and what a rep reads is a word.

Overview reads in the order a rep works: the reading, why now, the evidence behind
it, then the stage and the two things you do about it.

- **The reading** is the fit word in `title` size — `Strong`, `Good`, `Fair`,
  `Weak`, cut from the configured thresholds — with the band tag beside it and one
  sentence saying what the word means against the market default. There is no
  meter and no probability: the pool average and the Priority line marked on a bar
  explain the scoring model, not the company. The word is the only place orange is
  spent on this screen besides the primary action.
- **Themes** sit in the same panel as tags, with `corroborated` or `one theme
  only` beside them — they are not a board column, because the question "why is
  this only Nurture" is asked with the company open.
- **Why now** is the narrative and the urgency, and nothing else. The **Facts**
  toggle and the counts behind it — strongest signal, themes agreeing, evidence
  counts, how long the band holds, documents read — were removed: they were
  arithmetic in a slot that wanted a sentence, and every one of them is in the
  export.
- **Why we would call** lists the question titles that fired, each opening the
  capture it was read from; then **Reasons not to call**, then **Read, not
  evidence**. No contribution is printed on any of them.
- **Documents** lists what answered a question first, then, under *Read, nothing to
  report* on a sunken background, the documents that answered none.

The fit and the band appear again in the tab strip **only when a tab other than
Overview is showing it**, because otherwise the document list scrolls the reading
out of sight — and on Overview a second copy would just be the same badge twice.

The board additionally has a **bulk bar**: selecting any row replaces the filter
toolbar with a dark bar holding the selection count, *Set stage*, *Export*,
*Copy links* and a clear button. Bulk stage changes fire one `recordLead` per
company and report how many landed.

**The board opens on the work, not on the book.** The default view is
`To call`: worth calling — Priority, Nurture or Watch — and nobody has worked
them yet. It used to be `All`, which meant every company the corpus holds,
so a company that had been contacted six times still sat at rank 1 of the
calling list. The view segments are `To call`, `Priority`, `Unproven`,
`Out of the queue`, `Saved` and `Everyone`; `To call` is the only one that reads
the lead stage, and its tooltip says so. Everything else about stage lives in the
`Filters` menu, so a rep who wants the companies already worked sets the stage
there rather than scrolling past them on the board.

**The board's filter bar is one row and four controls.** Left to right: the
service line, then the one segmented control that decides *which companies*, then
a spacer, then the search and one `Filters` menu, then `Clear` when anything is
narrowing the view. The engine filter and the lead stage used to sit at opposite
ends of the bar as a menu and a bare `select`, which read as two unrelated
controls and pushed the bar onto a second line at 1440. They are the same kind of
thing — a way of narrowing the rows you already have — so they are one menu with
two headed sections, and its label carries the count of what is on.

## 5. Scope, region, and where each control lives

Two settings reach across screens, and each sits where its effect is visible.
This is deliberate: a control that silently does nothing on the screen you are
looking at is worse than no control.

**Service line** scopes the board and the pipeline. The switcher is therefore the
**first control in the toolbar of
the board and the pipeline** — the same component in the same position, reading
and writing one session value so the two never disagree — and it never appears
on Settings or in the rail. The workspace settings row is global, so that screen
does not pretend to be scoped: saving re-scores **every** service line.

The switcher is searchable, and it carries **All service lines** at the top. All
means what it says: every line is fetched and merged into one list, each company
kept once at the line it scores highest for, so the row still names the line that
justifies the call and the card opens against that line's policy. Rejected
companies stay at the bottom, the way a single line's own ranking puts them. The
`#` column then shows that merged rank rather than the server's per-line rank,
which would repeat the same numbers three times.

**Region** filters the board by market. It is workspace-wide chrome rather than a
per-view filter, so it lives in the **rail footer**, bottom-left, labelled *Board
region* to say what it applies to. The board states the active region in its
subtitle, so the effect is legible from the screen it changes.

The signal questions themselves no longer have a screen. They are seeded from
`PolicyLibrary` and read by the scorer; the interface shows their effect — on the
board, and as the shape of the Signal map — and does not offer to author them.

The board's band counts are tallied from the rows in hand, not read from the
server's per-line totals, so the Priority metric always counts the Priority
companies the region filter has left on screen.

**The Signal map shows how a question becomes a company.** Three columns — theme,
question, company — with an edge from each question to each company it found. It
is the one screen that shows the shape of the framework rather than a list of its
results: a question that found 53 companies draws a bar 53 wide, and a company
standing under three questions is visibly the reason the board calls it.

Two rules keep it legible. **Themes are separated by position, not by hue** — the
palette has three roles and a sixth categorical hue would be a different design
system — so colour carries meaning instead: ink for a question that counts for the
company, oxblood for one that counts against, and the same two on the edges that
leave them. And **the company column holds the top 16 by how many questions found
them**, not all 124, with the count in the toolbar saying what is below the cut.
The column is ordered by the average height of the questions feeding it, which is
what stops the edges from braiding.

Every company node is a link and opens its scorecard; a question is a label, not
a destination, now that there is nowhere to edit one. Hovering dims everything
not connected to the node under the cursor.

**The ICP Studio keeps the books of business.** A profile is a name, a colour and
a set of criteria — geography, industries, company size, exclusions — and the
board shows the union of the profiles in force. The list is on the left and the
open profile on the right — a list beside a detail, the same shape the board uses
for a company.

Its criteria panels **tile the width** rather than stacking in one column. This is
the one screen that breaks the single-column rule of §2.1, and it earns it: the
reader is comparing six short criteria, not reading prose, and a 760px column left
half the window empty while the panels scrolled past each other. Each panel's own
fields still run one to a line.

The colour is the **last** panel, and it opens a palette rather than sitting inline.
Choosing from thirty swatches at once is one decision; a swatch row beside the name
field is a decision that has to be made before the reader knows what the profile
is for. The palette is a full-window surface and so is mounted through `portal`
(§8). Reach — the count of what the criteria hold — sits above it and comes from
the same predicate the board uses, so the screen cannot promise a number the board
will not show.

The board says which books are on in the **Filters** menu, and says nothing about
them on the rows themselves. A coloured chip on every row was tried and taken out
again: at six chips to a screen it stopped being information and became texture,
and a row already carries the company's band, its lead stage and its signals. The
colour still does work where a person is choosing between profiles — the list, the
swatch and the picker — which is the only place a colour can be told apart from
the three roles the palette reserves.

**A profile's colour is the one place the palette admits a hue that means
nothing.** Everywhere else colour is a role — orange is the action and the fit,
viridian the corroboration mark, oxblood rejection — and the signal map separates
themes by position for exactly this reason. A profile is the deliberate exception:
the operator picks it, and it means only "this book and not that one".
The `Table` toggle is the same data as rows, which is the accessible view and the
one to reach for when the map is too dense to read a name off.

**The Market screen groups the corpus; it does not grade it.** It answers "where is
the demand" by country, industry or headcount, and the columns are counts a rep
can act on. The accuracy panel that used to sit above the table — a backtest with
a `30d` / `90d` / `180d` window selector — is gone, along with the `Med. fit`
column. Both printed a number that the screen itself then had to explain away: the
panel's own text said any rate it could compute would be "a fact about the crawl's
depth wearing the costume of an accuracy figure", and a median fit of 0.25 in
every row is the base rate restated twelve times. A screen that has to argue
against its own figure should not print it.

## 6. Density and alignment

Dense data is a table, never a grid of cards.

- Right-align numbers and amounts (`text-right num`), left-align text, centre
  status badges (`text-center`).
- Sortable headers only. Sorting is client-side over the fetched array, so both
  directions work; the `#` column keeps the server's fit rank whatever the sort,
  which is why its tooltip says *fit rank*.
- Paginate at 50 by default, with 100/250/All available. The board is 578 rows;
  rendering them all at once was the single largest cost on the screen.
- Truncate anything that can run long — company names and meta clamp at one line
  (the mark keeps its square), the question at one.
- Columns drop in a fixed order as width falls, so the table never squeezes: Docs
  and Newest below 1180px, then the split gives way to an overlay at 1100px.

The board's columns are `#`, the company mark, Company, Fit, Why we would call,
Docs, Newest, save and actions.

**The mark is its own column**, not a glyph inside the company cell: the names
then start on one x whether or not a company has an icon, and the column can be
scanned on its own. `CompanyMark` draws the 32px scraped icon or, where
`tools/fetch_logos.py` found none, a monogram of exactly the same size.

**Fit replaced both the score and the band.** There is one column, and what it
shows depends on what the queue decided:

| Band | The cell reads |
| --- | --- |
| Priority, Nurture, Watch | the word — `Strong`, `Good`, `Fair` |
| `Nurture` pinned at the Priority line | the word plus `capped · one theme` |
| `THIN` | `Unproven` |
| `DISQUALIFIED` | the `Out of the queue` tag and the gate's reason |

The Pipeline reads the same word off the same thresholds, because a rep moves
between the two screens on the same company and two vocabularies for one fact is
one too many.

A separate band column was redundant: the word is cut from the same three
thresholds that place the band, so the two agreed on every row that was not gated.
The one case where they disagree is the capped one — evidence at the line and a
single theme — and that case now says so in the cell rather than leaving the
reader to infer it from two columns. The numeric probability lives in the cell's
tooltip and in the CSV.

## 7. Adding to a screen

- **A column** — add a `Th` with an `id`, a `width` and a hide rule in `class`
  (for example `max-xl:hidden`). Put the same hide rule on the matching `td`, and
  add the `id` to the sort switch.
- **An action** — if it is the one thing the screen is for, it is a button in the
  topbar. Otherwise it is a `MenuItem`.
- **A row action** — append to the view's `rowMenu`/`menuFor` builder, with
  `separatorBefore` on the first item of a new group and `danger` on anything
  destructive. An action that is the point of the screen is a button instead,
  the way *Push to HubSpot* is on the company pane.
- **A chip field on the board** — the row carries six signals, not three, so the
  *Top evidence* cell can name three **distinct** reasons. It runs them through
  `reasons()`, which keeps the strongest signal per question, because one
  question firing on six documents is one reason, not six.
- **A panel** — `Panel` with a `title`, then `Metric` for numbers and `Facts`
  for key/value pairs. Prose belongs behind a disclosure, not in the
  default view.
- **A number the rep acts on** — a sales screen states the reading, not the
  derivation. If a figure would only be legible to someone who has read
  `ScoringService`, it belongs in an export or a tooltip, and the screen gets the
  word for it instead.

## 8. Full-window surfaces

A surface that takes the screen — the outreach editor, a toast — is `position:
fixed`, and a fixed element resolves against the nearest ancestor that establishes
a containing block, not against the viewport. **An ancestor with a filled
animation on `transform` is one**, even after the animation has finished: the
detail pane enters with `enter-right` and `fill-mode: both`, so the outreach sheet
rendered at the width of the pane until it was mounted on `document.body` through
the `portal` action in `ui/src/lib/portal.ts`. Check the ancestors before
concluding the CSS is wrong, and reach for the portal rather than removing the
entry animation.
