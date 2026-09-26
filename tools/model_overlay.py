"""Build the language-model signal overlay.

The local rubric engine is a cue matcher: it is the recall layer that keeps the
board populated offline. This script spends model calls where they change the
answer — on every document that shares vocabulary with a question, plus a random
sample of the ones that do not, so the miss rate is measurable rather than
assumed — and writes the verdicts to corpus/signals.json in the overlay format
CorpusSeeder already reads.

Every quote is re-located in its own capture before it is written, using the same
containment-then-sentence-trim rule SpanLocator applies, so a row this script
emits is a row the seeder will accept rather than strike.

    python3 tools/model_overlay.py --dry-run
    python3 tools/model_overlay.py --sample=0.05 --workers=24
"""

import argparse
import concurrent.futures as futures
import datetime as dt
import json
import os
import random
import re
import sys
import threading
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
OUT = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "signals.json")
PARTIAL = OUT + ".partial"

API = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
KEY = os.environ.get("DEEPSEEK_API_KEY", "")
MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-flash")

MAX_DOCUMENT_CHARS = 9000
WINDOW_CHARS = 1600
WINDOW_STRIDE = 1200
MAX_CONTEXT_WINDOWS = 4

SYSTEM = """You are a sales-signal classifier. You decide whether one document is evidence \
about one company for one configured buying-signal question.

You must answer with JSON only:
{"match": true|false, "confidence": 0.0-1.0, "quote": "...", "verdict": "BUY|COUNTER|BUILD", "reason": "..."}

Rules that decide the score:
- "quote" MUST be copied character-for-character from the document. It is checked by exact \
containment. If you cannot copy an exact substring, return match=false. Never paraphrase, \
never correct spelling, never join fragments from different places.
- Keep "quote" under 240 characters. Take the shortest run of sentences that carries the \
evidence; a long quote is truncated in transmission and the whole answer is then discarded.
- Keep "reason" under 20 words. Answer with the JSON object and nothing after it.
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
- The question is scoped to ONE company. Evidence about a different company, a customer, or a \
partner is not evidence about this company. Return match=false.
- A vendor's own marketing copy is not evidence about the vendor being a buyer."""

STATS = {}
STATS_LOCK = threading.Lock()


def bump(name, n=1):
    with STATS_LOCK:
        STATS[name] = STATS.get(name, 0) + n


def normalize(text):
    return re.sub(r"\s+", " ", text).strip()


def windows_of(document):
    out = []
    for start in range(0, max(1, len(document)), WINDOW_STRIDE):
        chunk = document[start:start + WINDOW_CHARS]
        if len(chunk.strip()) < 40:
            continue
        out.append((start, chunk))
        if start + WINDOW_CHARS >= len(document):
            break
    return out


def cue_regex(cue):
    return re.compile(r"(?<![\w])" + re.escape(normalize(cue)) + r"(?![\w])", re.I)


def relevant_windows(document, cues):
    """The passages a cue list points at, with one window of breathing room.

    A question that shares no vocabulary with a document is not a candidate; the
    model is spent where the cheap engine would have looked anyway.
    """
    prepared = windows_of(document)
    hit = set()
    for index, (_, chunk) in enumerate(prepared):
        for pattern in cues:
            if pattern.search(chunk):
                hit.add(index)
                break
    if not hit:
        return None
    expanded = set()
    for index in hit:
        for neighbour in (index - 1, index, index + 1):
            if 0 <= neighbour < len(prepared):
                expanded.add(neighbour)
    chosen = sorted(expanded)[:MAX_CONTEXT_WINDOWS]
    return "\n\n".join(prepared[i][1] for i in chosen)[:MAX_DOCUMENT_CHARS]


def call_model(company, question, document):
    user = (
        "COMPANY: %s\n\nQUESTION: %s\n\nRUBRIC (this defines what counts and what does not):\n%s\n\n"
        "DOCUMENT:\n---\n%s\n---\n\nAnswer with JSON only."
        % (company, question["text"], question["instructions"], document)
    )
    body = json.dumps({
        "model": MODEL,
        "temperature": 0,
        "max_tokens": 1400,
        "stream": False,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user},
        ],
    }).encode("utf-8")

    request = urllib.request.Request(
        API + "/chat/completions", data=body,
        headers={"content-type": "application/json", "authorization": "Bearer " + KEY})
    last = None
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
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


def read_json_object(raw):
    raw = raw.strip()
    if raw.startswith("```"):
        first = raw.find("\n")
        last = raw.rfind("```")
        if first > 0 and last > first:
            raw = raw[first + 1:last].strip()
    try:
        return json.loads(raw)
    except ValueError:
        found = re.search(r"\{.*\}", raw, re.S)
        if not found:
            return None
        try:
            return json.loads(found.group(0))
        except ValueError:
            return None


def locate(document, quote):
    """The capture's own text for the span the quote points at, or None.

    Mirrors SpanLocator: exact containment on normalised text, then the quote cut
    back to its last full sentence, then nothing. Returning the document's bytes
    rather than the model's is what makes the stored span verifiable.
    """
    if not quote or not quote.strip():
        return None
    flat = normalize(document)
    needle = normalize(quote)
    index = flat.find(needle)
    if index < 0:
        cut = needle.rfind(". ")
        if cut > 20:
            needle = needle[:cut + 1]
            index = flat.find(needle)
    if index < 0:
        return None
    return flat[index:index + len(needle)]


def rejected(evidence_key, question_key, rationale):
    return {"evidenceKey": evidence_key, "questionKey": question_key, "verdict": "NONE",
            "confidence": 0.0, "quote": None, "rationale": rationale}


