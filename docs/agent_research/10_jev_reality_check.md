# Jev / TypeSafe AI — Reality Check

**Research date:** 2026-09-25. All live tests run from this machine on this date.

---

## VERDICT

**Jev exists. It is real, it is live, it is free to use right now, and it does what the hypothesis says it does. The plan's central technical claim survives.**

This is not a hopeful verdict — it is backed by a live API probe and 30+ classification calls I ran myself against the production endpoint, plus consistent corroboration across BusinessWire, SiliconANGLE, heise.de, CIO.com, the Financial Times, LiteLLM's engineering blog and Zapier.

Three corrections the plan needs before it is pitched to judges:

1. **"40–400x cheaper and faster" is not defensible as stated.** That is vendor marketing restated. Measured independent numbers are **5.4x faster and 96% cheaper** than Claude Haiku 4.5 (LiteLLM, n=240). One TypeSafe engineer's own pipeline sped up only **15.9% end-to-end** when a step was swapped to Jev. Say "5–25x faster, ~96–99% cheaper than an LLM on the classifier step" and the claim becomes unattackable.
2. **Jev is not more *accurate* than an LLM across the board.** It lost to Haiku 4.5 on phishing detection (62.6% vs 81.3%). Its actual, defensible superpower is **calibrated confidence** — its confidence separates right from wrong answers (gap 0.16–0.49) where Claude's is flat (gap 0.00–0.07). That is the honest and *stronger* pitch, because the challenge scores "low false-positive rate," not raw accuracy.
3. **The free tier caps at 4 concurrent requests per IP.** In my burst test, **8 of 12 concurrent calls returned HTTP 429**. Unhandled, this breaks a live demo the moment the pipeline fans out. Fix is trivial (semaphore of 4, or a funded key) but it must be in the code.

**Recommendation: keep Jev. Use `classifier.dev` as the primary path — zero auth, zero cost, zero signup — and pass an explicit plain-language `instructions` rubric per signal question.** I verified the rubric field single-handedly fixes both the negation failure and the corporate-boilerplate false positive (details in §3).

---

## 1. Does "Jev" by "TypeSafe AI" exist? — YES

### Live proof (strongest evidence)

`api.typesafe.ai` resolves and answers. An unauthenticated POST returns a well-formed provider error, which is only possible if the service is genuinely running:

```
$ curl -X POST https://api.typesafe.ai/v1/systemone -d '{...}'
http_code=403 time=0.58s
{"detail":{"error_type":"authentication_error",
 "message":"Must supply an API key! Check your request and try again."}}
```

DNS: `api.typesafe.ai -> 44.227.31.201`, `typesafe.ai -> 31.43.160.6`.

And the free public front-end is fully working — this is a real classification response, not a mock:

```json
{ "label": "not relevant", "confidence": 0.95,
  "scores": {"needs process automation": 0.03, "not relevant": 0.97},
  "ms": 122, "model": "jev-1.13.0", "tier": "fast" }
```

### What it actually is

