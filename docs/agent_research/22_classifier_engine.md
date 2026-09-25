# Classifier Engine — DeepSeek `deepseek-flash`

Decided 2026-09-25 on the client's instruction. This supersedes the free-tier endpoint as the primary classification engine, and it removes the single hardest blocker in the plan.

## Credentials

**The key lives in `~/utils/deepcode.sh`** as `DEEPSEEK_API_KEY` (it is a DeepSeek key, `sk-…`, and that script also wires Claude Code to the same endpoint).

**The key is deliberately not written into this repository.** These documents are committed and pushed; a key in them would be a live secret in git history. Any code that needs it reads the environment variable. *If a key ever lands in a file under `docs/`, treat it as compromised and rotate it.*

## What it is

DeepSeek exposes an **OpenAI-compatible** chat endpoint, plus an Anthropic-compatible one that the wrapper script uses:

| | |
| --- | --- |
| Chat completions | `POST https://api.deepseek.com/chat/completions` |
| Anthropic-compatible | `https://api.deepseek.com/anthropic` |
| Models available (queried live) | **`deepseek-flash`**, **`deepseek-v4-pro`** |

**Model id is `deepseek-flash`.** The `deepseek-flash[1m]` string in the wrapper is a Claude Code context-window annotation, not part of the API model name — sending it to the API would be a mistake. ("v4.1 flash" in conversation maps to `deepseek-flash`; there is no literal `v4.1` in the model list.)

## Test results — the six cases from `17_rubric_experiment.md`

Same rubric, `temperature: 0`, JSON response format:

| Case | Expected | `deepseek-flash` | Confidence | Latency |
| --- | --- | --- | --- | --- |
| **KYC Team Lead – EDD/ODD** | signal | **signal** | 0.98 | 1.3s |
| Hiring an RPA Developer (UiPath) | *(contested)* | not_signal | 0.95 | 2.4s |
| "No plans to invest in automation or RPA" | not_signal | not_signal | 0.90 | 9.2s |
| "Committed to efficiency… digital transformation" | not_signal | not_signal | 0.95 | 1.9s |
| Business Development Representative | not_signal | not_signal | 0.99 | 1.2s |
| Senior Marketing Automation Manager | not_signal | not_signal | 0.99 | 1.3s |

**5/6 against the original labels — and arguably 6/6, because the label on the RPA case was wrong.** The taxonomy research (`15_signal_taxonomy.md`) independently concluded that hiring an RPA developer is the *weakest measured* buying signal, since most firms intend to run RPA internally. `deepseek-flash` calling it `not_signal` agrees with the purchase data, where the free endpoint and our original experiment did not.

**The case that matters is correct at 0.98:** a KYC leadership role is recognised as a buying signal *because the rubric explains why*, with no keyword "automation" anywhere in the text.

## Why this is the right call — it deletes a blocker

The adversarial review measured the free tier at **3,000 classifications/minute and 20,000 decisions/IP/day**, and showed the plan's own 7-of-10 rehearsal test needs **55,410 decisions — 2.8× the daily cap**. It concluded the publisher must buy a $20/month key.

Switching to DeepSeek removes that entirely: it is a paid API at normal production rate limits, with no 4-concurrent ceiling, no per-IP daily decision cap, and no spend-based batch cliff. **Blocker §5b.1 in the MVP spec is resolved by this decision** — and it also means the corpus build is no longer rationed against a daily budget.

## The cost, stated honestly

