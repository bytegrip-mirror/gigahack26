"""Company-website corpus: newsrooms, press rooms and strategy pages.

The news layer only ever sees what a wire wrote about a company. The brief names
a second source the wire cannot substitute for — "company websites, newsrooms,
annual reports and strategy publications" — and it is the one that carries the
company's own words about what it intends to do. A press release saying a cost
programme has a quantified target is the company speaking; a story about its
share price is not.

Discovery is link-first: fetch the homepage, keep the anchors whose path or text
looks like a newsroom, press room, investor-relations or strategy page, and read
the article links from there. Nothing is invented — a document is written only
when its text was actually fetched.

    python3 tools/newsroom_corpus.py --only=dhl-group,lufthansa-group
    python3 tools/newsroom_corpus.py --tier=featured --limit=80
    python3 tools/newsroom_corpus.py --tier=all
"""

import argparse
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
import browser_fetch

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
PARTIAL = OUT + ".newsroom.partial"
CACHE = os.path.join(ROOT, "tools", ".cache", "newsroom")

FEATURED = [
    "dhl-group", "lufthansa-group", "reckitt", "expeditors-international", "emag",
    "ab-inbev", "electrolux", "signify", "julius-baer", "renishaw", "abn-amro",
    "crif", "vinci", "turkish-airlines", "jpmorgan-chase", "basf", "amgen",
    "continental", "societe-generale", "compugroup-medical",
]

UA = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}

DISCOVER_PATHS = [
    "", "/news", "/newsroom", "/press", "/press-room", "/press-releases", "/media",
    "/media-centre", "/media-center", "/news-and-events", "/news-events",
    "/investors", "/investor-relations", "/en/news", "/en/press", "/en/newsroom",
    "/about/news", "/company/news", "/corporate/news", "/newsroom.html",
    "/stories", "/insights", "/blog", "/our-stories", "/news-and-stories",
]

STRATEGY_PATHS = [
    "/strategy", "/en/strategy", "/about/strategy", "/company/strategy",
    "/investors/strategy", "/our-strategy", "/purpose", "/about-us", "/about",
    "/sustainability", "/en/sustainability", "/esg",
]

NEWSROOM_HINT = re.compile(
    r"news|press|media|newsroom|stories|insight|blog|updates|announcement", re.I)
STRATEGY_HINT = re.compile(
    r"strateg|sustainab|purpose|about|transformation|esg|report|investor|annual", re.I)
ARTICLE_HINT = re.compile(
    r"/(19|20)\d{2}/|/news/|/press|/story|/stories/|/article|/insight|/blog/|"
    r"announc|release|-20\d\d|/post/", re.I)

MISSING = re.compile(
    r"\b404\b|page (?:you are looking for|not found|cannot be found|doesn't exist)|"
    r"unavailable or no longer exists|no longer available|"
    r"the page you requested|sorry, (?:this|the) page|error 404|"
    r"we can'?t find (?:that|this) page|content not found|page not available", re.I)

MAX_ARTICLES = 14
MAX_STRATEGY = 4
MAX_TEXT = 12000
MIN_TEXT = 400

STATS = {}
STATS_LOCK = threading.Lock()


def bump(name, n=1):
    with STATS_LOCK:
        STATS[name] = STATS.get(name, 0) + n


def now_iso():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def cache_path(url):
    return os.path.join(CACHE, hashlib.sha1(url.encode()).hexdigest() + ".html")


def http_get(url, timeout=15, limit_bytes=4_000_000):
    for attempt in range(2):
        try:
            request = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.read(limit_bytes), response.headers.get("content-type", "")
        except urllib.error.HTTPError as exc:
            bump("http_%d" % exc.code)
            if exc.code in (429, 503) and attempt == 0:
                continue
            raise
        except ssl.SSLError:
            bump("tls_rejected")
            raise
        except Exception:
            bump("http_fail")
            raise
    raise RuntimeError("unreachable")


def fetch_page(url, allow_browser=False):
    os.makedirs(CACHE, exist_ok=True)
    cached = cache_path(url)
    if os.path.exists(cached):
        with open(cached, encoding="utf-8", errors="replace") as handle:
            return handle.read()
    try:
        raw, content_type = http_get(url)
        page = raw.decode("utf-8", "replace") if "html" in content_type.lower() else ""
    except Exception:
        page = ""
    if not page and allow_browser and browser_fetch.available():
        page = browser_fetch.render(url)
        if page:
            bump("browser_rescued")
    if not page:
        return ""
    with open(cached, "w", encoding="utf-8") as handle:
        handle.write(page)
    return page


STRIP_BLOCK = re.compile(
    r"(?is)<(script|style|noscript|svg|nav|footer|header|form|iframe)[^>]*>.*?</\1>")
TAG = re.compile(r"<[^>]+>")


def clean(value):
    value = htmlmod.unescape(value)
    value = re.sub(r"[ \t\r\f\v ]+", " ", value)
    return re.sub(r"\n{3,}", "\n\n", value).strip()