| Property | Finding | Source |
|---|---|---|
| Vendor | TypeSafe AI, San Francisco, founded 2024 | [SiliconANGLE](https://siliconangle.com/2026/09/16/typesafe-ai-exits-stealth-with-40m-to-build-ai-for-use-by-software/) |
| Founder | Diogo Almeida, ex-OpenAI, RLHF/InstructGPT co-author | [Economic Times](https://enterpriseai.economictimes.indiatimes.com/news/industry/former-openai-researcher-launches-jev-for-faster-ai-decision-making/134349979) |
| Funding | $40M seed led by DCVC, emerged from stealth 2026-09-15 | [BusinessWire](https://www.businesswirenews.com/news/home/20260915525333/en/TypeSafe-AI-Emerges-From-Stealth-With-%2440M-in-Funding-With-New-Model-for-Composable-AI) (403s to bots; corroborated) |
| Product | Jev, first "System One" model, GA 2026-09-21 | [36Kr](https://eu.36kr.com/en/p/3992394169613316) |
| Output primitives | `Noul` (yes/no 0–1), `Choice` (up to 255 options), `Score` (rubric scale) — each with `confidence` and `probabilities` | [docs.typesafe.ai](https://docs.typesafe.ai/) |
| Pricing | $0.042/M input tokens, output free (~$0.000081/decision) | [typesafe.ai](https://www.typesafe.ai/) |
| Free tier | $5 credit ≈ 120M tokens, instant signup, no waitlist | [explainx.ai](https://www.explainx.ai/blog/jev-general-availability-no-waitlist-2026) |
| Integrations | Vercel AI Gateway, Cloudflare Workers AI, LangChain, Pydantic AI, LiteLLM | [liteLLM](https://docs.litellm.ai/blog/jev-auto-router-benchmark) |

**Answer to the "real, rebranded, or invented?" question: real and recent.** It is a genuine product from a funded company, launched 10 days ago. It is not obscure-by-obscurity — it had 140,000 beta signups and ~13% of Vercel's paid AI Gateway teams tried it within 24 hours. But it is also *ten days old*, which is the actual risk (§4).

**One naming correction for the pitch:** the hypothesis calls it "a specialized System One AI model." TypeSafe's own term is exactly "System One model" — that checks out. But calling it a "classifier" is a simplification TypeSafe resists; the fair framing is "a decision model that is usable as a zero-shot classifier." Judges may know this. `classifier.dev` does expose it as a plain classifier, so the framing is defensible.

---

## 2. Ranked alternatives for September 2026

Ranked for *this* constraint set: deployable in 48 hours, no fine-tuning run, no GPU cluster, per-item confidence, configurable labels.

| # | Option | Cost | Latency | Calibrated confidence? | Training data? | Honest failure mode |
|---|---|---|---|---|---|---|
| **1** | **classifier.dev (Jev, free)** | **$0** — no key, no account | **0.44–0.50s warm**, 1.1s cold | Yes — real per-label `scores` | None, plain-language labels | 6 weeks old, solo-maintained, no SLA; **4 concurrent/IP cap** |
| **2** | **TypeSafe Jev direct API** | $0.042/M in, output free; $5 free credit | 70–500ms vendor; **925–1068ms measured** for 5-question calls | Yes — the whole design goal | None | Must sign up; key needed; billing header required past credit |
| **3** | **Jev via Vercel AI Gateway / Cloudflare Workers AI** | Same token price, unified billing | Same as direct | Yes | None | Extra platform dependency; 32K context on Cloudflare |
| **4** | **GLiNER2** (`pip install gliner2`) | Free, self-hosted | CPU-first, ~205M params, no GPU | `include_confidence=True` | None — schema/labels at runtime | No published accuracy/speed numbers; new-ish; label wording sensitivity |
| **5** | **Embeddings + logistic regression** (bge-m3 + sklearn) | ~$0 (local) | ms after one-time encode | Yes — Platt scaling or isotonic regression are one line each | ~50–200 labels (minutes to write) | Not zero-shot; needs seed labels; drifts as taxonomy changes |
| **6** | **SetFit** (`pip install setfit`) | Free, self-hosted | Minutes to train on CPU | Yes, via sklearn head | **8–16 examples per class** | Needs those examples; weakest when classes are vague/overlapping |
| **7** | **NLI zero-shot** (bart-large-mnli) | Free, local | ~100ms CPU | Softmax score, not calibrated | None | **Collapses under label paraphrase** — 26–48% accuracy in adversarial shuffle vs Jev's 100% |
| **8** | **Frontier LLM + logprobs** | $0.15–$3/M in | 1–9s | **No.** Badly overconfident | None | Structured decoding *worsens* it — GPT-4.1-mini outputs 95–100% even when wrong |
| **9** | **fastText / TF-IDF + LR** | Free | microseconds | Yes, with calibration | Needs labeled data | Not zero-shot at all; no semantics — pure lexical |
| **10** | ~~Cohere Classify~~ | — | — | — | — | **DEAD.** `/v1/classify` deprecated 2025-01-31, fine-tuning retired 2025. Do not build on it |

### Notes on the ranking

**Why Jev is #1 despite losing on raw accuracy.** The challenge weights "Signal Relevance & Accuracy (25%): low false-positive rate." A model whose confidence *tells you when it is unsure* is worth more than a more accurate model that fails confidently. The YidiDev benchmark measured this directly: Jev's correct-vs-incorrect confidence gap was **0.16–0.49** across three unrelated tasks; Claude Sonnet 5's was **0.01–0.07** and Haiku 4.5's **0.00–0.05**, described as "consistently useless, 3/3 tasks." A rule routing sub-0.6 confidence to humans catches most Jev errors and almost no Claude errors. That is a *demonstrable* claim you can show a judge live.

**The cost story is real but smaller than claimed.** Metered totals from a 15,000-call benchmark: Jev **$0.75**, Haiku 4.5 **$29.67**, Sonnet 5 **$68.10**. LiteLLM measured 96.1% cheaper and 5.43x faster at p50 (138ms vs 721ms) on 240 calls. Not 400x — but 96% cheaper is still a compelling sentence.

**Options 4–7 are your fallback tier, not your primary.** GLiNER2 is the best of them (Apache-2.0, CPU-first, has `classify_text` with confidence, and also does span extraction for company/role/tech-stack fields the challenge explicitly asks for). Keep one of these wired behind a feature flag if `classifier.dev` dies mid-demo.

**Option 8 is a trap.** "Just use GPT/Claude with logprobs" is the intuitive fallback and it is the *worst* option for a false-positive-sensitive task, because constraining output to an enum concentrates probability mass on the chosen token and destroys the uncertainty signal.

---

## 3. What actually breaks the story in a live demo — measured, with counters

Every row below is from a live call I ran today, not from documentation.

| Failure mode | Measured behaviour | Counter (verified) |
|---|---|---|
| **Concurrency cap** | 12 concurrent calls → **4 succeeded, 8× HTTP 429** in 0.96s | Cap client concurrency at 4 per IP; or use a funded key; add retry-with-backoff on 429 |
| **Cold start** | First call **1.12s**; warm median **0.45s** | Warm the connection on app boot with one throwaway call; never let the first demo click pay it |
| **Negation blindness** | "We have **not** invested in automation and have **no plans** to do so" → flagged **needs process automation, score 0.83** | **Rubric fixes it: 0.83 → 0.00.** Verified |
| **Generic corporate boilerplate** | "committed to efficiency and operational excellence" → automation **0.39**, confidence only 0.61 — one bad threshold away from a false positive | **Rubric fixes it: 0.39 → 0.01.** Verified |
| **Bare short snippets** | "Join our Center of Excellence as a UiPath Automation Engineer" → only **0.70 / conf 0.41** (a false *negative* on a true signal) | Feed the job description plus company context, not the title alone |
| **Long context dilution** | 1,931-char article mentioning automation → conf dropped to **0.69** | Chunk and pre-filter; Jev's own docs recommend this |
| **Noisy HTML** | Tags, `&nbsp;`, nav cruft → correctly found the signal at **conf 0.94** | None needed. This one is genuinely good news |
| **Vendor-surveyed "40–400x"** | Independent: 5.43x, 96% cheaper; one pipeline only 15.9% faster end-to-end | Restate as measured, or a knowledgeable judge will discount the whole deck |
| **Vendor lock / 10-day-old dependency** | No SLA; the free front-end is 6 weeks old | Fallback: GLiNER2 local, or embeddings+LR — both ship in under an hour |

### The single highest-leverage finding

The `instructions` field is not a prompt-engineering nicety — it is the mechanism that makes the false-positive requirement achievable. Passing a plain-language rubric:

> *"Flag ONLY concrete, current automation initiatives, budgets, hires or tenders. Statements that the company has NOT invested, or has no plans, must be 'not relevant'."*

flipped the negation case from **0.83 → 0.00** and the boilerplate case from **0.39 → 0.01**, both to full confidence. This maps *exactly* onto the challenge's "configurable signal questions" and "negative signals or disqualification rules" requirements — the rubric IS the product feature. Put this on screen during the demo; it is the most persuasive 10 seconds available.

---

## 4. What I could not verify

- **I did not obtain a TypeSafe API key.** I confirmed `api.typesafe.ai` is live (proper 403 auth error) but never made an authenticated call. All functional testing went through `classifier.dev`.
- **Whether the free tiers survive the hackathon weekend.** $5 / 120M tokens and classifier.dev's free tier are both promotional. If they are pulled Saturday, the demo needs the fallback ready.
- **The provenance of the 193.6x / 444.6x figures.** typesafe.ai footnotes them as "based on workflows for System One tasks" with a link to their own comparison page. I could not find the underlying methodology or an independent replication.
- **True ECE for Jev.** classifier.dev publishes accuracy-by-confidence-bucket (91.6% at [0.9,1.0] on AG News, vs 68% for the prior model's logprob confidence) but no ECE number. sysone-bench separately notes `score` questions are worse calibrated than `choice`/`noul` (ECE near 0.3) — **prefer Choice and Noul primitives over Score for anything threshold-driven.**
- **Independent accuracy on *B2B sales-signal* language specifically.** My ~30 test calls are directional, not statistical. The closest public evidence is banking77-style intent routing, where Jev scored 0.906 vs Laya's 0.802.
- **classifier.dev's operational reliability.** Six weeks old, effectively solo-maintained, no published SLA. Risky as a demo dependency; the retry/fallback path is not optional.
- **Whether the 4-concurrent limit is per-IP or per-account on funded keys.** Docs say free tier is per-IP; pro workspaces get 10x allowances, which implies funded keys lift it, but I did not confirm.

---

## 5. Changes to make to the hypothesis document

| Current text | Replace with |
|---|---|
| "40 to 400 times cheaper and faster than frontier generative LLMs" | "Measured 5.4x faster and 96% cheaper than Claude Haiku 4.5 on the classifier step; vendor-published figures reach 194x/445x on curated tasks" |
| "Calibrated Confidence: Jev outputs a calibrated probability score" | Keep — this is true and is the strongest claim. Add the evidence: its confidence separates correct from incorrect answers where Claude's does not |
| "Zero-Shot Dynamic Taxonomy ... without requiring any model fine-tuning" | Keep — verified directly. Add that a plain-language `instructions` rubric per question is what suppresses false positives |
| "Only the structured, highly qualified signals that pass the triage layer are handed off to a traditional LLM" | Keep — this two-tier cascade is correct and is independently endorsed (a Jev-first cascade with escalation below 0.8 confidence matched a frontier model at 26–28% of cost) |
| No mention of concurrency | Add: client-side concurrency capped at 4, retry on 429, cold-start warm-up on boot |
| No fallback named | Add: GLiNER2 (`pip install gliner2`, Apache-2.0, CPU) or embeddings + logistic regression behind a feature flag |

---

## Sources

- https://www.typesafe.ai/
- https://docs.typesafe.ai/
- https://docs.litellm.ai/blog/jev-auto-router-benchmark
- https://greennode.ai/blog/what-is-jev
- https://github.com/instax-dutta/sysone-bench
- https://github.com/YidiDev/jev-benchmark
- https://classifier.dev/docs
- https://classifier.dev/benchmark
- https://siliconangle.com/2026/09/16/typesafe-ai-exits-stealth-with-40m-to-build-ai-for-use-by-software/
- https://enterpriseai.economictimes.indiatimes.com/news/industry/former-openai-researcher-launches-jev-for-faster-ai-decision-making/134349979
- https://eu.36kr.com/en/p/3992394169613316
- https://www.explainx.ai/blog/jev-general-availability-no-waitlist-2026
- https://www.heise.de/en/news/AI-model-Jev-to-make-machines-decide-faster-11457071.html
- https://pypi.org/project/gliner2/
- https://docs.cohere.com/docs/deprecations
- https://github.com/gepa-ai/gepa/blob/main/docs/docs/guides/confidence-adapter.md
