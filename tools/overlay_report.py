"""What the overlay cost, and what the cheap engine missed.

The overlay is built by spending model calls where the cue matcher would have
looked anyway, plus a random sample of the pairs it said nothing about. That
sample is the instrument: on it, the cue matcher is silent by construction, so
any signal the model returns there is a signal the cheap engine would have
missed. Reporting the rate turns "the model reads documents properly" from a
claim into a measurement with a sample size.

    python3 tools/overlay_report.py            # print
    python3 tools/overlay_report.py --write    # fold the numbers into signals.json
"""

import argparse
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model_overlay as overlay

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
SIGNALS = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "signals.json")
SAMPLE = 0.10
SEED = 20260926


def report(policies_url="https://hackapi.pistonpunk.com"):
    with open(RAW, encoding="utf-8") as handle:
        corpus = json.load(handle)
    with open(SIGNALS, encoding="utf-8") as handle:
        built = json.load(handle)
    questions = {q["key"]: q for q in overlay.load_questions(policies_url)}

    rng = random.Random(SEED)
    live = set()
    silent = set()
    for item in corpus["evidence"]:
        document = item.get("text") or ""
        if not document.strip():
            continue
        for key, question in questions.items():
            if overlay.relevant_windows(document, question["patterns"]) is not None:
                live.add((item["key"], key))
            elif rng.random() < SAMPLE:
                silent.add((item["key"], key))

    found = {(row["evidenceKey"], row["questionKey"]): row
             for row in built["signals"] if row["verdict"] != "NONE"}
    live_hits = sum(1 for pair in live if pair in found)
    silent_hits = sum(1 for pair in silent if pair in found)

    return {
        "cueLivePairs": len(live),
        "cueLiveSignals": live_hits,
        "cueLiveYield": round(live_hits / max(1, len(live)), 4),
        "cueSilentSampled": len(silent),
        "cueSilentSignals": silent_hits,
        "cueSilentYield": round(silent_hits / max(1, len(silent)), 4),
        "sampleRate": SAMPLE,
        "reportedSignals": len(found),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--policies-url", default="https://hackapi.pistonpunk.com")
    args = parser.parse_args()

    summary = report(args.policies_url)
    print(json.dumps(summary, indent=1))

    if not args.write:
        return 0
    with open(SIGNALS, encoding="utf-8") as handle:
        built = json.load(handle)
    built["sampling"] = summary
    with open(SIGNALS, "w", encoding="utf-8") as handle:
        json.dump(built, handle, ensure_ascii=False, indent=1)
    print("wrote the sampling block to %s" % SIGNALS)
    return 0


if __name__ == "__main__":
    sys.exit(main())
