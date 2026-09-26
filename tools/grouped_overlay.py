"""Read each document once, not once per question.

MEASURED, AND NOT THE ONE THAT SHIPS. The saving is real — 2,061 calls against
12,409, covering 46% more pairs — but comparing the two overlays on the live
corpus showed what it costs: the grouped build disqualifies 102 companies where
the per-question build disqualifies 76, and the difference is not vendors. Bank
of Cyprus is removed for recruiting security engineers, eMAG for hiring
DevSecOps, JPMorgan for building internal automation. Those are hiring signals,
which is what the question asks for, read as in-house building. Asked one
question at a time the model gets this right; asked six at once it reaches for
the disqualifier.

So this stays as the cheap option for a corpus that grows, and the per-question
builder is what ships. If it is ever switched on, the three disqualifying
questions should be asked on their own.


The per-question builder asks the model the same document's worth of text
sixteen times, because a question is the unit of configuration and the builder
took that to mean it was also the unit of work. It is not: the document does not
change between the calls, and neither does the model's reading of it. Asking
once per service line — three calls instead of seventeen — costs a fraction of
the tokens, and it lets the model reason across questions instead of answering
each in isolation, which is the difference between seventeen readers and one.

The output is the same overlay the seeder already reads, one row per pair, so
nothing downstream changes.

    python3 tools/grouped_overlay.py --dry-run
    python3 tools/grouped_overlay.py --workers=24
"""

import argparse
import concurrent.futures as futures
import datetime as dt
import json
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model_overlay as base

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
OUT = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "signals.json")
PARTIAL = OUT + ".grouped.partial"

SAMPLE = 0.05

SYSTEM = """You are a sales-signal classifier. You are given one document about one company \
and several configured buying-signal questions. For each question you decide whether this \
document is evidence about this company for that question.

Answer with JSON only, an object keyed by question key:
{"<question key>": {"match": true|false, "confidence": 0.0-1.0, "quote": "...", "verdict": "BUY|COUNTER|BUILD", "reason": "..."}, ...}
Include an entry for every question you were given.

Rules that decide the score:
- "quote" MUST be copied character-for-character from the document. It is checked by exact \
containment. If you cannot copy an exact substring, set match to false. Never paraphrase, \
never correct spelling, never join fragments from different places.
- Keep "quote" under 240 characters. Take the shortest run of sentences that carries the \
evidence; a long quote is truncated in transmission and the whole answer is then discarded.
- Keep "reason" under 20 words.
- "confidence" is your probability that a sales manager would act on this document for this \
question. Use 0.5 when the document is genuinely ambiguous, and below 0.5 when it is not \
evidence. Do not inflate.
- verdict BUY: the document shows the company needs this service from an outside provider.
- verdict COUNTER: the document shows something that argues against buying (a hiring freeze, \
budget cuts, a signed competitor contract, a pause).
- verdict BUILD is narrow: the company SELLS or provides this capability to others — a vendor, \
a consultancy or a competitor — and the document is about its own product or service. \
A company that uses, buys, hires for or runs this capability internally is NOT BUILD: an internal \
data team or AI in production is the normal state of a large company and is often exactly the \
buying signal the question asks for.
- The document is scoped to ONE company. Evidence about a different company, a customer, or a \
partner is not evidence about this company. Return match=false.
- A vendor's own marketing copy is not evidence about the vendor being a buyer.
- Answering the same document against several questions is not an invitation to find something \
for each. Most documents answer none of them, and saying so is the correct answer."""

STATS = {}
STATS_LOCK = threading.Lock()


def bump(name, n=1):
    with STATS_LOCK:
        STATS[name] = STATS.get(name, 0) + n


def group_questions(questions):
    lines = {}
    for question in questions:
        lines.setdefault(question["serviceLine"], []).append(question)
    return lines


def call_group(company, group, context):
    listing = "\n\n".join(
        "KEY: %s\nQUESTION: %s\nRUBRIC: %s" % (q["key"], q["text"], q["instructions"])
        for q in group)
    user = ("COMPANY: %s\n\nQUESTIONS:\n%s\n\nDOCUMENT:\n---\n%s\n---\n\n"
            "Answer with JSON only, keyed by question key." % (company, listing, context))
    body = json.dumps({
        "model": base.MODEL,
        "temperature": 0,
        "max_tokens": 2400,
        "stream": False,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user},
        ],
    }).encode("utf-8")

    request = urllib.request.Request(
        base.API + "/chat/completions", data=body,
        headers={"content-type": "application/json", "authorization": "Bearer " + base.KEY})
    last = None
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=240) as response:
                payload = json.loads(response.read())
            return payload["choices"][0]["message"]["content"]
        except urllib.error.HTTPError as exc:
            last = exc
            if exc.code in (429, 402, 503):
                time.sleep(1.5 * (2 ** attempt))
                continue
            raise
        except Exception as exc:
            last = exc
            time.sleep(1.0 * (2 ** attempt))
    raise RuntimeError(str(last))


