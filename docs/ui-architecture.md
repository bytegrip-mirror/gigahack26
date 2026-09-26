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

The interface uses Tailwind v4 for utilities and bits-ui for behaviour. The
design system has three parts, and a gate holds each part in place.

1. **The theme.** `ui/src/app.css` defines every colour, space, type size, radius,
   font, breakpoint and z-index in one `@theme` block. The block starts with
   `--*: initial`, so the default Tailwind scale does not exist. `p-7`,
   `text-gray-500` and `rounded-lg` produce no CSS. Only the tokens below produce
   utilities.
2. **The primitives.** `ui/src/lib/ui/` holds every control and every piece of the
   shell. Only these files import `bits-ui`. Only these files decide padding,
   colour, border and type for a control. A caller selects a look with a
   `variant`, `size` or `tone` prop.
3. **The gate.** `pnpm run check` runs `ui/scripts/design-lint.mjs` after
   `svelte-check`. The lint fails the build on a violation of the rules in
   section 2.2.

### 2.1 Tokens

| Group | Utilities | Values |
| --- | --- | --- |
| Spacing | `p-*`, `m-*`, `gap-*`, `inset-*` … | `0`, `px`, `0.5` 2, `1` 4, `2` 8, `3` 12, `4` 16, `5` 24, `6` 32 |
| Sizes | `h-*`, `w-*`, `size-*`, `min-h-*` … | `ctl-sm` 22, `ctl` 28, `row` 34, `bar` 44, `topbar` 56, `rail` 232 |
| Max widths | `max-w-*` | `prose` 60ch, `menu` 320, `palette` 640, `sheet` 720, `dialog` 1080 |
| Colour | `text-*`, `bg-*`, `border-*` … | `orange`, `orange-deep`, `orange-ink`, `orange-wash`, `orange-line`, `on-orange`, `accent`, `accent-wash`, `accent-line`, `oxblood`, `oxblood-wash`, `oxblood-line`, `ink`, `ink-2`, `ink-3`, `ink-4`, `ink-line`, `ink-hover`, `paper`, `surface`, `sunken`, `rule`, `rule-strong`, `scrim`, `veil` |
| Type size | `text-*` | `micro` 11, `sm` 11.5, `base` 12.5, `md` 13.5, `lg` 15.5, `xl` 18, `title` 22 |
| Family | `font-*` | `display` (Space Grotesk), `ui` (Archivo) |
| Leading | `leading-*` | `none` 1, `tight` 1.1, `snug` 1.3, `normal` 1.45, `relaxed` 1.6 |
| Tracking | `tracking-*` | `tight`, `num`, `normal`, `wide`, `caps` |
| Radius | `rounded-*` | `sm` 2px, `none`, `full` (dots only) |
| Depth | `z-*` | `sticky`, `raised`, `overlay`, `menu`, `sheet`, `toast` |
| Breakpoint | `sm:`, `max-lg:` … | `sm` 760, `compact` 1000, `md` 1100, `lg` 1180, `xl` 1380 |

Text styles are utilities too: `type-display`, `type-label` (uppercase tracked
label), `type-sub` (helper line), `type-key` and `num` (tabular figures). Layout
helpers are `scroll`, `grid-fields`, `grid-kv` and `table-grid`.

Type has three roles, and a size belongs to exactly one role:

- **`title` and `xl`** are for page titles and panel headings, in `font-display`.
- **`lg`, `md` and `base`** are for row data, field values and body copy.
- **`micro` and `sm`** are for labels, tags and helper lines. 11px is the floor.
  Nothing load-bearing goes below it.

Numbers always carry `num`, so that columns align. Radii stay at 2px. There are
no shadows and no gradients. A popover shows its edge with a full-contrast ink
border against paper.

### 2.2 What the gate rejects

Outside `ui/src/lib/ui/`:

- A `<style>` block.
- A `style="…"` attribute. A `style:` directive is allowed only for geometry
  (`width`, `height`, `left`, `top`, `transform`, grid tracks) with a computed
  value, for example a percentage bar.
- An arbitrary value, that is, any class with `[`, `]`, `(` or `)`.
- A raw z-index (`z-10`), a colour opacity modifier (`bg-orange/50`), or a raw hex
  or rgb colour in markup.
- A class that the theme does not define.
- A class built at run time (`` `tag-${tone}` ``, a variable). Class values must
  be literal strings, literal ternaries or literal arrays.
- A class on a component that is not a layout class. A component takes display,
  placement, width, height, position, overflow and depth from its caller. It never
  takes padding, margin, colour, font, border or radius. If a component needs a
  new look, add a variant to it in `lib/ui`.
- An import from `bits-ui`.

Inside `ui/src/lib/ui/`, the theme and arbitrary-value rules still apply. The only
exceptions are the bits-ui state selectors (`data-[state=open]:`) and the bits-ui
size variables (`max-h-(--bits-select-content-available-height)`).

The rules separate two kinds of edit. A view can arrange things: grid, flex,
width, order, what hides at which breakpoint. A view cannot restyle things: that
change goes into the primitive, where every screen gets it.

## 3. The shared components

All of them are in `ui/src/lib/ui/`. Read the file for the exact props.

| Component | Contract |
| --- | --- |
| `View`, `Topbar`, `Toolbar`, `ToolbarSeparator`, `Panes`, `Bulkbar` | The shell from section 1. `Topbar` takes a `title` and the snippets `subtitle`, `metrics` and `tools`. |
| `Button` | `variant`: `default`, `primary`, `ink`, `quiet`, `inverse`. `size`: `md`, `sm`. Optional `icon`, `trailing` and `href`. |
| `IconButton` | An icon-only button. `label` is required and becomes the accessible name and the tooltip. |
| `Input`, `Textarea` | Text fields with `bind:value`. |
| `Select` | A bits-ui listbox. Takes `options: {value, label}[]` and `bind:value`. The value can be a string or a number. |
| `Checkbox` | A bits-ui checkbox. Its children are the label. Without children it needs `label`. |
| `Segmented` | A bits-ui single toggle group, for a filter with a few options and optional counts. |
| `Tabs` | A bits-ui tab strip with a `trailing` snippet and one panel for each tab. |
| `Menu` + `menu.ts` | The overflow pattern on the bits-ui dropdown menu. Takes `items: MenuItem[]` and `onselect(id)`. `text` renders a labelled button, otherwise an icon button. `searchable` adds a filter on the bits-ui command list. |
| `Dialog` | The bits-ui dialog. `kind`: `dialog`, `sheet` (from the right), `palette`. Handles Escape, focus and outside click. |
| `Icon` + `icons.ts` | One 24-box stroke set. `size`: `sm` 13, `md` 15, `lg` 17, `xl` 22. Add a path to `PATHS`, never an inline `<svg>` in a view. |
| `Metric` | A number above its tracked label, with an optional `tone` and `hint`. Use it for every top-level summary, never a sentence. |
| `Tag` | The only badge. `tone` selects the colour. `band` renders the semantic band colours. |
| `Th` | A sortable header cell. Renders `aria-sort` and a direction arrow that shows without hover. `class` takes the column's hide rule. |
| `Pager` | Range, page size, prev and next, over the fetched array. |
| `Panel`, `Empty`, `Note`, `Facts`, `Field`, `Fields` | Content blocks: a titled section, an empty state, a status line, key/value pairs, a labelled control, a field grid. |
| `Spinner`, `LoadingVeil` | `LoadingVeil` sits over its pane, so a load never moves the layout. |

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
