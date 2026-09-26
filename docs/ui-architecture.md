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
| `Menu` + `menu.ts` | The overflow pattern. Takes `items: MenuItem[]` and `onselect(id)`. `text` renders a labelled button, otherwise an icon button; `stretch` makes it fill its container. |
| `Tag` | The only badge. `variant` selects the colour, `band` renders the semantic band colours. |
| `Th` | A sortable header cell. Renders `aria-sort` and a direction arrow that shows without hover. `class` takes the column's hide rule. |
| `Metric` | A number above its tracked label, with an optional `tone` and `hint`. Use it for every top-level summary, never a sentence. |
| `Pager` | Range, page size, prev and next, over the fetched array. |
| `ScoreMeter` | The fit bar with the pool average and the Priority line marked. |
| `Star` | The bookmark toggle. |
| `Ledger` | The evidence ledger: one row per reading, with its weight, its source and the arithmetic behind a disclosure. |
| `CaptureSheet` | The stored document with the marked span, opened from any quote. |
| `OutreachModal` | The drafted message. Consumes Escape so a draft and the board position survive it. |
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
| Board | *Ask a question* | export the board, copy a link, clear filters, re-score; per row: open the scorecard, open the quoted capture, copy a link, export the row, set the lead stage |
| Company pane | *Generate outreach*, then *Push to HubSpot* | copy the summary, open the website, count as a lead, count as rejected, close |
| Pipeline | per-row *advance to the next stage* | open on the board, copy a link, export the row, move to any stage; screen-level: export the pipeline, open the board |
| Signal Studio | *New question* | export every question, duplicate, delete; per row: edit, duplicate, turn on/off, copy the key, delete |
| Settings | *Save and re-score* | read the writer brief; per service line: enable/disable, delete |

Both buttons on the company pane are full width and stacked: the outreach draft is
the work, and the HubSpot push is the hand-off, so it carries the HubSpot mark.

The outreach editor gives the draft two independently scrolling columns — the
message on the left, its sources on the right — so reading a citation never
scrolls the text you are editing. The subject and body are editable, and **Send**
hands them to the rep's mail client through a `mailto:` built with
`encodeURIComponent` (not `URLSearchParams`, which writes `+` for a space and
mails it literally). The channel is a small selector beside the draft rather than
two equal buttons in the header, because the channel changes the text: InMail
drops the sign-off and signature for something under 90 words, and its action is
*Copy for LinkedIn* because there is no `mailto:` for it.

The company pane has its own hierarchy: the header carries identity, a tab strip
sits directly under it — **Overview** (the default), **Ledger** *(n)* and
**Documents** *(n)* — and each tab replaces the whole body below, so the ledger
and the document list get the full height of the pane rather than a slot at the
bottom of a scroll.

Overview reads in the order a rep works: the score, why now, the evidence behind
it, then the stage and the two things you do about it. The band is stated once,
in the score block. The fit and the band appear again in the tab strip **only
when a tab other than Overview is showing it**, because otherwise the ledger
scrolls the score out of sight — and on Overview a second copy would just be the
same badge twice.

The arithmetic that used to sit in Why now — strongest signal, themes agreeing,
evidence counts, how long the band holds, documents read — is behind a small
**Facts** toggle in that panel's head. It is reference material, not the pitch,
and it was crowding out the sentence that actually explains the company.

The board additionally has a **bulk bar**: selecting any row replaces the filter
toolbar with a dark bar holding the selection count, *Set stage*, *Export*,
*Copy links* and a clear button. Bulk stage changes fire one `recordLead` per
company and report how many landed.

## 5. Scope, region, and where each control lives

Two settings reach across screens, and each sits where its effect is visible.
This is deliberate: a control that silently does nothing on the screen you are
looking at is worse than no control.

**Service line** scopes the board, the pipeline and the studio's default for a
new question. The switcher is therefore the **first control in the toolbar of
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

The studio is a library, not a scoped view: it lists every question grouped by
service line, with the session's line first, and offers no scope control at all.

The board's band counts are tallied from the rows in hand, not read from the
server's per-line totals, so the Priority metric always counts the Priority
companies the region filter has left on screen.

## 6. Density and alignment

Dense data is a table, never a grid of cards.

- Right-align numbers and amounts (`text-right num`), left-align text, centre
  status badges (`text-center`).
- Sortable headers only. Sorting is client-side over the fetched array, so both
  directions work; the `#` column keeps the server's fit rank whatever the sort,
  which is why its tooltip says *fit rank*.
- Paginate at 50 by default, with 100/250/All available. The board is 578 rows;
  rendering them all at once was the single largest cost on the screen.
- Truncate anything that can run long — quotes clamp at two lines, the question
  at one, company meta at one when the detail pane is open.
- Columns drop in a fixed order as width falls, so the table never squeezes:
  Themes below 1380px, Docs and Newest below 1180px, then the split gives way to
  an overlay at 1100px.

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