def classify(job):
    company, question, document, context, evidence_key = job
    question_key = question["key"]
    try:
        raw = call_model(company, question, context)
    except Exception as exc:
        bump("failed")
        row = rejected(evidence_key, question_key, "call failed: %s" % str(exc)[:120])
        row["_failed"] = True
        return row
    bump("calls")

    answer = read_json_object(raw)
    if answer is None:
        bump("unparseable")
        return rejected(evidence_key, question_key, "the model's answer could not be parsed")

    if not answer.get("match"):
        bump("rejected")
        return rejected(evidence_key, question_key,
                        "The model read this document and found no signal")

    try:
        confidence = float(answer.get("confidence") or 0.0)
    except (TypeError, ValueError):
        confidence = 0.0
    if confidence <= 0.5 or confidence > 1.0:
        bump("below_floor")
        return rejected(evidence_key, question_key,
                        "The model read this document and found no signal")

    span = locate(document, answer.get("quote") or "")
    if span is None:
        bump("struck")
        return rejected(evidence_key, question_key,
                        "The model's quote was not found in its capture and the row was struck")

    verdict = str(answer.get("verdict") or "").upper()
    if verdict not in ("BUY", "COUNTER", "BUILD"):
        verdict = question["verdict"]
    bump("signals")
    return {"evidenceKey": evidence_key, "questionKey": question_key, "verdict": verdict,
            "confidence": round(confidence, 4), "quote": span,
            "rationale": str(answer.get("reason") or "")[:200]}


def load_questions(base_url, attempts=8):
    policies = None
    last = None
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(base_url + "/api/policies", timeout=30) as response:
                policies = json.loads(response.read())
            break
        except Exception as exc:
            last = exc
            time.sleep(2 * (attempt + 1))
    if policies is None:
        raise RuntimeError("could not read the policy set: %s" % last)
    out = []
    for policy in policies:
        if not policy.get("active"):
            continue
        polarity = policy.get("polarity")
        out.append({
            "key": policy["key"],
            "text": policy.get("text") or "",
            "instructions": policy.get("instructions") or "",
            "polarity": polarity,
            "verdict": {"POSITIVE": "BUY", "NEGATIVE": "COUNTER",
                        "DISQUALIFYING": "BUILD"}.get(polarity, "BUY"),
            "patterns": [cue_regex(cue) for cue in (policy.get("cues") or []) if cue.strip()],
        })
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--sample", type=float, default=0.05,
                        help="fraction of cue-silent documents to read anyway")
    parser.add_argument("--workers", type=int, default=24)
    parser.add_argument("--policies-url", default="https://hackapi.pistonpunk.com")
    parser.add_argument("--only-questions", default="")
    parser.add_argument("--only-companies", default="")
    parser.add_argument("--seed", type=int, default=20260926)
    args = parser.parse_args()

    if not KEY and not args.dry_run:
        print("DEEPSEEK_API_KEY is not set", file=sys.stderr)
        return 1

    with open(RAW, encoding="utf-8") as handle:
        corpus = json.load(handle)
    names = {c["key"]: c["name"] for c in corpus["companies"]}
    evidence = corpus["evidence"]

    questions = load_questions(args.policies_url)
    if args.only_questions:
        wanted = set(args.only_questions.split(","))
        questions = [q for q in questions if q["key"] in wanted]
    if args.only_companies:
        wanted = set(args.only_companies.split(","))
        evidence = [e for e in evidence if e.get("companyKey") in wanted]

    rng = random.Random(args.seed)
    jobs = []
    live = 0
    sampled = 0
    for item in evidence:
        company = names.get(item.get("companyKey"), "")
        document = item.get("text") or ""
        if not document.strip():
            continue
        for question in questions:
            context = relevant_windows(document, question["patterns"])
            if context is not None:
                live += 1
            elif rng.random() < args.sample:
                context = document[:MAX_DOCUMENT_CHARS]
                sampled += 1
            else:
                continue
            jobs.append((company, question, document, context, item["key"]))

    print("documents        %d" % len(evidence))
    print("questions        %d" % len(questions))
    print("cue-live pairs   %d" % live)
    print("sampled pairs    %d" % sampled)
    print("model calls      %d" % len(jobs))
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

    todo = [job for job in jobs if (job[4], job[1]["key"]) not in done]
    print("to do            %d" % len(todo), flush=True)

    started = time.time()
    results = list(done.values())
    lock = threading.Lock()

    def flush():
        payload = {"engine": "%s@deepseek" % MODEL,
                   "generatedAt": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                   "pairsSeen": len(results),
                   "signals": results}
        tmp = PARTIAL
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=1)
        os.replace(tmp, PARTIAL)

    with futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for index, row in enumerate(pool.map(classify, todo), 1):
            if row is None:
                continue
            with lock:
                results.append(row)
                if index % 200 == 0:
                    rate = index / max(0.001, time.time() - started)
                    print("[%d/%d] %d rows  %.1f calls/s  eta %.0f min"
                          % (index, len(todo), len(results), rate,
                             (len(todo) - index) / max(0.001, rate) / 60), flush=True)
                    flush()

    flush()
    kept = [row for row in results if not row.get("_failed")]
    for row in kept:
        row.pop("_failed", None)

    payload = {"engine": "%s@deepseek" % MODEL,
               "generatedAt": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
               "pairsSeen": len(kept),
               "strikeRate": round(STATS.get("struck", 0) / max(1, STATS.get("calls", 1)), 4),
               "signals": kept}
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
    if os.path.exists(PARTIAL):
        os.remove(PARTIAL)

    print("[stats] %s" % json.dumps(STATS, indent=1, sort_keys=True))
    print("[out] %s %d bytes, %d rows, %d signals"
          % (OUT, os.path.getsize(OUT), len(kept), STATS.get("signals", 0)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
