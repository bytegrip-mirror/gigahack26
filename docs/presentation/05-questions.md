# What judges ask

Short answers, in the order they tend to come. Answer the question that was
asked, then stop — the follow-up is where the detail belongs.

---

**"So it is an LLM that tells you who to call?"**

No. A model never scores a company and never sees a company. It answers one
narrow question about one sentence — *does this say they announced an efficiency
programme with a number attached?* — and it must hand back the words it read.
The score is arithmetic over those answers, run by code you can read, with every
constant on a settings screen.

---

**"How do you stop it making things up?"**

Every quotation is checked back against the stored page. If the model returns a
sentence we cannot find in the document we gave it, we throw the answer away
rather than show it. The count of those throws is on the Reading screen in
Settings — it is meant to be non-zero, and it is meant to be small.

---

**"What happens if the internet dies on stage?"**

Nothing. The default reader is a deterministic rubric engine that matches cue
phrases and runs in-process. No key, no network, no latency. The model is a
second opinion used where it earns its cost, and every screen has a working
answer without it.

---

**"Explain the score."**

It is a log-odds sum. Each sentence we find answers a question that carries a
weight, and that weight is scaled by how trustworthy the kind of document is,
how stale it is, and how confident the reading was. The sum lands on top of a
base rate — the share of your pool worth calling at all — and one sigmoid turns
it into a probability. Then thresholds cut it into bands. The full derivation is
in [../architecture/04-scoring.md](../architecture/04-scoring.md).

---

**"Why not just ask ChatGPT with browsing?"**

Four reasons, and the first is the fatal one:

- **You cannot check it.** A name and a reason you cannot verify is not a sales
  tool, it is a confident intern.
- **It has no memory of the policy.** We author what counts as evidence, what
  disqualifies a company, and how much each fact is worth. That is the product.
- **It has no sense of decay.** A cost programme announced two years ago is not
  a reason to call today. Ours halves in value on a schedule, per question.
- **It is not reproducible.** Ask twice, get two answers. Ask our scorer twice,
  get the same number, and every term of it is inspectable.

---

**"Is the data real?"**

Two halves, and say which is which. **The companies and their documents are
real** — pages we fetched and stored, and every quotation resolves to a
character range inside a stored capture. **The people are seeded** — a demo book
with authored posts, marked as such in the data, because people-level signals
are not published in a form we could honestly crawl.

---

**"What about a company you have never read anything about?"**

It is not scored low, it is marked **unproven**, and the board counts those
separately as blind spots. A company we have not read is an unknown, not a bad
prospect, and a queue that hides that distinction is a queue you cannot trust.

---

**"Can a rep overrule it?"**

Yes, and they are meant to. They set the stage, they accept or reject a row, and
the blacklist removes a company outright with the reason shown. Those decisions
are also the only honest measure of whether the scoring works — the precision
figure comes from them, not from us marking our own homework.

---

**"What does it cost to run?"**

There is a screen for it, because we did not want to wave our hands. *Settings →
Reading* counts the pairs a full pass would need, counts how many the model
actually read, and lets you put your own price per thousand calls on the
difference. It shows what the second opinion cost you against reading everything
the expensive way.

---

**"How do you know it is any good?"**

Two numbers and one caveat. The **strike rate** — quotations the model returned
that we could not verify — and the **precision** from reps marking rows accepted
or rejected. The caveat: with a demo-sized book these are small samples, and we
would rather show you a small honest number than a large invented one.
