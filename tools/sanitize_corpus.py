"""Remove documents that cannot be about the company they are filed under.

Every claim the board makes resolves to a byte range in a stored capture, and
none of that machinery checks the one thing that matters most: whether the
document is about the company at all. Attribution was a lowercase substring
test, so the shipped corpus carried a New Zealand yachting report under SEB
("Seb" Menzies), a Czech cruise-missile story and a Nigerian election story
under LPP, "Best Orange Cake Ever" under Orange, a physics-AI startup's job ads
under VINCI, and a pension scheme's registry row under AstraZeneca.

This strips those out and refuses the shapes that produced them, so the board
is scoring documents about the company it names. It is deliberately
conservative: it drops a document only when there is a positive reason to
believe it belongs to somebody else, and it prints what it dropped and why.

    python3 tools/sanitize_corpus.py --dry-run     # say what it would drop
    python3 tools/sanitize_corpus.py --write       # rewrite corpus and overlay
"""

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from attribution import (ATS_HOSTS, board_slug, domain_labels, fold, host_of,
                         mentions_company)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
SIGNALS = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "signals.json")

REGISTRY_NOISE = re.compile(
    r"PENSIONSSTIFTELSE|PENSION SCHEME|PENSION TRUSTEES|Pensionskasse|PENSIONSKASSE|"
    r"Klubben|Fællesklubben|Faellesklubben|PENSION FUND|RETIREMENT|STAFF ASSOCIATION",
    re.I)

NAVIGATION_PATH = re.compile(
    r"/press-spokes|/pressestelle|/presse-regional|/press-contact|/media-contact|/contact|"
    r"/nomination|/board|/governance|/subscribe|/investor-relations$|/newsletter|"
    r"/about-us/?$|/about/?$|/vision/?$|/our-values|/imprint|/legal-notice|/privacy",
    re.I)

MARKET_DATA_TITLE = re.compile(
    r"\bStock\b|\bBond\b|\bFund\b|Factsheet|Share Price|Estimated Below Intrinsic|"
    r"Number of Employees|Stock Jumps|Stock Falls|Stock Holds|Shares Fall|Shares Rise",
    re.I)

COLLISIONS = {
    "lpp": re.compile(r"cruise missile|narwhal|warhead|LPP Holding|Nigerians|LPP Candidate", re.I),
    "seb": re.compile(r"Menzies|yachting|49er|Nacra|skiff", re.I),
    "orange": re.compile(r"orange cake|cake|recipe", re.I),
    "nexi": re.compile(r"NexImmune", re.I),
    "vinci": re.compile(r"physics AI|physics simulation|hardware design", re.I),
    "sunrise": re.compile(r"robotic cells|Sunrise Robotics|on-board AI", re.I),
    "bp": re.compile(r"Aker BP", re.I),
    "atlas-copco": re.compile(r"charge card|concierge", re.I),
    "maxima": re.compile(r"accounting automation|bookkeeping", re.I),
    "novo-nordisk": re.compile(r"Senior Product Manager, Banking|neobank", re.I),
}

MIN_NEWSROOM_CHARS = 600


def rejection(company, item):
    url = item.get("sourceUrl") or ""
    kind = item.get("sourceKind") or "news"
    text = item.get("text") or ""
    title = item.get("title") or ""
    host = host_of(url)
    path = url[len("https://" + host):] if host else url

    collision = COLLISIONS.get(company["key"])
    if collision and collision.search(title + " " + text[:1200]):
        return "matches a known name collision for this company"

    if kind == "filing":
        return None

    if kind == "registry":
        if REGISTRY_NOISE.search(text):
            return "the registry record is a pension scheme or staff club, not the company"
        return None

    if kind == "job_posting":
        slug = board_slug(url)
        if any(host.endswith(ats) for ats in ATS_HOSTS):
            if slug not in domain_labels(company) and not mentions_company(company, text + " " + title):
                return "the job board slug belongs to a different company"
        if not mentions_company(company, text + " " + title) and not mentions_company(company, url):
            return "the posting never names the company"
        return None

    if kind in ("newsroom", "strategy"):
        if NAVIGATION_PATH.search(path):
            return "the page is navigation, contacts or governance, not content"
        if len(text) < MIN_NEWSROOM_CHARS:
            return "the page carries no prose"
        if not mentions_company(company, text + " " + title):
            return "the page never names the company"
        return None

    if MARKET_DATA_TITLE.search(title) and len(text) < 1200:
        return "the page is a quote or fund listing, not an article"

    if not mentions_company(company, text + " " + title):
        return "the document never names the company"
    return None


def sanitize(corpus, overlay):
    companies = {c["key"]: c for c in corpus["companies"]}
    kept_evidence = []
    dropped = {}
    for item in corpus["evidence"]:
        company = companies.get(item.get("companyKey"))
        if company is None:
            continue
        reason = rejection(company, item)
        if reason:
            dropped.setdefault(reason, []).append((company["name"], item.get("title") or "", item.get("sourceUrl") or ""))
            continue
        kept_evidence.append(item)

    counts = {}
    for item in kept_evidence:
        counts[item["companyKey"]] = counts.get(item["companyKey"], 0) + 1
    for company in corpus["companies"]:
        held = counts.get(company["key"], 0)
        notes = company.get("coverageNotes") or ""
        match = re.match(r"^(\d+)\s+(.*)$", notes)
        if match:
            company["coverageNotes"] = ("%d %s" % (held, match.group(2))) if held else "no documents"
        elif not held:
            company["coverageNotes"] = "no documents"

    kept_keys = {item["key"] for item in kept_evidence}
    kept_signals = [row for row in overlay["signals"] if row.get("evidenceKey") in kept_keys]
    return kept_evidence, kept_signals, dropped


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--show", type=int, default=6)
    args = parser.parse_args()

    with open(RAW, encoding="utf-8") as handle:
        corpus = json.load(handle)
    with open(SIGNALS, encoding="utf-8") as handle:
        overlay = json.load(handle)

    before_docs = len(corpus["evidence"])
    before_signals = sum(1 for row in overlay["signals"] if row.get("verdict") != "NONE")
    kept_evidence, kept_signals, dropped = sanitize(corpus, overlay)
    after_signals = sum(1 for row in kept_signals if row.get("verdict") != "NONE")

    print("documents: %d -> %d (dropped %d)" % (before_docs, len(kept_evidence), before_docs - len(kept_evidence)))
    print("model signals kept: %d -> %d" % (before_signals, after_signals))
    print()
    for reason, rows in sorted(dropped.items(), key=lambda pair: -len(pair[1])):
        print("%5d  %s" % (len(rows), reason))
        for name, title, url in rows[:args.show]:
            print("         %-28s %s" % (name[:28], (title or url)[:74]))
    print()

    if not args.write:
        print("dry run; pass --write to rewrite the corpus and the overlay")
        return 0

    corpus["evidence"] = kept_evidence
    overlay["signals"] = kept_signals
    with open(RAW, "w", encoding="utf-8") as handle:
        json.dump(corpus, handle, ensure_ascii=False, indent=1)
    with open(SIGNALS, "w", encoding="utf-8") as handle:
        json.dump(overlay, handle, ensure_ascii=False, indent=1)
    print("wrote %s and %s" % (RAW, SIGNALS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
