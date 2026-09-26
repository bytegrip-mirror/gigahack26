"""Refuse to ship a corpus that will lose documents on the way in.

The database keys evidence by its key, so a duplicate in the file is a document
that is silently dropped at seed time and never scored. That is how a hundred and
sixteen news documents disappeared: the builder keyed them by their index within
each company, so every company's first story was called nws-1 and only one of
them survived. Nothing failed, nothing logged, and the board was quietly a
hundred documents short.

    python3 tools/verify_corpus.py            # checks the working tree
    python3 tools/verify_corpus.py --strict   # also fails on warnings
"""

import argparse
import hashlib
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
SIGNALS = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "signals.json")

MISSING = re.compile(
    r"\b404\b|page (?:you are looking for|not found|cannot be found)|"
    r"unavailable or no longer exists|no longer available", re.I)


class Report:
    def __init__(self):
        self.failures = []
        self.warnings = []

    def fail(self, message):
        self.failures.append(message)

    def warn(self, message):
        self.warnings.append(message)


def check(report, path=RAW, signals_path=SIGNALS):
    with open(path, encoding="utf-8") as handle:
        corpus = json.load(handle)

    companies = corpus.get("companies") or []
    evidence = corpus.get("evidence") or []

    company_keys = [c["key"] for c in companies]
    duplicates = {k for k in company_keys if company_keys.count(k) > 1}
    if duplicates:
        report.fail("company keys repeat: %s" % ", ".join(sorted(duplicates)[:5]))

    known = set(company_keys)
    keys = [e["key"] for e in evidence]
    repeated = {k for k in keys if keys.count(k) > 1}
    if repeated:
        report.fail("%d evidence keys repeat across %d rows, and the database keeps one of each: %s"
                    % (len(repeated), sum(1 for k in keys if k in repeated),
                       ", ".join(sorted(repeated)[:5])))

    for item in evidence:
        key = item.get("key") or ""
        if not key:
            report.fail("an evidence row has no key")
            continue
        if item.get("companyKey") not in known:
            report.fail("%s belongs to an unknown company %s" % (key, item.get("companyKey")))
        text = item.get("text") or ""
        if not text.strip():
            report.fail("%s carries no text" % key)
            continue
        digest = item.get("contentHash")
        if digest and digest.startswith("sha256:"):
            actual = "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()
            if actual != digest:
                report.fail("%s has a content hash that does not match its text" % key)
        if not item.get("sourceUrl"):
            report.warn("%s has no source URL" % key)
        if MISSING.search((item.get("title") or "")[:200]) or MISSING.search(text[:400]):
            report.warn("%s reads like a missing-page notice" % key)

    if not os.path.exists(signals_path):
        report.warn("no overlay is shipped")
        return report

    with open(signals_path, encoding="utf-8") as handle:
        overlay = json.load(handle)
    present = set(keys)
    orphans = [r for r in overlay.get("signals", []) if r.get("evidenceKey") not in present]
    if orphans:
        report.fail("%d overlay rows point at evidence the corpus does not carry, "
                    "so their verdicts can never be applied" % len(orphans))
    signals = sum(1 for r in overlay.get("signals", []) if r.get("verdict") != "NONE")
    if signals == 0:
        report.warn("the overlay carries no signals")
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--raw", default=RAW)
    parser.add_argument("--signals", default=SIGNALS)
    args = parser.parse_args()

    report = check(Report(), args.raw, args.signals)
    for message in report.warnings:
        print("warn:  %s" % message)
    for message in report.failures:
        print("FAIL:  %s" % message)

    if report.failures:
        print("\n%d problem(s) the corpus cannot ship with" % len(report.failures))
        return 1
    if args.strict and report.warnings:
        print("\n%d warning(s) in strict mode" % len(report.warnings))
        return 1
    with open(args.raw, encoding="utf-8") as handle:
        corpus = json.load(handle)
    print("corpus is coherent: %d companies, %d documents, %d documents per company"
          % (len(corpus["companies"]), len(corpus["evidence"]),
             round(len(corpus["evidence"]) / max(1, len(corpus["companies"])), 2)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