- **Self-reported confidence is not calibrated.** `deepseek-flash` returned 0.90–0.99 on everything, including cases it could reasonably have been unsure about. The free endpoint's confidence is *documented as calibrated* — answers above 0.9 were right 82% of the time, below 0.5 only 29% — and that property was doing real work in the scoring design's review gate. **An LLM's numeric self-assessment is a stylistic output, not a probability.** Do not feed it into log-odds arithmetic as if it were `P(evidence | hypothesis)`.
- **Consequence:** the 0.7 review gate from `13_scoring_design.md` needs a different calibration source. Either fit a mapping from DeepSeek's raw score to observed accuracy on the labelled set (Platt scaling on ~60 labels), or use confidence only as a coarse three-way band and be explicit that it is not a probability.
- **Latency is variable:** 1.2–2.4s typical, one case at 9.2s. Since each request carries one text, the re-score path must parallelise across requests rather than batching many texts per call — a real architectural difference from the batched free endpoint, and one that makes a local fallback model more attractive, not less.
- **It is a paid dependency.** Trivial at DeepSeek's pricing, but the "zero cost, no account" claim in the earlier research no longer applies to the primary engine.

## Recommended configuration

- **Primary:** `deepseek-flash`, `temperature: 0`, JSON response format, rubric in the system prompt.
- **Keep the free keyless endpoint as a fallback** for bulk pre-filtering where per-item cost matters and calibrated confidence is not needed — it is still the cheapest way to discard obvious non-signals before spending a real model call.
- **Keep a local model (GLiNER2, Apache-2.0, CPU) as the demo-day insurance** for the re-score path, because that path must not depend on venue Wi-Fi. See `40_mvp_spec.md` §3.
- **Re-measure the accuracy claims on DeepSeek.** Every number in `17_`–`19_` was produced against the other engine; the KYC result in particular should be re-confirmed at n≥40 before it goes on a slide.

## Measured throughput — and why the demo still needs a second engine

Each request carries **one** text (no batching, unlike the keyless endpoint's ~400). Throughput therefore depends entirely on how many requests are in flight. Measured against the live API with realistic KYC-review text:

| Concurrency | ms / item | 2,000 units | Errors |
| --- | --- | --- | --- |
| 4 | 354 | 707s | 0/8 |
| 8 | 181 | 361s | 0/16 |
| 16 | 95 | 189s | 0/32 |
| 32 | 55 | 109s | 0/64 |
| 64 | 31 | 62s | 0/128 |
| **128** | **16** | **32s** | **0/256** |

**Two conclusions, and the second is the important one:**

1. **No throttling anxiety.** Zero errors at every level up to 128 concurrent — this is a paid production API, not the free tier. Throughput scales linearly, so the engine is not the bottleneck it was before.
2. **It still cannot carry the re-score moment alone.** Even at 128 concurrent, 2,000 units take **32s** — over the demo's 25s hard cap, and that measurement is from a well-connected machine rather than a laptop on venue Wi-Fi. Reaching 25s needs roughly 200 requests in flight; reaching the 15s target needs ~350. That is a fragile thing to bet a €30,000 demo on.

**Architectural consequence — go hybrid:**

| Workload | Engine | Why |
| --- | --- | --- |
| Bulk re-score during the demo | **Batched engine** (~400 texts/request) or a **local model** | The only options fast enough at sane concurrency, and the local model is the only one immune to venue Wi-Fi |
| Extraction, disqualifier reasoning, outreach drafting | **`deepseek-flash`** | Genuinely better at judgement-per-item, and per-item cost is irrelevant at these volumes |
| Bulk pre-filtering of obvious non-signals | Either; the keyless endpoint is cheapest | Discard the noise before spending a real call |

The original plan's error was assuming one engine for everything. The replacement should not repeat it in the other direction.

## Reproduce

```sh
KEY=$(grep -oP 'sk-[A-Za-z0-9]{20,}' ~/utils/deepcode.sh | head -1)
curl -s https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $KEY" \
  -d '{"model":"deepseek-flash","temperature":0,"response_format":{"type":"json_object"},
       "messages":[{"role":"system","content":"<rubric>"},
                   {"role":"user","content":"KYC Team Lead - EDD/ODD. You will lead a team handling enhanced due diligence for onboarding.\n\nReply with JSON only: {\"label\":\"signal\"|\"not_signal\",\"confidence\":0.0-1.0}"}]}'
```
