# Slide 3 — The demo

**Slide title:** *blank — this slide is the live product*

**On the slide:** nothing. No screenshot, no bullet points. The screen is the
demo. If the projector fails, this is the slide you lose least by skipping.

**Timing:** 4 minutes of clicking, 2 minutes of the model comparison.

---

## Before you start

- Six service lines are loaded. **Intelligent Automation** is the default and
  the one to open on — it has the most evidence behind it.
- The board is not filtered. Do not open on a filter you have to explain.
- Have the deep link to one company ready. The best one changes as data
  changes; check it the morning of, and pick a company whose top reason is a
  *sentence you would want to read out loud.*

## 1. The board — 45 seconds

Open **Companies**.

> "This is the queue. Four hundred companies this desk is watching for
> intelligent automation, ranked by how strong the evidence is. Not by revenue,
> not by who we know — by what they have published."

Point at one row and read it left to right:

> "The company, the score, the stage a rep has it at, the signals we found —
> two of them, with a count of the rest — and the shape of when they arrived.
> That line at the bottom is the last year; the bump is where the news landed."

Then the picker, top left:

> "Six service lines, and each one is its own queue with its own evidence. Same
> four hundred companies, six different answers to *is this worth a call*."

## 2. Open one company — 60 seconds

Click the company name. The pane opens on the right.

> "This is the whole case. The score, the themes behind it, and here — the
> reasons we would call, and the reasons we would not."

Then the graph:

> "Each point is a signal, plotted when we read it and how much it was worth.
> The line is the year those arrived in."

## 3. The click that proves it — 45 seconds

**This is the moment.** Click one of the reasons under *Reasons to call*.

The document pane opens with the sentence highlighted.

> "That is the claim. This is the document we read it from — the actual page as
> we fetched it — and that is the sentence, highlighted, with the character
> range printed underneath."

Scroll a little to show it is a real document, not an excerpt we wrote.

> "Every number in that score came out of sentences like this one. Nothing on
> that board was written by a model about a company. It was read out of a page."

## 4. Where the model is, and is not — 60 seconds

Still in the pane:

> "One sentence in this document is why this company is on the board. Ask
> yourself how a model was supposed to find it — and it did, and then it had to
> prove it. A quotation we cannot find again in the stored page is dropped
> rather than shown. You can see that count in Settings."

Go to **Settings → Reading**, scroll to *Model pass*:

> "Every quote the model returned, checked against the page it claimed to come
> from. Anything that did not match was thrown away. This is the number that
> would be non-zero if we were trusting the model."

## 5. The offline claim — 30 seconds

Go back to **Companies**, and press **Re-score** (the toolbar action).

> "That pass runs on the rubric engine — no API key, no network, on this
> laptop. We can turn the Wi-Fi off and nothing here changes. The model is the
> second opinion, not the floor."

## 6. If there is time — the working end — 30 seconds

**Settings → Scoring**: drag the Priority line down; the board's bands move.
**Settings → Qualification**: the competitor gate and the blacklist.

> "Every number in the score is a setting on a screen. Nothing is hard-coded,
> and nothing needs a deploy to change."

## Things that will go wrong

| If | Do |
|---|---|
| A page is slow | Keep talking about the row you already opened. Do not reload. |
| The graph looks sparse | Say so: "this company has been quiet — that is the point." |
| Someone asks for a company we do not hold | "We hold what we crawled. It is on the blind-spots count, not hidden." |
| The model is unreachable | Nothing breaks — the rubric engine answers. Say that; it is the design. |
