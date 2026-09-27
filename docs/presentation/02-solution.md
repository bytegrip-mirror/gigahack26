# Slide 2 — The solution

**Slide title:** Every claim points at the sentence it came from

**On the slide:** one screenshot — a company's score, next to the quoted
sentence from the document, with the sentence highlighted.

> We read what companies publish.
> We find the sentences that answer a question we asked.
> We score the company on those sentences — not on a model's opinion of it.
> Click any claim, and it opens the page it was read from, at the sentence.

---

## What to say

*~90 seconds. This is the core idea; the demo will prove it.*

"We built a desk that reads what companies publish, finds the sentences that
matter, and turns them into a queue — but with one rule that shapes everything
else.

**Nothing on this screen is a claim without a source.** Every reason we give you
is a sentence in a document we hold. Click it, and you land in the document at
that sentence — not at the page, not at a summary. At the sentence. If we cannot
show you where we read it, we do not say it.

That rule changes the design in three ways.

**One.** The model does not score companies. The model answers one narrow
question — *does this sentence say the company announced an efficiency
programme?* — and hands back the words it read. The scoring is arithmetic over
those answers, computed by code you can read, with every number in it a setting
you can change.

**Two.** There are two readers, and the cheap one is the default. A deterministic
rubric engine matches cue phrases and reads offline, instantly, with no key and
no network. A language model is the second opinion, used where it earns its
cost. The demo you are about to see runs on a laptop with the Wi-Fi off.

**Three.** We say what we do not know. A company we have never found a sentence
about is not scored low — it is marked *unproven*, and the board counts those
separately. A queue that hides its blind spots is a queue you cannot trust."

## If asked "so it is just keyword search?"

"It is keyword search where the keywords are authored policy, and each one is
a question with a written rubric — what counts, what does not, how much it is
worth, and how fast it goes stale. And it is a search that refuses to return an
answer it cannot quote. We will show you both engines reading the same document
in a moment."

## Transition

"Let me show you. This is the board — these are the companies this desk thinks
are worth calling for one service line, ranked."