def body_text(page):
    page = STRIP_BLOCK.sub(" ", page)
    pieces = []
    for match in re.finditer(r"<(h1|h2|h3|p|li)\b[^>]*>(.*?)</\1\s*>", page, re.I | re.S):
        piece = clean(TAG.sub(" ", match.group(2)))
        if len(piece) >= 40:
            pieces.append(piece)
    return "\n\n".join(pieces)[:MAX_TEXT]


def title_of(page):
    match = re.search(r"<title[^>]*>(.*?)</title>", page, re.I | re.S)
    if not match:
        match = re.search(r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)', page, re.I)
    if not match:
        return ""
    return clean(TAG.sub("", match.group(1)))[:400]


def published_of(page):
    for pattern in (
            r'<meta[^>]+property=["\']article:published_time["\'][^>]+content=["\']([^"\']+)',
            r'<meta[^>]+name=["\'](?:date|pubdate|publishdate|article:published_time)["\'][^>]+content=["\']([^"\']+)',
            r'"datePublished"\s*:\s*"([^"]+)"',
            r'<time[^>]+datetime=["\']([^"\']+)'):
        match = re.search(pattern, page, re.I)
        if match:
            return iso_from(match.group(1))
    return None


def iso_from(value):
    value = value.strip()
    match = re.search(r"(\d{4})-(\d{2})-(\d{2})", value)
    if match:
        return "%s-%s-%sT00:00:00Z" % match.groups()
    return None


def links(page, base):
    out = []
    for match in re.finditer(r'<a\b[^>]*href=["\']([^"\'#]+)["\'][^>]*>(.*?)</a>', page, re.I | re.S):
        href = htmlmod.unescape(match.group(1)).strip()
        text = clean(TAG.sub(" ", match.group(2)))[:120]
        if href.startswith(("mailto:", "javascript:", "tel:")):
            continue
        try:
            absolute = urllib.parse.urljoin(base, href)
        except ValueError:
            continue
        parsed = urllib.parse.urlparse(absolute)
        if parsed.scheme not in ("http", "https"):
            continue
        out.append((absolute.split("#")[0], text, parsed.path))
    return out


def same_site(url, domain):
    host = urllib.parse.urlparse(url).netloc.lower().replace("www.", "")
    return host == domain or host.endswith("." + domain) or domain.endswith(host)


def mentions(text, variants):
    low = text.lower()
    for variant in variants:
        if len(variant) >= 4 and variant.lower() in low:
            return True
    return False


def key_for(company, url):
    return "%s:site:%s" % (company["key"], hashlib.sha1(url.encode()).hexdigest()[:12])


def document(company, url, page, observed):
    text = body_text(page)
    if len(text) < MIN_TEXT:
        return None
    title = title_of(page) or url
    if MISSING.search(title) or MISSING.search(text[:600]):
        bump("missing_page")
        return None
    return {
        "key": key_for(company, url),
        "companyKey": company["key"],
        "sourceUrl": url,
        "publisher": urllib.parse.urlparse(url).netloc.lower().replace("www.", ""),
        "sourceKind": "newsroom",
        "publishedAt": published_of(page),
        "observedAt": observed,
        "title": title,
        "text": text,
        "contentHash": "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest(),
    }


def fetch_many(urls, workers=10, allow_browser=False):
    """Probe every candidate path at once.

    A site answers on one of a dozen guessed newsroom paths and 404s the rest.
    Asking for them in turn spends the whole timeout budget on the misses, so
    they go out together and the first useful answer wins. The browser fallback
    is off for guesses: rendering twenty-three paths that were never going to
    exist costs minutes a company and rescues nothing.
    """
    found = []
    with futures.ThreadPoolExecutor(max_workers=workers) as pool:
        pending = {pool.submit(fetch_page, url, allow_browser): url for url in urls}
        for future in futures.as_completed(pending):
            url = pending[future]
            try:
                page = future.result()
            except Exception:
                continue
            if page:
                found.append((url, page))
    return found


