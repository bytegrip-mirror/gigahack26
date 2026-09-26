"""Breadth-first corpus builder.

Google News RSS gives 100 dated items per query but its article links cannot be
resolved any more without hitting a 429 on the batchexecute endpoint. Bing News
RSS returns fewer items but hands back the real publisher URL inside the
redirect's url parameter, so the article can be fetched directly. This script
uses Bing for discovery and fetches the articles for their text.
"""

import concurrent.futures as futures
import datetime as dt
import hashlib
import html as htmlmod
import json
import os
import re
import ssl
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_corpus import COMPANIES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
PARTIAL = OUT + ".partial"

UA = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept-Language": "en-US,en;q=0.9",
}

QUERY_SHAPES = ["{n}", "{n} automation", "{n} cybersecurity", "{n} artificial intelligence"]
MAX_DOCS_PER_COMPANY = 6
MAX_AGE_DAYS = 548
WORKERS = 40
FETCH_WORKERS = 40

STATS = {}
STATS_LOCK = threading.Lock()


def bump(name, n=1):
    with STATS_LOCK:
        STATS[name] = STATS.get(name, 0) + n


def now_iso():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def http_get(url, timeout=25, limit_bytes=3_000_000):
    for attempt in range(2):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read(limit_bytes), r.headers.get("content-type", "")
        except urllib.error.HTTPError as e:
            bump("http_error_%d" % e.code)
            if e.code in (429, 503) and attempt == 0:
                continue
            raise
        except ssl.SSLError:
            bump("http_tls_rejected")
            raise
        except Exception:
            bump("http_fail")
            raise
    raise RuntimeError("unreachable")


def clean_text(s):
    s = htmlmod.unescape(s)
    s = re.sub(r"[ \t ]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def article_text(url):
    raw, _ = http_get(url)
    page = raw.decode("utf-8", "replace")
    page = re.sub(r"(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\1>", " ", page)
    chunks = []
    for m in re.finditer(r"<(h1|h2|p)\b[^>]*>(.*?)</\1\s*>", page, re.I | re.S):
        piece = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", m.group(2)))
        piece = clean_text(piece)
        if len(piece) >= 40:
            chunks.append(piece)
    text = "\n\n".join(chunks)
    if len(text) < 300:
        desc = re.findall(
            r'<meta[^>]+(?:name|property)=["\'](?:description|og:description)["\'][^>]+'
            r'content=["\']([^"\']{80,})["\']', page, re.I)
        if desc:
            text = (text + "\n\n" + "\n\n".join(clean_text(d) for d in desc)).strip()
    return text[:12000]


def bing_items(query):
    url = ("https://www.bing.com/news/search?q=" + urllib.parse.quote(query)
           + "&format=RSS&count=50")
    raw, _ = http_get(url, timeout=20)
    xml = raw.decode("utf-8", "replace")
    out = []
    for block in re.findall(r"<item>(.*?)</item>", xml, re.S):
        title = re.search(r"<title>(.*?)</title>", block, re.S)
        link = re.search(r"<link>(.*?)</link>", block, re.S)
        pub = re.search(r"<pubDate>(.*?)</pubDate>", block, re.S)
        if not title or not link:
            continue
        title = htmlmod.unescape(title.group(1)).strip()
        link = htmlmod.unescape(link.group(1)).strip()
        real = urllib.parse.parse_qs(urllib.parse.urlparse(link).query).get("url", [None])[0]
        if not real:
            continue
        out.append({
            "title": title,
            "url": real,
            "pubDate": pub.group(1).strip() if pub else None,
            "domain": urllib.parse.urlparse(real).netloc.lower().replace("www.", ""),
        })
    return out


MONTHS = {m: i + 1 for i, m in enumerate(
    ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])}


def rfc822_to_iso(value):
    if not value:
        return None
    m = re.search(r"(\d{1,2}) (\w{3}) (\d{4}) (\d{2}):(\d{2}):(\d{2})", value)
    if not m or m.group(2) not in MONTHS:
        return None
    day, mon, year, hh, mm, ss = m.groups()
    return "%04d-%02d-%02dT%s:%s:%sZ" % (int(year), MONTHS[mon], int(day), hh, mm, ss)


def age_days(iso):
    if not iso:
        return None
    try:
        when = dt.datetime.fromisoformat(iso.replace("Z", "+00:00"))
    except ValueError:
        return None
    return (dt.datetime.now(dt.timezone.utc) - when).days


def mentions(text, variants):
    low = text.lower()
    for v in variants:
        if len(v) >= 4:
            if v.lower() in low:
                return True
        elif 2 <= len(v) <= 3 and v.isupper() and re.search(r"\b%s\b" % re.escape(v), text):
            # ABB, BP, EY, UBS: a two-letter name is only a match as a standalone
            # capitalised token, never as a substring of an ordinary word.
            return True
    return False


def key_for(company, url):
    return "%s:bing:%s" % (company["key"], hashlib.sha1(url.encode()).hexdigest()[:12])


def slug(text, limit=60):
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s[:limit]


def alias_list(company):
    raw = company.get("aliases") or []
    if isinstance(raw, str):
        raw = raw.split(",")
    return [a.strip() for a in raw if a and len(a.strip()) >= 4]


def variants_of(company):
    return {company["name"], *alias_list(company)}


