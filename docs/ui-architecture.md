# The interface, as it is built

A reference for the shell, the token scale and the shared components. Read this
before adding a screen, a column or an action, so the new work lands in the same
system rather than beside it.

Everything here is enforced by two gates: `pnpm run check` must report zero
errors, and every row of the board must survive a 1024×768 projector — nothing
load-bearing below 11px, nothing that only appears on hover.

---

## 1. The shell

One rail, one scroll region per pane, no document scroll. `+layout.svelte` owns
the rail and mounts the command palette; every screen below it is a `.view` that
fills the remaining width.

```
┌──────────┬────────────────────────────────────────────────────────────┐
│ rail     │ .topbar      identity · metrics · one primary action · ⋯   │
│ 232px    ├────────────────────────────────────────────────────────────┤
│          │ .toolbar     the lens: filters, search, scope              │
│ brand    ├────────────────────────────────────────────────────────────┤
│ search   │ .panes                                                     │
│ nav      │  ┌──────────────────────────┬─────────────────────────┐    │
│ corpus   │  │ .list  (scrolls)         │ .detail (scrolls)       │    │
│          │  │  the ranked table        │  the scorecard          │    │
│ region   │  └──────────────────────────┴─────────────────────────┘    │
│ refresh  ├────────────────────────────────────────────────────────────┤
│          │ .pager       1–50 of 578 · page size · prev/next           │
└──────────┴────────────────────────────────────────────────────────────┘
```

The rail reads top-down as identity, navigation, then the facts about the corpus
and the two workspace-wide settings, pushed to the bottom together so they sit in
the same place on every screen.

When a company is open, `.view` becomes two columns and the detail pane takes the
**full height of the window** — the topbar, toolbar, list and pager are the left
column and stop at the divider, rather than running over the top of the pane.
Below 1100px the pane lays over the view instead of beside it.

Rules that hold across every screen:

- **`.topbar` carries identity, the numbers that summarise the screen, and at
  most one prominent button.** Everything else is a `Menu` behind a `⋯`.
- **`.toolbar` carries filters and nothing else.** It never holds a primary
  action.
- **`.panes` is the only part that scrolls.** Its children are `min-height: 0`
  so a grid child cannot push the view taller than the viewport.
- **Selection lives in the URL** (`/?co=<id>`), so a company stays linkable.
- Below 1100px the detail pane becomes an overlay inside `.panes` rather than a
  second column, so the table keeps its width on a projector.
- `.panes` is a grid from `app.css`. A screen that wants a plain block stack
  (the pipeline) must set `display: block` itself.

## 2. The token scale

All of it is in `ui/src/app.css`. Views use the tokens; they do not invent px.

| Group | Tokens | Use |
| --- | --- | --- |
| Spacing | `--sp-1` 4 · `--sp-2` 8 · `--sp-3` 12 · `--sp-4` 16 · `--sp-5` 24 · `--sp-6` 32 | every padding, margin and gap |
| Control heights | `--ctl` 28 · `--ctl-sm` 22 · `--row` 34 · `--bar` 44 | buttons, inputs, toolbars |
| Type | `--fs-micro` 11 · `--fs-sm` 11.5 · `--fs-base` 12.5 · `--fs-md` 13.5 · `--fs-lg` 15.5 · `--fs-xl` 18 · `--fs-title` 22 | see below |
| Depth | `--z-sticky` 2 · `--z-menu` 200 · `--z-sheet` 260 · `--z-toast` 300 | never hardcode a z-index |
| Motion | `--ease`, `--ease-enter` | every transition; surface entries |

**Surfaces arrive.** Panels, sheets, popovers and toasts enter with one of four
keyframes in `app.css` — `rise`, `fade-in`, `enter-right`, `enter-left` — on one
of two durations: `--ease` (120ms) for things that attach to a control and
`--ease-enter` (160ms) for things that take the screen. A few pixels of travel,
on the same curve as every other transition, and no scaling or bounce anywhere.

Entry only. Exit animation requires keeping a component mounted while it leaves,
which is where flicker and double-render bugs live; a panel that closes instantly
is not a defect anyone notices.

Every keyframe ends on the element's default state rather than on an explicit
transform, so an animation that is skipped cannot strand anything invisible.
`prefers-reduced-motion` collapses them all to 0.01ms, verified with the media
feature emulated: the detail pane still lands at its final position, opacity 1.

Type is three roles, and a size belongs to exactly one of them:

- **`--fs-title` / `--fs-xl`** — page titles and panel headings, in
  `--font-display`.
- **`--fs-lg` / `--fs-md` / `--fs-base`** — row data, field values, body copy,
  in `--font-ui`.
- **`--fs-micro` with `--fs-sm`** — uppercase tracked labels, tags, helper
  lines. 11px is the floor; nothing load-bearing goes below it.

Numbers always carry `.num`: tabular figures so columns align.

Radii stay at 2px, hierarchy comes from rules and weight, and there are no
shadows anywhere — a popover announces itself with a full-contrast ink border
against paper, not a drop shadow.

## 3. The shared components

| Component | Contract |
| --- | --- |
| `Icon.svelte` + `icons.ts` | One 24-box stroke set, `name` from the `PATHS` map. Add a path there, never an inline `<svg>` in a view. |
| `Menu.svelte` + `menu.ts` | The overflow pattern. Takes `items: MenuItem[]` and `onselect(id)`. Renders fixed-positioned so a menu inside a scrolling pane is never clipped; closes on outside click, scroll, resize and Escape; arrow keys, Home/End. `text` renders a labelled button, otherwise an icon button. |
| `Metric.svelte` | A number above its tracked-out label, with an optional `tone` and `hint`. Use for every top-level summary — never a sentence. |
| `Th.svelte` | A sortable header cell. `id`, `label`, `sort`, `dir`, `onsort`, `align`, `width`, `className`. Renders `aria-sort` and a direction arrow that is visible without hovering. |
| `Pager.svelte` | Range, page size, prev/next. Client-side over the fetched array. |
| `CommandPalette.svelte` | ⌘K / Ctrl-K. Searches companies through the API and jumps to views, service lines and regions. Mounted once, in the layout. |
| `Tag.svelte` | The only badge. `band` renders the semantic band colours. |
| `LoadingVeil.svelte` | Absolutely positioned over its pane, so a load never reflows the layout. |

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

- Right-align numbers and amounts (`.c`, `.num`), left-align text, centre status
  badges (`.m`).
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

- **A column** — add a `Th` with an `id`, `width` and `className`, add the
  matching `td`, and add the `id` to the sort switch. Give it a hide rule in the
  same media queries as its neighbours.
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
- **A panel** — `.panel` + `.panel-head`, then `.metrics` for numbers and
  `.facts` for key/value pairs. Prose belongs behind a disclosure, not in the
  default view.