def gather(company, observed):
    domain = (company.get("domain") or "").strip().lower()
    aliases = company.get("aliases") or []
    if not domain:
        bump("no_domain")
        return []

    variants = [company["name"]] + [a for a in aliases if isinstance(a, str)]
    variants = [v for v in variants if len(v) >= 4]
    origin = "https://" + domain

    root = fetch_many([origin], workers=1, allow_browser=True)
    landing = []
    extra = []
    if root:
        landing.append(root[0])
        for href, text, link_path in links(root[0][1], origin):
            if not same_site(href, domain):
                continue
            if NEWSROOM_HINT.search(link_path) or NEWSROOM_HINT.search(text):
                extra.append(href)
            elif STRATEGY_HINT.search(link_path):
                extra.append(href)

    guessed = [origin + path for path in DISCOVER_PATHS[1:]]
    for url, page in fetch_many(guessed, workers=10):
        bump("landings")
        landing.append((url, page))

    landing.extend(fetch_many(sorted(set(extra))[:12], workers=6, allow_browser=browser_fetch.available()))
    landing = [(url, page) for url, page in landing if page]

    strategy_pages = []
    for url, page in landing:
        if len(strategy_pages) >= MAX_STRATEGY:
            break
        path = urllib.parse.urlparse(url).path.lower()
        if any(hint in path for hint in STRATEGY_PATHS[1:]) and len(body_text(page)) >= MIN_TEXT:
            strategy_pages.append((url, page, True))
            bump("strategy_pages")

    articles = []
    seen = set()
    for url, page in landing:
        for href, text, link_path in links(page, url):
            if len(articles) >= MAX_ARTICLES * 2:
                break
            if href in seen or not same_site(href, domain):
                continue
            if not ARTICLE_HINT.search(link_path):
                continue
            if re.search(r"\.(pdf|jpg|png|zip|mp4)$", link_path, re.I):
                continue
            seen.add(href)
            articles.append(href)

    docs = []
    for url, page in fetch_many(articles[:MAX_ARTICLES], workers=8,
                                allow_browser=browser_fetch.available()):
        built = document(company, url, page, observed)
        if built is None:
            bump("article_too_thin")
            continue
        if not mentions(built["text"] + " " + built["title"], variants) and not same_site(url, domain):
            bump("article_offtopic")
            continue
        docs.append(built)
        bump("articles")

    for url, page, _ in strategy_pages:
        built = document(company, url, page, observed)
        if built is None:
            continue
        built["sourceKind"] = "strategy"
        docs.append(built)
        bump("strategy_docs")

    return docs


def read_existing():
    if not os.path.exists(OUT):
        return {"generatedAt": now_iso(), "companies": [], "evidence": []}
    try:
        with open(OUT, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {"generatedAt": now_iso(), "companies": [], "evidence": []}


def merge(existing, fresh, touched):
    """Add what was fetched; drop only what this sweep actually replaced.

    A company that came back with documents has its previous site documents
    replaced by the new ones. A company that returned nothing keeps whatever it
    had — a failed fetch is a gap in the run, not a finding about the company.
    """
    previous = existing.get("evidence", [])
    fresh_keys = {d["companyKey"] for d in fresh}
    kept = [d for d in previous
            if d.get("sourceKind") not in ("newsroom", "strategy")
            or d.get("companyKey") not in fresh_keys]

    merged = list(kept)
    held = {item.get("key") or item.get("sourceUrl") for item in merged}
    for item in fresh:
        key = item.get("key") or item.get("sourceUrl")
        if key in held:
            continue
        held.add(key)
        merged.append(item)

    companies = {c["key"]: c for c in existing.get("companies", [])}
    for company in touched:
        if company["key"] in companies:
            companies[company["key"]]["coverageNotes"] = "%d documents" % sum(
                1 for d in merged if d.get("companyKey") == company["key"])
        else:
            companies[company["key"]] = company
    return {
        "generatedAt": now_iso(),
        "companies": list(companies.values()),
        "evidence": merged,
    }


def write(payload):
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(PARTIAL, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
    os.replace(PARTIAL, OUT)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--tier", choices=["featured", "empty", "all"], default="featured")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args()

    existing = read_existing()
    companies = existing.get("companies", [])
    have = {}
    for item in existing.get("evidence", []):
        have[item["companyKey"]] = have.get(item["companyKey"], 0) + 1

    if args.only:
        wanted = [c for c in companies if c["key"] in set(args.only.split(","))]
    elif args.tier == "featured":
        order = {key: index for index, key in enumerate(FEATURED)}
        wanted = [c for c in companies if c["key"] in order]
        wanted.sort(key=lambda c: order[c["key"]])
    elif args.tier == "empty":
        wanted = [c for c in companies if have.get(c["key"], 0) == 0]
    else:
        wanted = list(companies)

    if args.limit:
        wanted = wanted[:args.limit]

    print("[run] %d companies (%s)" % (len(wanted), args.tier), flush=True)
    observed = now_iso()
    done = 0
    fresh = []

    with futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        pending = {pool.submit(gather, company, observed): company for company in wanted}
        for future in futures.as_completed(pending):
            company = pending[future]
            try:
                docs = future.result()
            except Exception as exc:
                print("[fail]", company["key"], type(exc).__name__, str(exc)[:80], flush=True)
                docs = []
            fresh.extend(docs)
            done += 1
            if done % 10 == 0 or done == len(wanted):
                print("[%d/%d] documents %d" % (done, len(wanted), len(fresh)), flush=True)
                write(merge(existing, fresh, wanted))

    write(merge(existing, fresh, wanted))
    payload = read_existing()
    print("[stats] %s" % json.dumps(STATS, indent=1, sort_keys=True))
    print("[out] %s %d bytes, %d evidence"
          % (OUT, os.path.getsize(OUT), len(payload.get("evidence", []))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