def build(company, observed):
    variants = variants_of(company)
    seen_urls = set()
    seen_titles = set()
    candidates = []

    for shape in QUERY_SHAPES:
        query = shape.format(n='"%s"' % company["name"])
        try:
            items = bing_items(query)
            bump("bing_queries")
        except Exception:
            bump("bing_query_failed")
            continue
        for item in items:
            url = item["url"]
            if url in seen_urls:
                continue
            tkey = re.sub(r"[^a-z0-9]", "", item["title"].lower())[:70]
            if tkey in seen_titles:
                continue
            if not mentions(item["title"], variants):
                bump("skipped_offtopic")
                continue
            age = age_days(rfc822_to_iso(item["pubDate"]))
            if age is not None and age > MAX_AGE_DAYS * 2:
                bump("skipped_ancient")
                continue
            seen_urls.add(url)
            seen_titles.add(tkey)
            candidates.append({**item, "publishedAt": rfc822_to_iso(item["pubDate"]), "age": age})

    candidates.sort(key=lambda c: (c["age"] is None, c["age"] or 0))
    docs = []
    for cand in candidates:
        if len(docs) >= MAX_DOCS_PER_COMPANY:
            break
        try:
            text = article_text(cand["url"])
        except Exception:
            bump("article_failed")
            continue
        if len(text) < 200 or not mentions(text, variants):
            bump("article_rejected")
            continue
        docs.append({
            "key": key_for(company, cand["url"]),
            "companyKey": company["key"],
            "sourceUrl": cand["url"],
            "publisher": cand["domain"],
            "sourceKind": "news",
            "publishedAt": cand["publishedAt"],
            "observedAt": observed,
            "title": cand["title"][:400],
            "text": text,
            "contentHash": "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest(),
        })
        bump("documents")
    return docs


def read_existing():
    if not os.path.exists(OUT):
        return {}
    try:
        with open(OUT, encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return {}


def carried_over(previous, evidence, companies_out):
    """
    A refresh adds documents; it never deletes evidence it did not go and fetch.

    Two classes survive every run: captures this sweep cannot produce at all
    (registry and filings), and everything belonging to a company that returned
    nothing this time — a failed fetch is a gap in the run, not a finding about
    the company. Only a company that actually came back with documents has its
    previous documents replaced.
    """
    refreshed = {d.get("companyKey") for d in evidence}
    previous_evidence = previous.get("evidence", [])
    kept = [d for d in previous_evidence
            if d.get("sourceKind") != "news" or d.get("companyKey") not in refreshed]

    every = {}
    for company in previous.get("companies", []) + companies_out:
        every[company.get("key")] = company

    if kept:
        print("[keep] %d documents carried over (%d companies untouched)"
              % (len(kept), len(every) - len(companies_out)), flush=True)
    return kept, list(every.values())


def deduped(evidence):
    seen = set()
    out = []
    for item in evidence:
        digest = item.get("contentHash") or item.get("sourceUrl")
        if digest in seen:
            continue
        seen.add(digest)
        out.append(item)
    return out


def write_payload(observed, companies_out, evidence):
    keep, every_company = carried_over(read_existing(), evidence, companies_out)
    merged = deduped(keep + evidence)
    payload = {"generatedAt": observed, "companies": every_company, "evidence": merged}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    tmp = PARTIAL
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    os.replace(tmp, OUT)


def main():
    only = None
    limit = None
    for arg in sys.argv[1:]:
        if arg.startswith("--only="):
            only = set(arg.split("=", 1)[1].split(","))
        elif arg.startswith("--limit="):
            limit = int(arg.split("=", 1)[1])

    todo = [c for c in COMPANIES if only is None or c["key"] in only]
    if limit:
        todo = todo[:limit]
    observed = now_iso()
    print("[run] %d companies" % len(todo), flush=True)

    results = {}
    done = 0
    with futures.ThreadPoolExecutor(max_workers=FETCH_WORKERS) as pool:
        futs = {pool.submit(build, c, observed): c for c in todo}
        for fut in futures.as_completed(futs):
            company = futs[fut]
            try:
                docs = fut.result()
            except Exception as exc:
                print("[fail]", company["key"], type(exc).__name__, str(exc)[:90], flush=True)
                docs = []
            results[company["key"]] = docs
            done += 1
            if done % 20 == 0 or done == len(todo):
                print("[%d/%d] docs so far %d" % (
                    done, len(todo), sum(len(v) for v in results.values())), flush=True)
                flush(observed, todo, results)

    flush(observed, todo, results)
    companies_out = [{
        "key": c["key"], "name": c["name"], "domain": c["domain"], "country": c["country"],
        "hqCity": c["hqCity"], "sizeBand": c["sizeBand"], "industry": c["industry"],
        "isCompetitor": c["isCompetitor"],
        "coverageNotes": "%d documents" % len(results.get(c["key"], [])),
        "aliases": alias_list(c),
    } for c in todo]
    evidence = [d for c in todo for d in results.get(c["key"], [])]
    write_payload(observed, companies_out, evidence)
    print("[stats]", json.dumps(STATS, indent=1, sort_keys=True), flush=True)
    print("[out]", OUT, os.path.getsize(OUT), "bytes", flush=True)


def flush(observed, todo, results):
    companies_out = [{
        "key": c["key"], "name": c["name"], "domain": c["domain"], "country": c["country"],
        "hqCity": c["hqCity"], "sizeBand": c["sizeBand"], "industry": c["industry"],
        "isCompetitor": c["isCompetitor"],
        "coverageNotes": "%d documents" % len(results.get(c["key"], [])),
        "aliases": alias_list(c),
    } for c in todo]
    evidence = [d for c in todo for d in results.get(c["key"], [])]
    write_payload(observed, companies_out, evidence)


if __name__ == "__main__":
    main()
