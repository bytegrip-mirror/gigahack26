# The pitch

Four slides, about eight minutes with the demo. Each file in this folder is one
slide: what is on the screen, and what to say over it.

| # | File | Slide | Time |
|---|---|---|---|
| 1 | [01-problem.md](01-problem.md) | The problem | 1:00 |
| 2 | [02-solution.md](02-solution.md) | The solution | 1:30 |
| 3 | [03-demo.md](03-demo.md) | The demo — live, no slide | 6:00 |
| 4 | [04-ai.md](04-ai.md) | How the AI decides | 2:30 |
| — | [05-questions.md](05-questions.md) | Answers to what judges ask | — |

## The one idea

If the audience remembers one sentence, it should be this:

> **Every claim on the screen points at the sentence it was read from.**

Everything else in the deck is a consequence of it. The model does not score
companies — it reads sentences and proves which ones. The score is arithmetic
over those sentences, run by code with every constant on a settings screen. The
cheap reader is the default and works offline; the model is the second opinion.

## How to hold the room

- **Say "no" before they ask.** The obvious objection — *is this just an LLM
  making things up?* — is the first thing a technical judge thinks. Answer it in
  slide 2, before the demo, so the demo is taken as proof rather than defence.
- **Show the sentence, not the score.** The score is a number; anyone can print
  a number. The click from a claim to the highlighted sentence is the product.
- **Name the limits yourself.** The blind-spots count, the unproven band, the
  dropped quotations. A demo that admits what it cannot do is a demo people
  believe about what it can.
- **Do not explain the maths on the slide.** It is in
  [../architecture/04-scoring.md](../architecture/04-scoring.md), and it is
  there for the judge who asks, not for the room.

## What is real and what is not

Worth knowing cold, because a judge will ask and being caught is fatal:

- **The companies are real**, and the documents are real pages we fetched and
  stored. Every quotation resolves to a character range in a held document.
- **The people are seeded.** They are a demo book with authored blogs and posts,
  marked as such in the data. Do not let anyone think they were scraped.
- **The scores are computed**, live, from those documents, by the code in
  `api/src/main/java/.../service/ScoringService.java`.