def classify_group(job):
    company, group, document, context, evidence_key = job
    try:
        raw = call_group(company, group, context)
    except Exception as exc:
        bump("failed")
        return [base.rejected(evidence_key, q["key"], "call failed: %s" % str(exc)[:100])
                for q in group]
    bump("calls")

    answer = base.read_json_object(raw)
    if not isinstance(answer, dict):
        bump("unparseable")
        return [base.rejected(evidence_key, q["key"],
                              "the model's answer could not be parsed") for q in group]

    rows = []
    for question in group:
        entry = answer.get(question["key"])
        if not isinstance(entry, dict) or not entry.get("match"):
            bump("rejected")
            rows.append(base.rejected(evidence_key, question["key"],
                                      "The model read this document and found no signal"))
            continue
        try:
            confidence = float(entry.get("confidence") or 0.0)
        except (TypeError, ValueError):
            confidence = 0.0
        if confidence <= 0.5 or confidence > 1.0:
            bump("below_floor")
            rows.append(base.rejected(evidence_key, question["key"],
                                      "The model read this document and found no signal"))
            continue
        span = base.locate(document, entry.get("quote") or "")
        if span is None:
            bump("struck")
            rows.append(base.rejected(evidence_key, question["key"],
                                      "The model's quote was not found in its capture and the row was struck"))
            continue
        verdict = str(entry.get("verdict") or "").upper()
        if verdict not in ("BUY", "COUNTER", "BUILD"):
            verdict = question["verdict"]
        bump("signals")
        rows.append({"evidenceKey": evidence_key, "questionKey": question["key"], "verdict": verdict,
                     "confidence": round(confidence, 4), "quote": span,
                     "rationale": str(entry.get("reason") or "")[:200]})
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--workers", type=int, default=24)
    parser.add_argument("--sample", type=float, default=SAMPLE)
    parser.add_argument("--policies-url", default="https://hackapi.pistonpunk.com")
    parser.add_argument("--seed", type=int, default=20260926)
    args = parser.parse_args()

    if not base.KEY and not args.dry_run:
        print("DEEPSEEK_API_KEY is not set", file=sys.stderr)
        return 1

    with open(RAW, encoding="utf-8") as handle:
        corpus = json.load(handle)
    names = {c["key"]: c["name"] for c in corpus["companies"]}
    lines = group_questions(base.load_questions(args.policies_url))

    rng = random.Random(args.seed)
    jobs = []
    for item in corpus["evidence"]:
        document = item.get("text") or ""
        if not document.strip():
            continue
        company = names.get(item.get("companyKey"), "")
        for service_line, group in lines.items():
            contexts = [base.relevant_windows(document, q["patterns"]) for q in group]
            hit = [c for c in contexts if c is not None]
            if hit:
                context = max(hit, key=len)[:base.MAX_DOCUMENT_CHARS]
                bump("groups_live")
            elif rng.random() < args.sample:
                context = document[:base.MAX_DOCUMENT_CHARS]
                bump("groups_sampled")
            else:
                continue
            jobs.append((company, group, document, context, item["key"]))

    pairs = sum(len(job[1]) for job in jobs)
    print("documents        %d" % len(corpus["evidence"]))
    print("service lines    %d" % len(lines))
    print("model calls      %d" % len(jobs))
    print("pairs covered    %d" % pairs)
    print("per-question would be %d calls" % pairs)
    if args.dry_run:
        return 0

    done = {}
    if os.path.exists(PARTIAL):
        try:
            with open(PARTIAL, encoding="utf-8") as handle:
                for row in json.load(handle).get("signals", []):
                    done[(row["evidenceKey"], row["questionKey"])] = row
            print("resuming with      %d rows already answered" % len(done))
        except (OSError, ValueError):
            done = {}

    answered = {(k[0], k[1]) for k in done}
    todo = [job for job in jobs
            if any((job[4], q["key"]) not in answered for q in job[1])]
    print("groups to do     %d" % len(todo), flush=True)

    started = time.time()
    results = list(done.values())
    lock = threading.Lock()

    def flush():
        payload = {"engine": "%s@deepseek" % base.MODEL,
                   "generatedAt": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                   "pairsSeen": len(results), "signals": results}
        with open(PARTIAL, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=1)

    with futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for index, rows in enumerate(pool.map(classify_group, todo), 1):
            with lock:
                results.extend(rows)
                if index % 100 == 0:
                    rate = index / max(0.001, time.time() - started)
                    print("[%d/%d] %d rows  %.1f calls/s  eta %.0f min"
                          % (index, len(todo), len(results), rate,
                             (len(todo) - index) / max(0.001, rate) / 60), flush=True)
                    flush()

    flush()
    kept = [row for row in results if not row.get("_failed")]
    payload = {"engine": "%s@deepseek" % base.MODEL,
               "generatedAt": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
               "pairsSeen": len(kept),
               "strikeRate": round(STATS.get("struck", 0) / max(1, STATS.get("calls", 1)), 4),
               "grouped": {"modelCalls": STATS.get("calls", 0), "groupsLive": STATS.get("groups_live", 0),
                           "groupsSampled": STATS.get("groups_sampled", 0),
                           "perQuestionEquivalent": pairs},
               "signals": kept}
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
    if os.path.exists(PARTIAL):
        os.remove(PARTIAL)
    print("[stats] %s" % json.dumps(STATS, indent=1, sort_keys=True))
    print("[out] %s, %d rows, %d signals in %d calls"
          % (OUT, len(kept), STATS.get("signals", 0), STATS.get("calls", 0)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
