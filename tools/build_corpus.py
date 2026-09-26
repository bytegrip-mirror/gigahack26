#!/usr/bin/env python3
import concurrent.futures as futures
import datetime as dt
import gzip
import hashlib
import html as htmlmod
import json
import os
import random
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import attribution
from vendor_registry import is_vendor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "api", "src", "main", "resources", "corpus", "raw.json")
CACHE = os.path.join(ROOT, "tools", ".cache")

UA = "gigahack26-corpus-builder/1.0 (daniel@pistonpunk.com)"
BROWSER_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
PLAIN_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

SEC_UA = UA
NEWS_WORKERS = 24
JOB_WORKERS = 24
ARTICLE_WORKERS = 40
REG_WORKERS = 6
FILING_WORKERS = 4

MAX_TEXT = 12000
MIN_TEXT = 120
MAX_JOBS_PER_COMPANY = 25
MAX_FILINGS_PER_COMPANY = 2
RECENCY_PRIMARY = "when:270d"
RECENCY_SECONDARY = "when:365d"
MAX_AGE_DAYS = 548
THEME_QUERIES = {
    "automation": [
        '"{}" automation',
        '"{}" digital transformation',
        '"{}" efficiency programme',
        '"{}" cost cutting',
        '"{}" results',
    ],
    "security": [
        '"{}" cybersecurity',
        '"{}" CISO',
        '"{}" NIS2',
        '"{}" DORA',
    ],
    "data": [
        '"{}" artificial intelligence',
        '"{}" machine learning',
        '"{}" data governance',
        '"{}" data platform',
    ],
}
THEME_TARGETS = {"automation": 5, "security": 2, "data": 2}
THEME_ORDER = ["automation", "security", "data"]

THEME_WORDS = {
    "security": ["cybersecurity", "cyber security", "ciso", "data breach", "breach",
                 "nis2", "dora", "iso 27001", "ransomware", "infosec", "soc 2",
                 "threat", "vulnerability", "phishing", "zero trust", "penetration test",
                 "security operations", "incident response"],
    "data": ["artificial intelligence", "machine learning", "data platform",
             "data governance", "chief data officer", "data lake", "data warehouse",
             "analytics", "generative ai", "genai", "llm", "data quality", "data mesh",
             "business intelligence", "data science", "predictive"],
    "automation": ["automation", "rpa", "robotic process", "digital transformation",
                   "process mining", "workflow", "efficiency", "cost cutting",
                   "restructuring", "outsourcing", "shared services", "bpo",
                   "erp", "sap", "straight-through", "back office", "self-service"],
}

SIGNAL_WORDS = [
    "automation", "automate", "automated", "rpa", "robotic process", "robotics",
    "efficiency", "efficiencies", "digital transformation", "digitalisation",
    "digitalization", "digitisation", "digitization", "artificial intelligence",
    "machine learning", "cybersecurity", "cyber security", "cloud", "erp", "sap",
    "data platform", "data analytics", "migration", "modernisation", "modernization",
    "cost cutting", "cost-cutting", "restructuring", "layoffs", "job cuts",
    "outsourcing", "outsource", "shared services", "shared service", "bpo",
    "process mining", "workflow", "back office", "back-office", "straight-through",
    "core banking", "legacy", "transformation programme", "transformation program",
    "ai-driven", "ai powered", "generative ai", "chatbot", "self-service", "fintech",
    "insurtech", "regtech", "invoice processing", "accounts payable", "payroll",
    "reconciliation", "contact centre", "contact center", "customer service",
]

JOB_ROLE_WORDS = [
    "automat", "rpa", "process", "ai", "artificial intelligence", "machine learning",
    "data", "security", "cyber", "transformation", "analyst", "engineer", "developer",
    "architect", "consultant", "sap", "erp", "cloud", "devops", "integration",
    "business intelligence", "bi ", "reporting", "quality assurance", "qa ",
    "project manager", "product owner", "scrum", "finance", "accounting", "payroll",
    "operations", "shared services", "support", "service desk", "technician",
]

HOST_SEMS = {}
HOST_SEMS_LOCK = threading.Lock()
STATS = {}
STATS_LOCK = threading.Lock()


def bump(name, n=1):
    with STATS_LOCK:
        STATS[name] = STATS.get(name, 0) + n


def sem_for(host, limit):
    with HOST_SEMS_LOCK:
        s = HOST_SEMS.get(host)
        if s is None:
            s = threading.Semaphore(limit)
            HOST_SEMS[host] = s
        return s


def now_iso():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def http_get(url, timeout=25, headers=None, retries=1, host_limit=16, limit_bytes=None, data=None):
    host = urllib.parse.urlparse(url).netloc
    s = sem_for(host, host_limit)
    hdrs = {
        "User-Agent": PLAIN_UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    }
    if headers:
        hdrs.update(headers)
    last = None
    for attempt in range(retries + 1):
        try:
            with s:
                req = urllib.request.Request(url, data=data, headers=hdrs)
                resp = urllib.request.urlopen(req, timeout=timeout)
                raw = resp.read() if limit_bytes is None else resp.read(limit_bytes)
                enc = (resp.headers.get("Content-Encoding") or "").lower()
                ctype = resp.headers.get("Content-Type") or ""
                if "gzip" in enc:
                    try:
                        raw = gzip.decompress(raw)
                    except Exception:
                        pass
                elif "deflate" in enc:
                    try:
                        raw = zlib.decompress(raw, -zlib.MAX_WBITS)
                    except Exception:
                        try:
                            raw = zlib.decompress(raw)
                        except Exception:
                            pass
            bump("http_ok")
            return raw, ctype, resp.geturl()
        except Exception as exc:
            last = exc
            bump("http_fail")
            if attempt < retries:
                time.sleep(0.6 + 0.4 * attempt)
    raise last


def decode_body(raw, ctype):
    charset = None
    m = re.search(r"charset=([A-Za-z0-9_\-]+)", ctype or "")
    if m:
        charset = m.group(1)
    if not charset:
        m = re.search(rb'charset=["\']?([A-Za-z0-9_\-]+)', raw[:4000], re.I)
        if m:
            charset = m.group(1).decode("ascii", "ignore")
    for cs in [charset, "utf-8", "cp1252", "latin-1"]:
        if not cs:
            continue
        try:
            return raw.decode(cs)
        except Exception:
            continue
    return raw.decode("utf-8", "replace")


BLOCK_RE = re.compile(
    r"<(script|style|noscript|svg|iframe|head|nav|footer|form|template|object|canvas)\b[^>]*>.*?</\1\s*>",
    re.I | re.S,
)
TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"[ \t\r\f\v ]+")
BLANKS_RE = re.compile(r"\n{3,}")


def strip_tags(s):
    s = BLOCK_RE.sub(" ", s)
    s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
    s = re.sub(r"</(p|div|li|h[1-6]|tr)\s*>", "\n\n", s, flags=re.I)
    s = TAG_RE.sub("", s)
    s = htmlmod.unescape(s)
    s = s.replace(" ", " ").replace("​", "").replace("﻿", "")
    lines = [WS_RE.sub(" ", ln).strip() for ln in s.split("\n")]
    out = []
    blank = 0
    for ln in lines:
        if not ln:
            blank += 1
            if blank > 2:
                continue
            out.append("")
        else:
            blank = 0
            out.append(ln)
    return "\n".join(out).strip()


def clean_text(s):
    s = htmlmod.unescape(s or "")
    s = s.replace(" ", " ").replace("​", "").replace("﻿", "")
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    lines = [WS_RE.sub(" ", ln).strip() for ln in s.split("\n")]
    out = []
    blank = 0
    for ln in lines:
        if not ln:
            blank += 1
            if blank > 2:
                continue
            out.append("")
        else:
            blank = 0
            out.append(ln)
    s = "\n".join(out).strip()
    s = BLANKS_RE.sub("\n\n", s)
    if len(s) > MAX_TEXT:
        cut = s[:MAX_TEXT]
        sp = cut.rfind(" ")
        if sp > MAX_TEXT - 400:
            cut = cut[:sp]
        s = cut.rstrip()
    return s


def has_markup(s):
    return "<" in s or ">" in s


def content_hash(text):
    return "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()


def slugify(s):
    s = s.lower().replace("&", " and ")
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return s.strip("-")


TICKER_MAP = {}


def load_sec_tickers():
    try:
        raw, _, _ = http_get(
            "https://www.sec.gov/files/company_tickers.json",
            headers={"User-Agent": SEC_UA},
            host_limit=2,
            timeout=40,
        )
        data = json.loads(raw.decode("utf-8"))
        for rec in data.values():
            t = (rec.get("ticker") or "").upper()
            if t:
                TICKER_MAP[t] = str(rec.get("cik_str", "")).zfill(10)
        print("[sec] tickers loaded:", len(TICKER_MAP), flush=True)
    except Exception as exc:
        print("[sec] ticker load failed:", exc, flush=True)


def rfc822_to_iso(v):
    if not v:
        return None
    v = v.strip()
    for fmt in ("%a, %d %b %Y %H:%M:%S %Z", "%a, %d %b %Y %H:%M:%S %z", "%Y-%m-%dT%H:%M:%SZ"):
        try:
            d = dt.datetime.strptime(v, fmt)
            if d.tzinfo is None:
                d = d.replace(tzinfo=dt.timezone.utc)
            return d.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        except Exception:
            continue
    try:
        d = dt.datetime.strptime(v[:25].strip(), "%a, %d %b %Y %H:%M:%S")
        return d.replace(tzinfo=dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    except Exception:
        return None


def iso_from_any(v):
    if not v:
        return None
    v = str(v).strip()
    if re.match(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z?$", v):
        return v[:19] + "Z"
    if re.match(r"^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}", v):
        base = v[:19].replace(" ", "T")
        m = re.match(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})", v)
        tail = v[19:]
        off = re.match(r"^([+-]\d{2}:?\d{2})", tail)
        if off:
            tz = off.group(1).replace(":", "")
            try:
                d = dt.datetime.strptime(m.group(1) + tz, "%Y-%m-%dT%H:%M:%S%z")
                return d.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            except Exception:
                pass
        return base + "Z"
    if re.match(r"^\d{4}-\d{2}-\d{2}$", v):
        return v + "T00:00:00Z"
    return None


def ms_to_iso(v):
    try:
        n = int(v)
    except Exception:
        return None
    if n > 10**12:
        n = n / 1000.0
    try:
        return dt.datetime.fromtimestamp(n, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    except Exception:
        return None


class Doc:
    __slots__ = ("key", "company_key", "source_url", "publisher", "source_kind",
                 "published_at", "title", "text", "observed_at")

    def __init__(self, key, company_key, source_url, publisher, source_kind,
                 published_at, title, text, observed_at):
        self.key = key
        self.company_key = company_key
        self.source_url = source_url
        self.publisher = publisher
        self.source_kind = source_kind
        self.published_at = published_at
        self.title = title
        self.text = text
        self.observed_at = observed_at

    def to_json(self):
        return {
            "key": self.key,
            "companyKey": self.company_key,
            "sourceUrl": self.source_url,
            "publisher": self.publisher,
            "sourceKind": self.source_kind,
            "publishedAt": self.published_at,
            "observedAt": self.observed_at,
            "title": self.title,
            "text": self.text,
            "contentHash": content_hash(self.text),
        }


def score_signal(text):
    low = text.lower()
    return sum(1 for w in SIGNAL_WORDS if w in low)


def name_variants(company):
    out = [company["name"]]
    out.extend(company["aliases"])
    return [v.lower() for v in out if v]


def mentions_company(text, variants):
    return attribution.mentions_company(variants, text)


def short_token(name):
    toks = [t for t in re.split(r"[^A-Za-z0-9]+", name) if len(t) > 3]
    if not toks:
        return None
    stop = {"group", "holding", "holdings", "international", "global", "company",
            "systems", "solutions", "services", "technologies", "technology"}
    cand = [t for t in toks if t.lower() not in stop]
    return (cand or toks)[0]


NEWS_ITEM_RE = re.compile(r"<item>(.*?)</item>", re.S)


def parse_rss_items(xml_text):
    items = []
    for block in NEWS_ITEM_RE.findall(xml_text):
        rec = {}
        for tag in ("title", "link", "pubDate", "description", "source"):
            m = re.search(r"<%s[^>]*>(.*?)</%s>" % (tag, tag), block, re.S)
            if m:
                rec[tag] = m.group(1).strip()
        m = re.search(r"<source\s+url=\"([^\"]+)\"", block, re.S)
        if m:
            rec["source_url_attr"] = htmlmod.unescape(m.group(1))
        if rec.get("link"):
            items.append(rec)
    return items


GN_DECODE_LOCK = threading.Semaphore(NEWS_WORKERS)


def gn_resolve(gn_link):
    if "/rss/articles/" not in gn_link:
        return gn_link
    try:
        article = gn_link.split("/rss/articles/")[-1].split("?")[0]
        with GN_DECODE_LOCK:
            raw, _, _ = http_get(gn_link, headers={"User-Agent": BROWSER_UA},
                                 host_limit=NEWS_WORKERS, timeout=30, retries=2)
            page = raw.decode("utf-8", "replace")
            msg = re.search(r'data-n-a-sg="([^"]+)"', page)
            mts = re.search(r'data-n-a-ts="([^"]+)"', page)
            if not msg or not mts:
                bump("gn_no_token")
                return None
            sg = msg.group(1)
            ts = mts.group(1)
            inner = (
                '[\\"garturlreq\\",[[\\"X\\",\\"X\\",[\\"X\\",\\"X\\"],null,null,1,1,'
                '\\"US:en\\",null,1,null,null,null,null,null,0,1],\\"X\\",\\"X\\",1,'
                '[2,3,4,8],1,1,null,0,0,null,0],\\"' + article + '\\",' + ts + ',\\"' + sg + '\\"]'
            )
            payload = '[[["Fbv4je","' + inner + '",null,"generic"]]]'
            body = urllib.parse.urlencode({"f.req": payload}).encode("utf-8")
            raw2, _, _ = http_get(
                "https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je",
                headers={"User-Agent": BROWSER_UA,
                         "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
                host_limit=NEWS_WORKERS, timeout=30, retries=2, data=body,
            )
        txt = raw2.decode("utf-8", "replace")
        m = re.findall(r'\[\\"garturlres\\",\\"(https?:[^"\\]+)', txt)
        if not m:
            m = re.findall(r'"(https?://(?!news\.google|www\.google|accounts\.google)[^"]{15,})"', txt)
        if not m:
            bump("gn_no_result")
            return None
        url = m[0].replace("\\u003d", "=").replace("\\u0026", "&").replace("\\/", "/")
        if not url.startswith("http"):
            bump("gn_bad_url")
            return None
        bump("gn_resolved")
        return url
    except Exception as exc:
        bump("gn_error")
        bump("gn_error_" + type(exc).__name__)
        return None


def extract_article(raw, ctype):
    page = decode_body(raw, ctype)
    if not page:
        return ""
    page = BLOCK_RE.sub(" ", page)
    chunks = []
    for m in re.finditer(r"<(h1|h2|p)\b[^>]*>(.*?)</\1\s*>", page, re.I | re.S):
        piece = strip_tags(m.group(2))
        piece = re.sub(r"\s+", " ", piece).strip()
        if len(piece) >= 40:
            chunks.append(piece)
    text = "\n\n".join(chunks)
    if len(text) < 400:
        body = re.search(r"<body\b[^>]*>(.*?)</body\s*>", page, re.I | re.S)
        alt = strip_tags(body.group(1) if body else page)
        alt = re.sub(r"\n{3,}", "\n\n", alt).strip()
        if len(alt) > len(text):
            text = alt
    if len(text) < 400:
        meta = re.findall(
            r'<meta[^>]+(?:name|property)=["\'](?:description|og:description)["\'][^>]+content=["\']([^"\']{60,})["\']',
            page, re.I)
        if meta:
            extra = "\n\n".join(re.sub(r"\s+", " ", htmlmod.unescape(x)).strip() for x in meta)
            text = (text + "\n\n" + extra).strip() if text else extra
    return clean_text(text)


def fetch_article_text(url):
    raw, ctype, _ = http_get(url, headers={"User-Agent": BROWSER_UA}, timeout=25,
                             host_limit=6, limit_bytes=3_000_000)
    if b"<" not in raw[:2000] and b"{" not in raw[:200]:
        return ""
    return extract_article(raw, ctype)


def c(key, name, domain, country, city, size, industry, competitor=False,
      aliases="", gleif=None, ticker=None):
    return {
        "key": key,
        "name": name,
        "domain": domain,
        "country": country,
        "hqCity": city,
        "sizeBand": size,
        "industry": industry,
        "isCompetitor": competitor or is_vendor(name, industry),
        "aliases": [a.strip() for a in aliases.split("|") if a.strip()],
        "gleif": gleif or name,
        "ticker": ticker,
    }


COMPANIES = [
    c("lufthansa-group", "Lufthansa Group", "lufthansa.com", "DE", "Cologne", "10001+", "Airlines",
      False, "Lufthansa|Deutsche Lufthansa AG", "Deutsche Lufthansa AG", "LHA.DE"),
    c("dhl-group", "DHL Group", "dhl.com", "DE", "Bonn", "10001+", "Logistics",
      False, "DHL|Deutsche Post DHL|Deutsche Post", "Deutsche Post AG", "DHL.DE"),
    c("air-france-klm", "Air France-KLM", "airfranceklm.com", "FR", "Paris", "10001+", "Airlines",
      False, "Air France|KLM", "Air France-KLM SA", "AF.PA"),
    c("iag", "International Airlines Group", "iairgroup.com", "GB", "London", "10001+", "Airlines",
      False, "British Airways|Iberia|Aer Lingus", "International Consolidated Airlines Group", "IAG.L"),
    c("ryanair", "Ryanair Holdings", "ryanair.com", "IE", "Dublin", "10001+", "Airlines",
      False, "Ryanair", "Ryanair Holdings plc", "RYA.IR"),
    c("easyjet", "easyJet", "easyjet.com", "GB", "Luton", "10001+", "Airlines",
      False, "", "easyJet plc", "EZJ.L"),
    c("wizz-air", "Wizz Air Holdings", "wizzair.com", "HU", "Budapest", "5001-10000", "Airlines",
      False, "Wizz Air", "Wizz Air Holdings Plc", "WIZZ.L"),
    c("turkish-airlines", "Turkish Airlines", "turkishairlines.com", "TR", "Istanbul", "10001+", "Airlines",
      False, "THY", "Turk Hava Yollari", "THYAO.IS"),
    c("aena", "Aena", "aena.es", "ES", "Madrid", "10001+", "Airport Operations",
      False, "Aena SME", "Aena S.M.E. SA", "AENA.MC"),
    c("fraport", "Fraport", "fraport.com", "DE", "Frankfurt", "10001+", "Airport Operations",
      False, "Frankfurt Airport", "Fraport AG", "FRA.DE"),
    c("paris-airport", "Aeroports de Paris", "parisaeroport.fr", "FR", "Paris", "10001+",
      "Airport Operations", False, "ADP|Paris Airport", "Aeroports de Paris SA", "ADP.PA"),
    c("amadeus", "Amadeus IT Group", "amadeus.com", "ES", "Madrid", "10001+", "Travel Technology",
      False, "Amadeus", "Amadeus IT Group SA", "AMS.MC"),
    c("kuehne-nagel", "Kuehne+Nagel", "kuehne-nagel.com", "CH", "Schindellegi", "10001+", "Logistics",
      False, "Kuehne Nagel", "Kuehne + Nagel International AG", "KNIN.SW"),
    c("dsv", "DSV", "dsv.com", "DK", "Hedehusene", "10001+", "Logistics",
      False, "DSV Air & Sea", "DSV A/S", "DSV.CO"),
    c("maersk", "A.P. Moller-Maersk", "maersk.com", "DK", "Copenhagen", "10001+", "Shipping",
      False, "Maersk", "A.P. Moller - Maersk", "MAERSK-B.CO"),
    c("cma-cgm", "CMA CGM", "cma-cgm.com", "FR", "Marseille", "10001+", "Shipping",
      False, "", "CMA CGM SA"),
    c("hapag-lloyd", "Hapag-Lloyd", "hapag-lloyd.com", "DE", "Hamburg", "10001+", "Shipping",
      False, "Hapag Lloyd", "Hapag-Lloyd AG", "HLAG.DE"),
    c("dp-world", "DP World", "dpworld.com", "AE", "Dubai", "10001+", "Logistics",
      False, "", "DP World Limited"),
    c("db-schenker", "DB Schenker", "dbschenker.com", "DE", "Essen", "10001+", "Logistics",
      False, "Schenker", "Schenker AG"),
    c("dachser", "Dachser", "dachser.com", "DE", "Kempten", "10001+", "Logistics",
      False, "", "Dachser SE"),
    c("rhenus", "Rhenus Logistics", "rhenus.group", "DE", "Holzwickede", "10001+", "Logistics",
      False, "Rhenus", "Rhenus SE & Co. KG"),
    c("geodis", "GEODIS", "geodis.com", "FR", "Levallois-Perret", "10001+", "Logistics",
      False, "", "GEODIS SA"),
    c("postnl", "PostNL", "postnl.nl", "NL", "The Hague", "10001+", "Postal",
      False, "PostNL NV", "PostNL N.V.", "PNL.AS"),
    c("bpost", "bpost", "bpost.be", "BE", "Brussels", "10001+", "Postal",
      False, "Belgian Post Group", "bpost SA", "BPOST.BR"),
    c("la-poste", "La Poste Groupe", "lapostegroupe.com", "FR", "Paris", "10001+", "Postal",
      False, "La Poste", "La Poste"),
    c("poste-italiane", "Poste Italiane", "posteitaliane.it", "IT", "Rome", "10001+", "Postal",
      False, "", "Poste Italiane S.p.A.", "PST.MI"),
    c("royal-mail", "International Distributions Services", "royalmailgroup.com", "GB", "London",
      "10001+", "Postal", False, "Royal Mail|Royal Mail Group", "Royal Mail plc", "IDS.L"),
    c("ctt-portugal", "CTT Correios de Portugal", "ctt.pt", "PT", "Lisbon", "5001-10000", "Postal",
      False, "CTT", "CTT - Correios de Portugal, S.A.", "CTT.LS"),
    c("dpdgroup", "DPDgroup", "dpd.com", "FR", "Issy-les-Moulineaux", "10001+", "Logistics",
      False, "DPD", "DPDgroup"),
    c("gls-group", "GLS Group", "gls-group.com", "NL", "Amsterdam", "10001+", "Logistics",
      False, "General Logistics Systems", "General Logistics Systems B.V."),
    c("fedex", "FedEx", "fedex.com", "US", "Memphis", "10001+", "Logistics",
      False, "Federal Express", "FedEx Corporation", "FDX"),
    c("ups", "UPS", "ups.com", "US", "Atlanta", "10001+", "Logistics",
      False, "United Parcel Service", "United Parcel Service, Inc.", "UPS"),
    c("xpo", "XPO", "xpo.com", "US", "Greenwich", "10001+", "Logistics",
      False, "XPO Logistics", "XPO, Inc.", "XPO"),
    c("ch-robinson", "C.H. Robinson", "chrobinson.com", "US", "Eden Prairie", "10001+", "Logistics",
      False, "CH Robinson", "C.H. Robinson Worldwide, Inc.", "CHRW"),
    c("expeditors", "Expeditors International", "expeditors.com", "US", "Seattle", "10001+", "Logistics",
      False, "Expeditors", "Expeditors International of Washington, Inc.", "EXPD"),
    c("deutsche-bahn", "Deutsche Bahn", "deutschebahn.com", "DE", "Berlin", "10001+", "Rail Transport",
      False, "DB", "Deutsche Bahn AG"),
    c("sncf", "SNCF", "sncf.com", "FR", "Saint-Denis", "10001+", "Rail Transport",
      False, "SNCF Group", "SNCF"),
    c("ns", "Nederlandse Spoorwegen", "ns.nl", "NL", "Utrecht", "10001+", "Rail Transport",
      False, "NS|Dutch Railways", "N.V. Nederlandse Spoorwegen"),
    c("alstom", "Alstom", "alstom.com", "FR", "Saint-Ouen", "10001+", "Rail Equipment",
      False, "", "Alstom SA", "ALO.PA"),

    c("deutsche-bank", "Deutsche Bank", "db.com", "DE", "Frankfurt", "10001+", "Banking",
      False, "", "Deutsche Bank AG", "DBK.DE"),
    c("commerzbank", "Commerzbank", "commerzbank.com", "DE", "Frankfurt", "10001+", "Banking",
      False, "", "Commerzbank AG", "CBK.DE"),
    c("unicredit", "UniCredit", "unicreditgroup.eu", "IT", "Milan", "10001+", "Banking",
      False, "UniCredit Group", "UniCredit S.p.A.", "UCG.MI"),
    c("intesa-sanpaolo", "Intesa Sanpaolo", "intesasanpaolo.com", "IT", "Turin", "10001+", "Banking",
      False, "", "Intesa Sanpaolo S.p.A.", "ISP.MI"),
    c("bnp-paribas", "BNP Paribas", "bnpparibas.com", "FR", "Paris", "10001+", "Banking",
      False, "", "BNP Paribas SA", "BNP.PA"),
    c("societe-generale", "Societe Generale", "societegenerale.com", "FR", "Paris", "10001+", "Banking",
      False, "SocGen", "Societe Generale SA", "GLE.PA"),
    c("credit-agricole", "Credit Agricole", "credit-agricole.com", "FR", "Montrouge", "10001+", "Banking",
      False, "Credit Agricole Group", "Credit Agricole S.A.", "ACA.PA"),
    c("bpce", "BPCE", "bpce.fr", "FR", "Paris", "10001+", "Banking",
      False, "Groupe BPCE|Natixis", "BPCE SA"),
    c("hsbc", "HSBC", "hsbc.com", "GB", "London", "10001+", "Banking",
      False, "HSBC Holdings", "HSBC Holdings plc", "HSBA.L"),
    c("barclays", "Barclays", "barclays.com", "GB", "London", "10001+", "Banking",
      False, "Barclays Bank", "Barclays PLC", "BARC.L"),
    c("lloyds-banking-group", "Lloyds Banking Group", "lloydsbanking.com", "GB", "London", "10001+",
      "Banking", False, "Lloyds Bank", "Lloyds Banking Group plc", "LLOY.L"),
    c("natwest-group", "NatWest Group", "natwestgroup.com", "GB", "Edinburgh", "10001+", "Banking",
      False, "NatWest|Royal Bank of Scotland", "NatWest Group plc", "NWG.L"),
    c("standard-chartered", "Standard Chartered", "sc.com", "GB", "London", "10001+", "Banking",
      False, "", "Standard Chartered PLC", "STAN.L"),
    c("santander", "Banco Santander", "santander.com", "ES", "Madrid", "10001+", "Banking",
      False, "Santander", "Banco Santander, S.A.", "SAN.MC"),
    c("bbva", "BBVA", "bbva.com", "ES", "Bilbao", "10001+", "Banking",
      False, "Banco Bilbao Vizcaya Argentaria", "Banco Bilbao Vizcaya Argentaria, S.A.", "BBVA.MC"),
    c("caixabank", "CaixaBank", "caixabank.com", "ES", "Valencia", "10001+", "Banking",
      False, "", "CaixaBank, S.A.", "CABK.MC"),
    c("banco-sabadell", "Banco Sabadell", "bancsabadell.com", "ES", "Alicante", "10001+", "Banking",
      False, "Sabadell", "Banco de Sabadell, S.A.", "SAB.MC"),
    c("ing-group", "ING Group", "ing.com", "NL", "Amsterdam", "10001+", "Banking",
      False, "ING", "ING Groep N.V.", "INGA.AS"),
    c("abn-amro", "ABN AMRO", "abnamro.com", "NL", "Amsterdam", "10001+", "Banking",
      False, "ABN AMRO Bank", "ABN AMRO Bank N.V.", "ABN.AS"),
    c("rabobank", "Rabobank", "rabobank.com", "NL", "Utrecht", "10001+", "Banking",
      False, "Cooperatieve Rabobank", "Cooperatieve Rabobank U.A."),
    c("nordea", "Nordea", "nordea.com", "FI", "Helsinki", "10001+", "Banking",
      False, "Nordea Bank", "Nordea Bank Abp", "NDA-FI.HE"),
    c("danske-bank", "Danske Bank", "danskebank.com", "DK", "Copenhagen", "10001+", "Banking",
      False, "", "Danske Bank A/S", "DANSKE.CO"),
    c("seb", "SEB", "sebgroup.com", "SE", "Stockholm", "10001+", "Banking",
      False, "Skandinaviska Enskilda Banken", "Skandinaviska Enskilda Banken AB", "SEB-A.ST"),
    c("swedbank", "Swedbank", "swedbank.com", "SE", "Stockholm", "10001+", "Banking",
      False, "", "Swedbank AB", "SWED-A.ST"),
    c("handelsbanken", "Handelsbanken", "handelsbanken.com", "SE", "Stockholm", "10001+", "Banking",
      False, "Svenska Handelsbanken", "Svenska Handelsbanken AB", "SHB-A.ST"),
    c("dnb", "DNB", "dnb.no", "NO", "Oslo", "10001+", "Banking",
      False, "DNB Bank", "DNB Bank ASA", "DNB.OL"),
    c("ubs", "UBS", "ubs.com", "CH", "Zurich", "10001+", "Banking",
      False, "UBS Group", "UBS Group AG", "UBSG.SW"),
    c("julius-baer", "Julius Baer", "juliusbaer.com", "CH", "Zurich", "5001-10000", "Wealth Management",
      False, "Julius Bar", "Julius Baer Group Ltd.", "BAER.SW"),
    c("raiffeisen-bank-international", "Raiffeisen Bank International", "rbinternational.com", "AT",
      "Vienna", "10001+", "Banking", False, "RBI", "Raiffeisen Bank International AG", "RBI.VI"),
    c("erste-group", "Erste Group", "erstegroup.com", "AT", "Vienna", "10001+", "Banking",
      False, "Erste Group Bank", "Erste Group Bank AG", "EBS.VI"),
    c("otp-bank", "OTP Bank", "otpbank.hu", "HU", "Budapest", "10001+", "Banking",
      False, "OTP Bank Nyrt", "OTP Bank Nyrt.", "OTP.BD"),
    c("kbc", "KBC", "kbc.com", "BE", "Brussels", "10001+", "Banking",
      False, "KBC Group", "KBC Group NV", "KBC.BR"),
    c("belfius", "Belfius", "belfius.be", "BE", "Brussels", "5001-10000", "Banking",
      False, "Belfius Bank", "Belfius Bank SA"),
    c("banca-transilvania", "Banca Transilvania", "bancatransilvania.ro", "RO", "Cluj-Napoca",
      "5001-10000", "Banking", False, "BT", "Banca Transilvania S.A.", "TLV.RO"),
    c("brd", "BRD - Groupe Societe Generale", "brd.ro", "RO", "Bucharest", "5001-10000", "Banking",
      False, "BRD", "BRD - Groupe Societe Generale S.A.", "BRD.RO"),
    c("bcr", "Banca Comerciala Romana", "bcr.ro", "RO", "Bucharest", "5001-10000", "Banking",
      False, "BCR", "Banca Comerciala Romana S.A."),
    c("piraeus-bank", "Piraeus Bank", "piraeusbank.gr", "GR", "Athens", "10001+", "Banking",
      False, "Piraeus", "Piraeus Bank S.A.", "TPEIR.AT"),
    c("eurobank", "Eurobank", "eurobank.gr", "GR", "Athens", "10001+", "Banking",
      False, "Eurobank Ergasias", "Eurobank Ergasias Services and Holdings S.A.", "EUROB.AT"),
    c("national-bank-of-greece", "National Bank of Greece", "nbg.gr", "GR", "Athens", "10001+", "Banking",
      False, "NBG", "National Bank of Greece S.A.", "ETE.AT"),
    c("bank-of-cyprus", "Bank of Cyprus", "bankofcyprus.com", "CY", "Nicosia", "1001-5000", "Banking",
      False, "", "Bank of Cyprus Public Company Limited", "BOCH.L"),
    c("pko-bank-polski", "PKO Bank Polski", "pkobp.pl", "PL", "Warsaw", "10001+", "Banking",
      False, "PKO BP", "Powszechna Kasa Oszczednosci Bank Polski S.A.", "PKO.WA"),
    c("bank-pekao", "Bank Pekao", "pekao.com.pl", "PL", "Warsaw", "10001+", "Banking",
      False, "Pekao", "Bank Polska Kasa Opieki S.A.", "PEO.WA"),
    c("mbank", "mBank", "mbank.pl", "PL", "Warsaw", "5001-10000", "Banking",
      False, "", "mBank S.A.", "MBK.WA"),
    c("ceska-sporitelna", "Ceska sporitelna", "csas.cz", "CZ", "Prague", "10001+", "Banking",
      False, "Czech Savings Bank", "Ceska sporitelna, a.s."),
    c("komercni-banka", "Komercni banka", "kb.cz", "CZ", "Prague", "1001-5000", "Banking",
      False, "", "Komercni banka, a.s.", "KOMB.PR"),
    c("moneta-money-bank", "Moneta Money Bank", "moneta.cz", "CZ", "Prague", "1001-5000", "Banking",
      False, "Moneta", "MONETA Money Bank, a.s.", "MONET.PR"),
    c("tatra-banka", "Tatra banka", "tatrabanka.sk", "SK", "Bratislava", "1001-5000", "Banking",
      False, "", "Tatra banka, a.s."),
    c("slovenska-sporitelna", "Slovenska sporitelna", "slsp.sk", "SK", "Bratislava", "1001-5000",
      "Banking", False, "SLSP", "Slovenska sporitelna, a.s."),
    c("citigroup", "Citigroup", "citigroup.com", "US", "New York", "10001+", "Banking",
      False, "Citi", "Citigroup Inc.", "C"),
    c("jpmorgan-chase", "JPMorgan Chase", "jpmorganchase.com", "US", "New York", "10001+", "Banking",
      False, "JPMorgan", "JPMorgan Chase & Co.", "JPM"),
    c("goldman-sachs", "Goldman Sachs", "goldmansachs.com", "US", "New York", "10001+", "Investment Banking",
      False, "", "The Goldman Sachs Group, Inc.", "GS"),
    c("morgan-stanley", "Morgan Stanley", "morganstanley.com", "US", "New York", "10001+", "Investment Banking",
      False, "", "Morgan Stanley", "MS"),
    c("wells-fargo", "Wells Fargo", "wellsfargo.com", "US", "San Francisco", "10001+", "Banking",
      False, "", "Wells Fargo & Company", "WFC"),
    c("american-express", "American Express", "americanexpress.com", "US", "New York", "10001+", "Payments",
      False, "Amex", "American Express Company", "AXP"),
    c("visa", "Visa", "visa.com", "US", "San Francisco", "10001+", "Payments",
      False, "Visa Inc", "Visa Inc.", "V"),
    c("mastercard", "Mastercard", "mastercard.com", "US", "Purchase", "10001+", "Payments",
      False, "", "Mastercard Incorporated", "MA"),
    c("paypal", "PayPal", "paypal.com", "US", "San Jose", "10001+", "Payments",
      False, "PayPal Holdings", "PayPal Holdings, Inc.", "PYPL"),
    c("fiserv", "Fiserv", "fiserv.com", "US", "Milwaukee", "10001+", "Financial Technology",
      False, "", "Fiserv, Inc.", "FI"),
    c("fis", "FIS", "fisglobal.com", "US", "Jacksonville", "10001+", "Financial Technology",
      False, "FIS Global", "Fidelity National Information Services, Inc.", "FIS"),
    c("ncr-voyix", "NCR Voyix", "ncrvoyix.com", "US", "Atlanta", "10001+", "Financial Technology",
      False, "NCR", "NCR Voyix Corporation", "VYX"),
    c("lseg", "London Stock Exchange Group", "lseg.com", "GB", "London", "10001+", "Financial Infrastructure",
      False, "LSEG", "London Stock Exchange Group plc", "LSEG.L"),
    c("deutsche-boerse", "Deutsche Boerse", "deutsche-boerse.com", "DE", "Frankfurt", "5001-10000",
      "Financial Infrastructure", False, "Deutsche Borse", "Deutsche Boerse AG", "DB1.DE"),
    c("euronext", "Euronext", "euronext.com", "NL", "Amsterdam", "1001-5000", "Financial Infrastructure",
      False, "", "Euronext N.V.", "ENX.PA"),
    c("six-group", "SIX Group", "six-group.com", "CH", "Zurich", "1001-5000", "Financial Infrastructure",
      False, "SIX", "SIX Group AG"),
    c("swift", "SWIFT", "swift.com", "BE", "La Hulpe", "1001-5000", "Financial Infrastructure",
      False, "Society for Worldwide Interbank Financial Telecommunication", "SWIFT SCRL"),
    c("euroclear", "Euroclear", "euroclear.com", "BE", "Brussels", "1001-5000", "Financial Infrastructure",
      False, "", "Euroclear Holding SA"),
    c("amundi", "Amundi", "amundi.com", "FR", "Paris", "5001-10000", "Asset Management",
      False, "", "Amundi SA", "AMUN.PA"),
    c("schroders", "Schroders", "schroders.com", "GB", "London", "5001-10000", "Asset Management",
      False, "", "Schroders plc", "SDR.L"),
    c("abrdn", "abrdn", "abrdn.com", "GB", "Edinburgh", "1001-5000", "Asset Management",
      False, "Aberdeen Standard", "abrdn plc", "ABDN.L"),
    c("nordea-asset", "Nordea Asset Management", "nordeaassetmanagement.com", "FI", "Helsinki",
      "201-500", "Asset Management", False, "Nordea Asset Management", "Nordea Investment Management AB"),
    c("axa", "AXA", "axa.com", "FR", "Paris", "10001+", "Insurance",
      False, "AXA Group", "AXA SA", "CS.PA"),
    c("allianz", "Allianz", "allianz.com", "DE", "Munich", "10001+", "Insurance",
      False, "", "Allianz SE", "ALV.DE"),
    c("munich-re", "Munich Re", "munichre.com", "DE", "Munich", "10001+", "Reinsurance",
      False, "Munchener Ruck", "Munchener Ruckversicherungs-Gesellschaft AG", "MUV2.DE"),
    c("hannover-re", "Hannover Re", "hannover-re.com", "DE", "Hanover", "1001-5000", "Reinsurance",
      False, "", "Hannover Ruck SE", "HNR1.DE"),
    c("talanx", "Talanx", "talanx.com", "DE", "Hanover", "10001+", "Insurance",
      False, "HDI", "Talanx AG", "TLX.DE"),
    c("zurich-insurance", "Zurich Insurance Group", "zurich.com", "CH", "Zurich", "10001+", "Insurance",
      False, "Zurich", "Zurich Insurance Group AG", "ZURN.SW"),
    c("swiss-re", "Swiss Re", "swissre.com", "CH", "Zurich", "10001+", "Reinsurance",
      False, "", "Swiss Re AG", "SREN.SW"),
    c("generali", "Generali", "generali.com", "IT", "Trieste", "10001+", "Insurance",
      False, "Assicurazioni Generali", "Assicurazioni Generali S.p.A.", "G.MI"),
    c("unipol", "UnipolSai", "unipol.it", "IT", "Bologna", "10001+", "Insurance",
      False, "Unipol", "UnipolSai Assicurazioni S.p.A.", "US.MI"),
    c("mapfre", "Mapfre", "mapfre.com", "ES", "Madrid", "10001+", "Insurance",
      False, "", "Mapfre S.A.", "MAP.MC"),
    c("aviva", "Aviva", "aviva.com", "GB", "London", "10001+", "Insurance",
      False, "", "Aviva plc", "AV.L"),
    c("legal-and-general", "Legal & General", "group.landg.com", "GB", "London", "10001+", "Insurance",
      False, "Legal and General", "Legal & General Group Plc", "LGEN.L"),
    c("prudential-plc", "Prudential", "prudentialplc.com", "GB", "London", "10001+", "Insurance",
      False, "Prudential plc", "Prudential plc", "PRU.L"),
    c("phoenix-group", "Phoenix Group", "thephoenixgroup.com", "GB", "London", "1001-5000", "Insurance",
      False, "", "Phoenix Group Holdings plc", "PHNX.L"),
    c("admiral-group", "Admiral Group", "admiralgroup.co.uk", "GB", "Cardiff", "5001-10000", "Insurance",
      False, "Admiral", "Admiral Group plc", "ADM.L"),
    c("direct-line", "Direct Line Group", "directlinegroup.co.uk", "GB", "Bromley", "5001-10000", "Insurance",
      False, "Direct Line", "Direct Line Insurance Group plc", "DLG.L"),
    c("aegon", "Aegon", "aegon.com", "NL", "The Hague", "10001+", "Insurance",
      False, "", "Aegon N.V.", "AGN.AS"),
    c("nn-group", "NN Group", "nn-group.com", "NL", "The Hague", "5001-10000", "Insurance",
      False, "Nationale-Nederlanden", "NN Group N.V.", "NN.AS"),
    c("achmea", "Achmea", "achmea.nl", "NL", "Zeist", "10001+", "Insurance",
      False, "", "Achmea B.V."),
    c("ageas", "Ageas", "ageas.com", "BE", "Brussels", "5001-10000", "Insurance",
      False, "", "Ageas SA/NV", "AGS.BR"),
    c("vienna-insurance-group", "Vienna Insurance Group", "vig.com", "AT", "Vienna", "10001+", "Insurance",
      False, "VIG", "Vienna Insurance Group AG", "VIG.VI"),
    c("uniqa", "UNIQA", "uniqa.at", "AT", "Vienna", "5001-10000", "Insurance",
      False, "UNIQA Insurance Group", "UNIQA Insurance Group AG", "UQA.VI"),
    c("triglav-group", "Triglav Group", "triglav.si", "SI", "Ljubljana", "1001-5000", "Insurance",
      False, "Zavarovalnica Triglav", "Zavarovalnica Triglav d.d.", "ZVTG.LJ"),
    c("pzu", "PZU", "pzu.pl", "PL", "Warsaw", "10001+", "Insurance",
      False, "Powszechny Zaklad Ubezpieczen", "Powszechny Zaklad Ubezpieczen S.A.", "PZU.WA"),
    c("ergo-group", "ERGO Group", "ergo.com", "DE", "Dusseldorf", "10001+", "Insurance",
      False, "ERGO", "ERGO Group AG"),
    c("signal-iduna", "Signal Iduna", "signal-iduna.de", "DE", "Dortmund", "10001+", "Insurance",
      False, "", "Signal Iduna Gruppe"),
    c("groupama", "Groupama", "groupama.com", "FR", "Paris", "10001+", "Insurance",
      False, "", "Groupama SA"),
    c("cnp-assurances", "CNP Assurances", "cnp.fr", "FR", "Paris", "5001-10000", "Insurance",
      False, "CNP", "CNP Assurances SA", "CNP.PA"),
    c("covea", "Covea", "covea.fr", "FR", "Paris", "10001+", "Insurance",
      False, "Covéa", "Covea"),
    c("sampo", "Sampo", "sampo.com", "FI", "Helsinki", "1001-5000", "Insurance",
      False, "Sampo Group", "Sampo Oyj", "SAMPO.HE"),
    c("tryg", "Tryg", "tryg.com", "DK", "Ballerup", "5001-10000", "Insurance",
      False, "", "Tryg A/S", "TRYG.CO"),
    c("topdanmark", "Topdanmark", "topdanmark.com", "DK", "Ballerup", "1001-5000", "Insurance",
      False, "", "Topdanmark A/S", "TOP.CO"),
    c("gjensidige", "Gjensidige", "gjensidige.no", "NO", "Oslo", "1001-5000", "Insurance",
      False, "Gjensidige Forsikring", "Gjensidige Forsikring ASA", "GJF.OL"),
    c("storebrand", "Storebrand", "storebrand.no", "NO", "Lysaker", "1001-5000", "Insurance",
      False, "", "Storebrand ASA", "STB.OL"),

    c("deutsche-telekom", "Deutsche Telekom", "telekom.com", "DE", "Bonn", "10001+", "Telecom",
      False, "Telekom", "Deutsche Telekom AG", "DTE.DE"),
    c("vodafone-group", "Vodafone Group", "vodafone.com", "GB", "Newbury", "10001+", "Telecom",
      False, "Vodafone", "Vodafone Group Plc", "VOD.L"),
    c("telefonica", "Telefonica", "telefonica.com", "ES", "Madrid", "10001+", "Telecom",
      False, "Telefónica", "Telefonica S.A.", "TEF.MC"),
    c("orange", "Orange", "orange.com", "FR", "Paris", "10001+", "Telecom",
      False, "Orange Group", "Orange S.A.", "ORA.PA"),
    c("bouygues-telecom", "Bouygues Telecom", "bouyguestelecom.fr", "FR", "Meudon", "5001-10000", "Telecom",
      False, "Bouygues", "Bouygues Telecom SA"),
    c("iliad", "Iliad", "iliad.fr", "FR", "Paris", "5001-10000", "Telecom",
      False, "Free", "Iliad SA", "ILD.PA"),
    c("bt-group", "BT Group", "bt.com", "GB", "London", "10001+", "Telecom",
      False, "BT", "BT Group plc", "BT-A.L"),
    c("virgin-media-o2", "Virgin Media O2", "virginmediao2.co.uk", "GB", "Reading", "10001+", "Telecom",
      False, "VMO2", "Virgin Media O2"),
    c("kpn", "KPN", "kpn.com", "NL", "Rotterdam", "5001-10000", "Telecom",
      False, "Koninklijke KPN", "Koninklijke KPN N.V.", "KPN.AS"),
    c("proximus", "Proximus", "proximus.com", "BE", "Brussels", "5001-10000", "Telecom",
      False, "Belgacom", "Proximus PLC", "PROX.BR"),
    c("telenet", "Telenet", "telenet.be", "BE", "Mechelen", "1001-5000", "Telecom",
      False, "Telenet Group", "Telenet Group Holding NV", "TNET.BR"),
    c("swisscom", "Swisscom", "swisscom.com", "CH", "Bern", "10001+", "Telecom",
      False, "", "Swisscom AG", "SCMN.SW"),
    c("sunrise", "Sunrise", "sunrise.ch", "CH", "Zurich", "1001-5000", "Telecom",
      False, "Sunrise UPC", "Sunrise GmbH"),
    c("a1-telekom-austria", "A1 Telekom Austria", "a1.net", "AT", "Vienna", "1001-5000", "Telecom",
      False, "Telekom Austria", "Telekom Austria AG", "TKA.VI"),
    c("t-mobile-us", "T-Mobile US", "t-mobile.com", "US", "Bellevue", "10001+", "Telecom",
      False, "T-Mobile", "T-Mobile US, Inc.", "TMUS"),
    c("verizon", "Verizon", "verizon.com", "US", "New York", "10001+", "Telecom",
      False, "", "Verizon Communications Inc.", "VZ"),
    c("at-and-t", "AT&T", "att.com", "US", "Dallas", "10001+", "Telecom",
      False, "ATT", "AT&T Inc.", "T"),
    c("telia-company", "Telia Company", "telia.com", "SE", "Solna", "10001+", "Telecom",
      False, "Telia", "Telia Company AB", "TELIA.ST"),
    c("telenor", "Telenor", "telenor.com", "NO", "Fornebu", "10001+", "Telecom",
      False, "Telenor Group", "Telenor ASA", "TEL.OL"),
    c("elisa", "Elisa", "elisa.com", "FI", "Espoo", "1001-5000", "Telecom",
      False, "Elisa Oyj", "Elisa Oyj", "ELISA.HE"),
    c("telecom-italia", "Telecom Italia", "gruppotim.it", "IT", "Milan", "10001+", "Telecom",
      False, "TIM", "Telecom Italia S.p.A.", "TIT.MI"),
    c("fastweb", "Fastweb", "fastweb.it", "IT", "Milan", "1001-5000", "Telecom",
      False, "", "Fastweb S.p.A."),
    c("orange-polska", "Orange Polska", "orange.pl", "PL", "Warsaw", "5001-10000", "Telecom",
      False, "", "Orange Polska S.A.", "OPL.WA"),
    c("o2-czech", "O2 Czech Republic", "o2.cz", "CZ", "Prague", "1001-5000", "Telecom",
      False, "O2 Czech", "O2 Czech Republic a.s."),
    c("slovak-telekom", "Slovak Telekom", "telekom.sk", "SK", "Bratislava", "1001-5000", "Telecom",
      False, "", "Slovak Telekom, a.s."),
    c("magyar-telekom", "Magyar Telekom", "telekom.hu", "HU", "Budapest", "1001-5000", "Telecom",
      False, "", "Magyar Telekom Tavkozlesi Nyrt", "MTEL.BD"),
    c("hrvatski-telekom", "Hrvatski Telekom", "hrvatskitelekom.hr", "HR", "Zagreb", "1001-5000", "Telecom",
      False, "HT", "Hrvatski Telekom d.d.", "HT.ZA"),
    c("telekom-slovenije", "Telekom Slovenije", "telekom.si", "SI", "Ljubljana", "1001-5000", "Telecom",
      False, "", "Telekom Slovenije, d.d.", "TLSG.LJ"),
    c("vivacom", "Vivacom", "vivacom.bg", "BG", "Sofia", "1001-5000", "Telecom",
      False, "Bulgarian Telecommunications Company", "Vivacom Bulgaria EAD"),
    c("cyta", "Cyta", "cyta.com.cy", "CY", "Nicosia", "1001-5000", "Telecom",
      False, "Cyprus Telecommunications Authority", "Cyta"),
    c("moldtelecom", "Moldtelecom", "moldtelecom.md", "MD", "Chisinau", "1001-5000", "Telecom",
      False, "", "Moldtelecom S.A."),
    c("orange-moldova", "Orange Moldova", "orange.md", "MD", "Chisinau", "1001-5000", "Telecom",
      False, "", "Orange Moldova S.A."),
    c("cellnex", "Cellnex Telecom", "cellnextelecom.com", "ES", "Barcelona", "1001-5000", "Telecom Infrastructure",
      False, "Cellnex", "Cellnex Telecom S.A.", "CLNX.MC"),
    c("ericsson", "Ericsson", "ericsson.com", "SE", "Stockholm", "10001+", "Telecom Equipment",
      False, "Telefonaktiebolaget LM Ericsson", "Telefonaktiebolaget LM Ericsson", "ERIC-B.ST"),
    c("nokia", "Nokia", "nokia.com", "FI", "Espoo", "10001+", "Telecom Equipment",
      False, "", "Nokia Corporation", "NOKIA.HE"),
    c("sinch", "Sinch", "sinch.com", "SE", "Stockholm", "1001-5000", "Communications Platform",
      False, "", "Sinch AB", "SINCH.ST"),

    c("siemens", "Siemens", "siemens.com", "DE", "Munich", "10001+", "Industrial Technology",
      False, "Siemens AG", "Siemens AG", "SIE.DE"),
    c("bosch", "Bosch", "bosch.com", "DE", "Gerlingen", "10001+", "Industrial Technology",
      False, "Robert Bosch", "Robert Bosch GmbH"),
    c("continental", "Continental", "continental.com", "DE", "Hanover", "10001+", "Automotive Supplier",
      False, "Continental AG", "Continental AG", "CON.DE"),
    c("zf", "ZF Friedrichshafen", "zf.com", "DE", "Friedrichshafen", "10001+", "Automotive Supplier",
      False, "ZF", "ZF Friedrichshafen AG"),
    c("schaeffler", "Schaeffler", "schaeffler.com", "DE", "Herzogenaurach", "10001+", "Automotive Supplier",
      False, "", "Schaeffler AG", "SHA.DE"),
    c("thyssenkrupp", "thyssenkrupp", "thyssenkrupp.com", "DE", "Essen", "10001+", "Industrial Conglomerate",
      False, "ThyssenKrupp", "thyssenkrupp AG", "TKA.DE"),
    c("basf", "BASF", "basf.com", "DE", "Ludwigshafen", "10001+", "Chemicals",
      False, "", "BASF SE", "BAS.DE"),
    c("bayer", "Bayer", "bayer.com", "DE", "Leverkusen", "10001+", "Pharmaceuticals",
      False, "", "Bayer AG", "BAYN.DE"),
    c("merck-kgaa", "Merck KGaA", "merckgroup.com", "DE", "Darmstadt", "10001+", "Life Sciences",
      False, "Merck Group", "Merck KGaA", "MRK.DE"),
    c("henkel", "Henkel", "henkel.com", "DE", "Dusseldorf", "10001+", "Consumer Goods",
      False, "", "Henkel AG & Co. KGaA", "HEN3.DE"),
    c("beiersdorf", "Beiersdorf", "beiersdorf.com", "DE", "Hamburg", "10001+", "Consumer Goods",
      False, "NIVEA", "Beiersdorf AG", "BEI.DE"),
    c("linde", "Linde", "linde.com", "GB", "Woking", "10001+", "Industrial Gases",
      False, "Linde plc", "Linde plc", "LIN"),
    c("krones", "Krones", "krones.com", "DE", "Neutraubling", "10001+", "Industrial Machinery",
      False, "", "Krones AG", "KRN.DE"),
    c("trumpf", "TRUMPF", "trumpf.com", "DE", "Ditzingen", "10001+", "Industrial Machinery",
      False, "Trumpf", "TRUMPF SE + Co. KG"),
    c("festo", "Festo", "festo.com", "DE", "Esslingen", "10001+", "Industrial Automation",
      False, "", "Festo SE & Co. KG"),
    c("sew-eurodrive", "SEW-EURODRIVE", "sew-eurodrive.com", "DE", "Bruchsal", "10001+", "Industrial Automation",
      False, "SEW Eurodrive", "SEW-EURODRIVE GmbH & Co KG"),
    c("voith", "Voith", "voith.com", "DE", "Heidenheim", "10001+", "Industrial Machinery",
      False, "", "Voith GmbH & Co. KGaA"),
    c("miele", "Miele", "miele.com", "DE", "Gutersloh", "10001+", "Consumer Appliances",
      False, "", "Miele & Cie. KG"),
    c("volkswagen-group", "Volkswagen Group", "volkswagen-group.com", "DE", "Wolfsburg", "10001+", "Automotive",
      False, "VW", "Volkswagen AG", "VOW3.DE"),
    c("bmw-group", "BMW Group", "bmwgroup.com", "DE", "Munich", "10001+", "Automotive",
      False, "BMW", "Bayerische Motoren Werke AG", "BMW.DE"),
    c("mercedes-benz-group", "Mercedes-Benz Group", "mercedes-benz.com", "DE", "Stuttgart", "10001+", "Automotive",
      False, "Mercedes-Benz|Daimler", "Mercedes-Benz Group AG", "MBG.DE"),
    c("porsche", "Porsche", "porsche.com", "DE", "Stuttgart", "10001+", "Automotive",
      False, "Porsche AG", "Dr. Ing. h.c. F. Porsche AG", "P911.DE"),
    c("airbus", "Airbus", "airbus.com", "FR", "Blagnac", "10001+", "Aerospace",
      False, "Airbus Group", "Airbus SE", "AIR.PA"),
    c("safran", "Safran", "safran-group.com", "FR", "Paris", "10001+", "Aerospace",
      False, "", "Safran SA", "SAF.PA"),
    c("thales", "Thales", "thalesgroup.com", "FR", "Paris", "10001+", "Defence Electronics",
      False, "Thales Group", "Thales SA", "HO.PA"),
    c("dassault-systemes", "Dassault Systemes", "3ds.com", "FR", "Velizy-Villacoublay", "10001+", "Software",
      False, "Dassault Systèmes", "Dassault Systemes SE", "DSY.PA"),
    c("michelin", "Michelin", "michelin.com", "FR", "Clermont-Ferrand", "10001+", "Automotive Supplier",
      False, "Compagnie Generale des Etablissements Michelin", "Compagnie Generale des Etablissements Michelin", "ML.PA"),
    c("renault", "Renault", "renaultgroup.com", "FR", "Boulogne-Billancourt", "10001+", "Automotive",
      False, "Renault Group", "Renault SA", "RNO.PA"),
    c("stellantis", "Stellantis", "stellantis.com", "NL", "Hoofddorp", "10001+", "Automotive",
      False, "", "Stellantis N.V.", "STLAP.PA"),
    c("volvo-group", "Volvo Group", "volvogroup.com", "SE", "Gothenburg", "10001+", "Automotive",
      False, "Volvo Trucks", "AB Volvo", "VOLV-B.ST"),
    c("volvo-cars", "Volvo Cars", "volvocars.com", "SE", "Gothenburg", "10001+", "Automotive",
      False, "", "Volvo Car AB", "VOLCAR-B.ST"),
    c("scania", "Scania", "scania.com", "SE", "Sodertalje", "10001+", "Automotive",
      False, "", "Scania CV AB"),
    c("abb", "ABB", "abb.com", "CH", "Zurich", "10001+", "Industrial Automation",
      False, "", "ABB Ltd", "ABBN.SW"),
    c("schneider-electric", "Schneider Electric", "se.com", "FR", "Rueil-Malmaison", "10001+", "Industrial Automation",
      False, "", "Schneider Electric SE", "SU.PA"),
    c("legrand", "Legrand", "legrand.com", "FR", "Limoges", "10001+", "Electrical Equipment",
      False, "", "Legrand SA", "LR.PA"),
    c("rexel", "Rexel", "rexel.com", "FR", "Paris", "10001+", "Electrical Distribution",
      False, "", "Rexel SA", "RXL.PA"),
    c("philips", "Philips", "philips.com", "NL", "Amsterdam", "10001+", "Health Technology",
      False, "Koninklijke Philips", "Koninklijke Philips N.V.", "PHIA.AS"),
    c("asml", "ASML", "asml.com", "NL", "Veldhoven", "10001+", "Semiconductor Equipment",
      False, "", "ASML Holding N.V.", "ASML.AS"),
    c("signify", "Signify", "signify.com", "NL", "Eindhoven", "10001+", "Lighting",
      False, "Philips Lighting", "Signify N.V.", "LIGHT.AS"),
    c("vdl-groep", "VDL Groep", "vdlgroep.nl", "NL", "Eindhoven", "10001+", "Manufacturing",
      False, "VDL", "VDL Groep B.V."),
    c("damen-shipyards", "Damen Shipyards", "damen.com", "NL", "Gorinchem", "5001-10000", "Shipbuilding",
      False, "Damen Shipyards Group", "Damen Shipyards Group N.V."),
    c("rolls-royce", "Rolls-Royce", "rolls-royce.com", "GB", "London", "10001+", "Aerospace",
      False, "Rolls-Royce Holdings", "Rolls-Royce Holdings plc", "RR.L"),
    c("bae-systems", "BAE Systems", "baesystems.com", "GB", "Farnborough", "10001+", "Defence",
      False, "", "BAE Systems plc", "BA.L"),
    c("melrose-industries", "Melrose Industries", "melroseplc.net", "GB", "London", "10001+", "Aerospace",
      False, "GKN", "Melrose Industries PLC", "MRO.L"),
    c("renishaw", "Renishaw", "renishaw.com", "GB", "Wotton-under-Edge", "1001-5000", "Precision Engineering",
      False, "", "Renishaw plc", "RSW.L"),
    c("imi", "IMI", "imiplc.com", "GB", "Birmingham", "5001-10000", "Industrial Engineering",
      False, "", "IMI plc", "IMI.L"),
    c("smiths-group", "Smiths Group", "smiths.com", "GB", "London", "5001-10000", "Industrial Technology",
      False, "", "Smiths Group plc", "SMIN.L"),
    c("spirax-sarco", "Spirax-Sarco Engineering", "spiraxsarco.com", "GB", "Cheltenham", "5001-10000",
      "Industrial Engineering", False, "Spirax Sarco", "Spirax-Sarco Engineering plc", "SPX.L"),
    c("halma", "Halma", "halma.com", "GB", "Amersham", "5001-10000", "Safety Equipment",
      False, "", "Halma plc", "HLMA.L"),
    c("weir-group", "Weir Group", "global.weir", "GB", "Glasgow", "10001+", "Industrial Engineering",
      False, "The Weir Group", "The Weir Group PLC", "WEIR.L"),
    c("leonardo", "Leonardo", "leonardo.com", "IT", "Rome", "10001+", "Defence",
      False, "Leonardo S.p.A.", "Leonardo S.p.A.", "LDO.MI"),
    c("fincantieri", "Fincantieri", "fincantieri.com", "IT", "Trieste", "10001+", "Shipbuilding",
      False, "", "Fincantieri S.p.A.", "FCT.MI"),
    c("pirelli", "Pirelli", "pirelli.com", "IT", "Milan", "10001+", "Automotive Supplier",
      False, "Pirelli & C.", "Pirelli & C. S.p.A.", "PIRC.MI"),
    c("prysmian", "Prysmian", "prysmiangroup.com", "IT", "Milan", "10001+", "Cables",
      False, "Prysmian Group", "Prysmian S.p.A.", "PRY.MI"),
    c("brembo", "Brembo", "brembo.com", "IT", "Stezzano", "10001+", "Automotive Supplier",
      False, "", "Brembo S.p.A.", "BRE.MI"),
    c("ferrari", "Ferrari", "ferrari.com", "IT", "Maranello", "1001-5000", "Automotive",
      False, "Ferrari N.V.", "Ferrari N.V.", "RACE"),
    c("cnh-industrial", "CNH Industrial", "cnhindustrial.com", "GB", "Basildon", "10001+", "Industrial Machinery",
      False, "CNH", "CNH Industrial N.V.", "CNH"),
    c("iveco-group", "Iveco Group", "ivecogroup.com", "IT", "Turin", "10001+", "Automotive",
      False, "Iveco", "Iveco Group N.V.", "IVG.MI"),
    c("sandvik", "Sandvik", "home.sandvik", "SE", "Stockholm", "10001+", "Industrial Engineering",
      False, "", "Sandvik AB", "SAND.ST"),
    c("atlas-copco", "Atlas Copco", "atlascopco.com", "SE", "Nacka", "10001+", "Industrial Equipment",
      False, "", "Atlas Copco AB", "ATCO-A.ST"),
    c("skf", "SKF", "skf.com", "SE", "Gothenburg", "10001+", "Industrial Engineering",
      False, "AB SKF", "AB SKF", "SKF-B.ST"),
    c("assa-abloy", "Assa Abloy", "assaabloy.com", "SE", "Stockholm", "10001+", "Access Solutions",
      False, "ASSA ABLOY", "Assa Abloy AB", "ASSA-B.ST"),
    c("alfa-laval", "Alfa Laval", "alfalaval.com", "SE", "Lund", "10001+", "Industrial Equipment",
      False, "", "Alfa Laval AB", "ALFA.ST"),
    c("tetra-pak", "Tetra Pak", "tetrapak.com", "SE", "Lausanne", "10001+", "Packaging",
      False, "", "Tetra Pak"),
    c("securitas", "Securitas", "securitas.com", "SE", "Stockholm", "10001+", "Security Services",
      False, "", "Securitas AB", "SECU-B.ST"),
    c("vestas", "Vestas", "vestas.com", "DK", "Aarhus", "10001+", "Wind Energy",
      False, "Vestas Wind Systems", "Vestas Wind Systems A/S", "VWS.CO"),
    c("danfoss", "Danfoss", "danfoss.com", "DK", "Nordborg", "10001+", "Industrial Automation",
      False, "", "Danfoss A/S"),
    c("grundfos", "Grundfos", "grundfos.com", "DK", "Bjerringbro", "10001+", "Pumps",
      False, "", "Grundfos Holding A/S"),
    c("novonesis", "Novonesis", "novonesis.com", "DK", "Bagsvaerd", "10001+", "Biotechnology",
      False, "Novozymes|Chr. Hansen", "Novonesis A/S", "NSIS-B.CO"),
    c("flsmidth", "FLSmidth", "flsmidth.com", "DK", "Copenhagen", "10001+", "Industrial Engineering",
      False, "FL Smidth", "FLSmidth & Co. A/S", "FLS.CO"),
    c("kone", "KONE", "kone.com", "FI", "Espoo", "10001+", "Elevators",
      False, "", "KONE Oyj", "KNEBV.HE"),
    c("wartsila", "Wartsila", "wartsila.com", "FI", "Helsinki", "10001+", "Industrial Machinery",
      False, "Wärtsilä", "Wartsila Oyj Abp", "WRT1V.HE"),
    c("metso", "Metso", "metso.com", "FI", "Espoo", "10001+", "Industrial Machinery",
      False, "", "Metso Oyj", "METSO.HE"),
    c("valmet", "Valmet", "valmet.com", "FI", "Espoo", "10001+", "Industrial Machinery",
      False, "", "Valmet Oyj", "VALMT.HE"),
    c("stora-enso", "Stora Enso", "storaenso.com", "FI", "Helsinki", "10001+", "Forest Products",
      False, "", "Stora Enso Oyj", "STERV.HE"),
    c("upm", "UPM", "upm.com", "FI", "Helsinki", "10001+", "Forest Products",
      False, "UPM-Kymmene", "UPM-Kymmene Oyj", "UPM.HE"),
    c("andritz", "Andritz", "andritz.com", "AT", "Graz", "10001+", "Industrial Machinery",
      False, "", "Andritz AG", "ANDR.VI"),
    c("voestalpine", "voestalpine", "voestalpine.com", "AT", "Linz", "10001+", "Steel",
      False, "", "voestalpine AG", "VOE.VI"),
    c("wienerberger", "Wienerberger", "wienerberger.com", "AT", "Vienna", "10001+", "Building Materials",
      False, "", "Wienerberger AG", "WIE.VI"),
    c("palfinger", "Palfinger", "palfinger.com", "AT", "Bergheim", "1001-5000", "Industrial Machinery",
      False, "", "Palfinger AG", "PAL.VI"),
    c("rosenbauer", "Rosenbauer", "rosenbauer.com", "AT", "Leonding", "1001-5000", "Fire Fighting Equipment",
      False, "", "Rosenbauer International AG", "ROS.VI"),
    c("ats", "AT&S", "ats.net", "AT", "Leoben", "5001-10000", "Electronics",
      False, "AT&S Austria Technologie & Systemtechnik", "AT & S Austria Technologie & Systemtechnik Aktiengesellschaft", "ATS.VI"),
    c("lego", "LEGO", "lego.com", "DK", "Billund", "10001+", "Toys",
      False, "LEGO Group", "LEGO A/S"),
    c("arcelik", "Arcelik", "arcelikglobal.com", "TR", "Istanbul", "10001+", "Consumer Appliances",
      False, "Beko", "Arcelik A.S.", "ARCLK.IS"),
    c("koc-holding", "Koc Holding", "koc.com.tr", "TR", "Istanbul", "10001+", "Conglomerate",
      False, "Koç Holding", "Koc Holding A.S.", "KCHOL.IS"),
    c("turkcell", "Turkcell", "turkcell.com.tr", "TR", "Istanbul", "10001+", "Telecom",
      False, "", "Turkcell Iletisim Hizmetleri A.S.", "TCELL.IS"),
    c("aselsan", "Aselsan", "aselsan.com", "TR", "Ankara", "5001-10000", "Defence Electronics",
      False, "", "Aselsan Elektronik Sanayi ve Ticaret A.S.", "ASELS.IS"),
    c("cez", "CEZ Group", "cez.cz", "CZ", "Prague", "10001+", "Utilities",
      False, "CEZ", "CEZ, a. s.", "CEZ.PR"),
    c("skoda-auto", "Skoda Auto", "skoda-auto.com", "CZ", "Mlada Boleslav", "10001+", "Automotive",
      False, "Škoda", "Skoda Auto a.s."),
    c("skanska", "Skanska", "group.skanska.com", "SE", "Stockholm", "10001+", "Construction",
      False, "", "Skanska AB", "SKA-B.ST"),
    c("vinci", "VINCI", "vinci.com", "FR", "Nanterre", "10001+", "Construction",
      False, "", "VINCI SA", "DG.PA"),
    c("bouygues", "Bouygues", "bouygues.com", "FR", "Paris", "10001+", "Construction",
      False, "Bouygues Group", "Bouygues SA", "EN.PA"),
    c("eiffage", "Eiffage", "eiffage.com", "FR", "Velizy-Villacoublay", "10001+", "Construction",
      False, "", "Eiffage SA", "FGR.PA"),
    c("ferrovial", "Ferrovial", "ferrovial.com", "ES", "Madrid", "10001+", "Infrastructure",
      False, "", "Ferrovial SE", "FER.MC"),
    c("acs", "ACS", "grupoacs.com", "ES", "Madrid", "10001+", "Construction",
      False, "Actividades de Construccion y Servicios", "ACS, Actividades de Construccion y Servicios, S.A.", "ACS.MC"),
    c("acciona", "Acciona", "acciona.com", "ES", "Alcobendas", "10001+", "Infrastructure",
      False, "", "Acciona, S.A.", "ANA.MC"),
    c("sika", "Sika", "sika.com", "CH", "Baar", "10001+", "Building Materials",
      False, "", "Sika AG", "SIKA.SW"),
    c("geberit", "Geberit", "geberit.com", "CH", "Rapperswil-Jona", "5001-10000", "Building Materials",
      False, "", "Geberit AG", "GEBN.SW"),
    c("holcim", "Holcim", "holcim.com", "CH", "Zug", "10001+", "Building Materials",
      False, "LafargeHolcim", "Holcim Ltd", "HOLN.SW"),
    c("heidelberg-materials", "Heidelberg Materials", "heidelbergmaterials.com", "DE", "Heidelberg", "10001+",
      "Building Materials", False, "HeidelbergCement", "Heidelberg Materials AG", "HEI.DE"),
    c("crh", "CRH", "crh.com", "IE", "Dublin", "10001+", "Building Materials",
      False, "", "CRH plc", "CRH"),
    c("kingspan", "Kingspan", "kingspan.com", "IE", "Kingscourt", "10001+", "Building Materials",
      False, "", "Kingspan Group plc", "KRX.IR"),
    c("kerry-group", "Kerry Group", "kerry.com", "IE", "Tralee", "10001+", "Food Ingredients",
      False, "", "Kerry Group plc", "KYGA.IR"),
    c("glanbia", "Glanbia", "glanbia.com", "IE", "Kilkenny", "5001-10000", "Food",
      False, "", "Glanbia plc", "GL9.IR"),

    c("nestle", "Nestle", "nestle.com", "CH", "Vevey", "10001+", "Food & Beverage",
      False, "Nestlé", "Nestle S.A.", "NESN.SW"),
    c("danone", "Danone", "danone.com", "FR", "Paris", "10001+", "Food & Beverage",
      False, "", "Danone SA", "BN.PA"),
    c("unilever", "Unilever", "unilever.com", "GB", "London", "10001+", "Consumer Goods",
      False, "", "Unilever PLC", "ULVR.L"),
    c("reckitt", "Reckitt", "reckitt.com", "GB", "Slough", "10001+", "Consumer Goods",
      False, "Reckitt Benckiser", "Reckitt Benckiser Group plc", "RKT.L"),
    c("diageo", "Diageo", "diageo.com", "GB", "London", "10001+", "Beverages",
      False, "", "Diageo plc", "DGE.L"),
    c("ab-inbev", "AB InBev", "ab-inbev.com", "BE", "Leuven", "10001+", "Beverages",
      False, "Anheuser-Busch InBev", "Anheuser-Busch InBev SA/NV", "ABI.BR"),
    c("heineken", "Heineken", "heineken.com", "NL", "Amsterdam", "10001+", "Beverages",
      False, "Heineken N.V.", "Heineken N.V.", "HEIA.AS"),
    c("carlsberg", "Carlsberg", "carlsberg.com", "DK", "Copenhagen", "10001+", "Beverages",
      False, "Carlsberg Group", "Carlsberg A/S", "CARL-B.CO"),
    c("coca-cola-hbc", "Coca-Cola HBC", "coca-colahellenic.com", "CH", "Zug", "10001+", "Beverages",
      False, "Coca-Cola Hellenic", "Coca-Cola HBC AG", "CCH.L"),
    c("mondelez", "Mondelez International", "mondelezinternational.com", "US", "Chicago", "10001+",
      "Food & Beverage", False, "Mondelēz", "Mondelez International, Inc.", "MDLZ"),
    c("pepsico", "PepsiCo", "pepsico.com", "US", "Purchase", "10001+", "Food & Beverage",
      False, "", "PepsiCo, Inc.", "PEP"),
    c("coca-cola-company", "The Coca-Cola Company", "coca-colacompany.com", "US", "Atlanta", "10001+",
      "Beverages", False, "Coca-Cola", "The Coca-Cola Company", "KO"),
    c("ica-gruppen", "ICA Gruppen", "icagruppen.se", "SE", "Solna", "10001+", "Retail",
      False, "ICA", "ICA Gruppen AB"),
    c("axfood", "Axfood", "axfood.se", "SE", "Solna", "5001-10000", "Retail",
      False, "", "Axfood AB", "AXFO.ST"),
    c("orkla", "Orkla", "orkla.com", "NO", "Oslo", "10001+", "Consumer Goods",
      False, "", "Orkla ASA", "ORK.OL"),

    c("carrefour", "Carrefour", "carrefour.com", "FR", "Massy", "10001+", "Retail",
      False, "Carrefour Group", "Carrefour SA", "CA.PA"),
    c("tesco", "Tesco", "tesco.com", "GB", "Welwyn Garden City", "10001+", "Retail",
      False, "Tesco PLC", "Tesco PLC", "TSCO.L"),
    c("sainsburys", "Sainsbury's", "sainsburys.co.uk", "GB", "London", "10001+", "Retail",
      False, "J Sainsbury", "J Sainsbury plc", "SBRY.L"),
    c("marks-and-spencer", "Marks & Spencer", "marksandspencer.com", "GB", "London", "10001+", "Retail",
      False, "M&S", "Marks and Spencer Group plc", "MKS.L"),
    c("kingfisher", "Kingfisher", "kingfisher.com", "GB", "London", "10001+", "Retail",
      False, "B&Q|Castorama", "Kingfisher plc", "KGF.L"),
    c("next", "Next", "nextplc.co.uk", "GB", "Enderby", "10001+", "Retail",
      False, "Next plc", "Next plc", "NXT.L"),
    c("jd-sports", "JD Sports Fashion", "jdsports.co.uk", "GB", "Bury", "10001+", "Retail",
      False, "JD Sports", "JD Sports Fashion Plc", "JD.L"),
    c("ahold-delhaize", "Ahold Delhaize", "aholddelhaize.com", "NL", "Zaandam", "10001+", "Retail",
      False, "Albert Heijn|Delhaize", "Koninklijke Ahold Delhaize N.V.", "AD.AS"),
    c("jumbo", "Jumbo", "jumbo.com", "NL", "Veghel", "10001+", "Retail",
      False, "Jumbo Supermarkten", "Jumbo Supermarkten B.V."),
    c("colruyt", "Colruyt Group", "colruytgroup.com", "BE", "Halle", "10001+", "Retail",
      False, "Colruyt", "Etn. Fr. Colruyt NV", "COLR.BR"),
    c("metro-ag", "Metro", "metroag.de", "DE", "Dusseldorf", "10001+", "Wholesale",
      False, "METRO AG", "METRO AG", "B4B.DE"),
    c("edeka", "EDEKA", "edeka.de", "DE", "Hamburg", "10001+", "Retail",
      False, "Edeka Group", "EDEKA Zentralhandelsgesellschaft mbH"),
    c("rewe-group", "REWE Group", "rewe-group.com", "DE", "Cologne", "10001+", "Retail",
      False, "REWE", "REWE Group"),
    c("schwarz-group", "Schwarz Group", "gruppe.schwarz", "DE", "Neckarsulm", "10001+", "Retail",
      False, "Lidl|Kaufland", "Schwarz Group"),
    c("otto-group", "Otto Group", "ottogroup.com", "DE", "Hamburg", "10001+", "E-Commerce",
      False, "OTTO", "Otto Group"),
    c("zalando", "Zalando", "zalando.com", "DE", "Berlin", "10001+", "E-Commerce",
      False, "", "Zalando SE", "ZAL.DE"),
    c("ceconomy", "Ceconomy", "ceconomy.de", "DE", "Dusseldorf", "10001+", "Retail",
      False, "MediaMarkt|Saturn", "Ceconomy AG", "CEC.DE"),
    c("hugo-boss", "Hugo Boss", "hugoboss.com", "DE", "Metzingen", "10001+", "Apparel",
      False, "BOSS", "Hugo Boss AG", "BOSS.DE"),
    c("adidas", "adidas", "adidas-group.com", "DE", "Herzogenaurach", "10001+", "Apparel",
      False, "Adidas AG", "adidas AG", "ADS.DE"),
    c("puma", "Puma", "puma.com", "DE", "Herzogenaurach", "10001+", "Apparel",
      False, "PUMA SE", "PUMA SE", "PUM.DE"),
    c("inditex", "Inditex", "inditex.com", "ES", "Arteixo", "10001+", "Apparel",
      False, "Zara", "Industria de Diseno Textil, S.A.", "ITX.MC"),
    c("el-corte-ingles", "El Corte Ingles", "elcorteingles.es", "ES", "Madrid", "10001+", "Retail",
      False, "El Corte Inglés", "El Corte Ingles S.A."),
    c("mercadona", "Mercadona", "mercadona.es", "ES", "Valencia", "10001+", "Retail",
      False, "", "Mercadona S.A."),
    c("h-and-m", "H&M", "hm.com", "SE", "Stockholm", "10001+", "Apparel",
      False, "Hennes & Mauritz", "H & M Hennes & Mauritz AB", "HM-B.ST"),
    c("ikea", "IKEA", "ikea.com", "SE", "Delft", "10001+", "Retail",
      False, "Inter IKEA", "Inter IKEA Systems B.V."),
    c("electrolux", "Electrolux", "electrolux.com", "SE", "Stockholm", "10001+", "Consumer Appliances",
      False, "AB Electrolux", "AB Electrolux", "ELUX-B.ST"),
    c("casino", "Casino Guichard-Perrachon", "groupe-casino.fr", "FR", "Saint-Etienne", "10001+", "Retail",
      False, "Casino", "Casino Guichard-Perrachon SA", "CO.PA"),
    c("auchan", "Auchan", "auchan.fr", "FR", "Croix", "10001+", "Retail",
      False, "Auchan Retail", "Auchan Retail"),
    c("decathlon", "Decathlon", "decathlon.com", "FR", "Villeneuve-d'Ascq", "10001+", "Retail",
      False, "", "Decathlon SA"),
    c("lvmh", "LVMH", "lvmh.com", "FR", "Paris", "10001+", "Luxury Goods",
      False, "Louis Vuitton Moet Hennessy", "LVMH Moet Hennessy Louis Vuitton SE", "MC.PA"),
    c("kering", "Kering", "kering.com", "FR", "Paris", "10001+", "Luxury Goods",
      False, "Gucci", "Kering SA", "KER.PA"),
    c("hermes", "Hermes International", "hermes.com", "FR", "Paris", "10001+", "Luxury Goods",
      False, "Hermès", "Hermes International SCA", "RMS.PA"),
    c("richemont", "Richemont", "richemont.com", "CH", "Bellevue", "10001+", "Luxury Goods",
      False, "Compagnie Financiere Richemont", "Compagnie Financiere Richemont SA", "CFR.SW"),
    c("essilorluxottica", "EssilorLuxottica", "essilorluxottica.com", "FR", "Charenton-le-Pont", "10001+",
      "Eyewear", False, "Essilor", "EssilorLuxottica SA", "EL.PA"),
    c("migros", "Migros", "migros.ch", "CH", "Zurich", "10001+", "Retail",
      False, "Migros-Genossenschafts-Bund", "Migros-Genossenschafts-Bund"),
    c("coop-switzerland", "Coop", "coop.ch", "CH", "Basel", "10001+", "Retail",
      False, "Coop Gruppe", "Coop Genossenschaft"),
    c("jeronimo-martins", "Jeronimo Martins", "jeronimomartins.com", "PT", "Lisbon", "10001+", "Retail",
      False, "Biedronka|Pingo Doce", "Jeronimo Martins, SGPS, S.A.", "JMT.LS"),
    c("sonae", "Sonae", "sonae.pt", "PT", "Maia", "10001+", "Retail",
      False, "", "Sonae, SGPS, S.A.", "SON.LS"),
    c("walmart", "Walmart", "walmart.com", "US", "Bentonville", "10001+", "Retail",
      False, "", "Walmart Inc.", "WMT"),
    c("target", "Target", "target.com", "US", "Minneapolis", "10001+", "Retail",
      False, "Target Corporation", "Target Corporation", "TGT"),
    c("kroger", "The Kroger Co.", "kroger.com", "US", "Cincinnati", "10001+", "Retail",
      False, "Kroger", "The Kroger Co.", "KR"),
    c("costco", "Costco", "costco.com", "US", "Issaquah", "10001+", "Retail",
      False, "Costco Wholesale", "Costco Wholesale Corporation", "COST"),
    c("home-depot", "The Home Depot", "homedepot.com", "US", "Atlanta", "10001+", "Retail",
      False, "Home Depot", "The Home Depot, Inc.", "HD"),
    c("nike", "Nike", "nike.com", "US", "Beaverton", "10001+", "Apparel",
      False, "Nike Inc", "NIKE, Inc.", "NKE"),
    c("alibaba", "Alibaba Group", "alibabagroup.com", "CN", "Hangzhou", "10001+", "E-Commerce",
      False, "Alibaba", "Alibaba Group Holding Limited", "BABA"),
    c("jd-com", "JD.com", "jd.com", "CN", "Beijing", "10001+", "E-Commerce",
      False, "JD", "JD.com, Inc.", "JD"),
    c("rakuten", "Rakuten Group", "rakuten.com", "JP", "Tokyo", "10001+", "E-Commerce",
      False, "Rakuten", "Rakuten Group, Inc.", "4755.T"),
    c("dino-polska", "Dino Polska", "grupadino.pl", "PL", "Krotoszyn", "10001+", "Retail",
      False, "Dino", "Dino Polska S.A.", "DNP.WA"),
    c("lpp", "LPP", "lpp.com", "PL", "Gdansk", "10001+", "Apparel",
      False, "Reserved", "LPP SA", "LPP.WA"),
    c("ccc", "CCC", "ccc.eu", "PL", "Polkowice", "10001+", "Footwear Retail",
      False, "", "CCC S.A.", "CCC.WA"),
    c("allegro", "Allegro", "allegro.eu", "PL", "Poznan", "5001-10000", "E-Commerce",
      False, "Allegro.eu", "Allegro.eu SA", "ALE.WA"),
    c("emag", "eMAG", "emag.ro", "RO", "Bucharest", "5001-10000", "E-Commerce",
      False, "", "eMAG International S.A."),
    c("dedeman", "Dedeman", "dedeman.ro", "RO", "Bacau", "10001+", "Retail",
      False, "", "Dedeman S.R.L."),
    c("maxima", "Maxima Grupe", "maxima.lt", "LT", "Vilnius", "10001+", "Retail",
      False, "Maxima", "Maxima Grupe UAB"),
    c("rimi-baltic", "Rimi Baltic", "rimi.lv", "LV", "Riga", "10001+", "Retail",
      False, "Rimi", "Rimi Baltic AB"),

    c("novartis", "Novartis", "novartis.com", "CH", "Basel", "10001+", "Pharmaceuticals",
      False, "", "Novartis AG", "NOVN.SW"),
    c("roche", "Roche", "roche.com", "CH", "Basel", "10001+", "Pharmaceuticals",
      False, "F. Hoffmann-La Roche", "Roche Holding AG", "ROG.SW"),
    c("boehringer-ingelheim", "Boehringer Ingelheim", "boehringer-ingelheim.com", "DE", "Ingelheim", "10001+",
      "Pharmaceuticals", False, "", "Boehringer Ingelheim International GmbH"),
    c("sanofi", "Sanofi", "sanofi.com", "FR", "Paris", "10001+", "Pharmaceuticals",
      False, "", "Sanofi", "SAN.PA"),
    c("ipsen", "Ipsen", "ipsen.com", "FR", "Paris", "1001-5000", "Pharmaceuticals",
      False, "", "Ipsen S.A.", "IPN.PA"),
    c("servier", "Servier", "servier.com", "FR", "Suresnes", "5001-10000", "Pharmaceuticals",
      False, "", "Servier"),
    c("astrazeneca", "AstraZeneca", "astrazeneca.com", "GB", "Cambridge", "10001+", "Pharmaceuticals",
      False, "", "AstraZeneca PLC", "AZN.L"),
    c("gsk", "GSK", "gsk.com", "GB", "London", "10001+", "Pharmaceuticals",
      False, "GlaxoSmithKline", "GSK plc", "GSK.L"),
    c("novo-nordisk", "Novo Nordisk", "novonordisk.com", "DK", "Bagsvaerd", "10001+", "Pharmaceuticals",
      False, "", "Novo Nordisk A/S", "NOVO-B.CO"),
    c("lundbeck", "Lundbeck", "lundbeck.com", "DK", "Copenhagen", "5001-10000", "Pharmaceuticals",
      False, "H. Lundbeck", "H. Lundbeck A/S", "HLUN-B.CO"),
    c("coloplast", "Coloplast", "coloplast.com", "DK", "Humlebaek", "10001+", "Medical Devices",
      False, "", "Coloplast A/S", "COLO-B.CO"),
    c("demant", "Demant", "demant.com", "DK", "Smorum", "10001+", "Medical Devices",
      False, "Oticon|William Demant", "Demant A/S", "DEMANT.CO"),
    c("ferring", "Ferring Pharmaceuticals", "ferring.com", "CH", "Saint-Prex", "5001-10000",
      "Pharmaceuticals", False, "", "Ferring Pharmaceuticals"),
    c("straumann", "Straumann", "straumann.com", "CH", "Basel", "5001-10000", "Medical Devices",
      False, "Institut Straumann", "Straumann Holding AG", "STMN.SW"),
    c("sonova", "Sonova", "sonova.com", "CH", "Stafa", "10001+", "Medical Devices",
      False, "Phonak", "Sonova Holding AG", "SOON.SW"),
    c("alcon", "Alcon", "alcon.com", "CH", "Geneva", "10001+", "Medical Devices",
      False, "", "Alcon Inc.", "ALC"),
    c("fresenius", "Fresenius", "fresenius.com", "DE", "Bad Homburg", "10001+", "Healthcare",
      False, "", "Fresenius SE & Co. KGaA", "FRE.DE"),
    c("fresenius-medical-care", "Fresenius Medical Care", "freseniusmedicalcare.com", "DE", "Bad Homburg",
      "10001+", "Healthcare", False, "FMC", "Fresenius Medical Care AG", "FME.DE"),
    c("sartorius", "Sartorius", "sartorius.com", "DE", "Gottingen", "10001+", "Life Sciences",
      False, "", "Sartorius AG", "SRT3.DE"),
    c("b-braun", "B. Braun", "bbraun.com", "DE", "Melsungen", "10001+", "Medical Devices",
      False, "B Braun", "B. Braun SE"),
    c("draeger", "Drager", "draeger.com", "DE", "Lubeck", "10001+", "Medical Devices",
      False, "Dräger", "Dragerwerk AG & Co. KGaA", "DRW3.DE"),
    c("ucb", "UCB", "ucb.com", "BE", "Brussels", "5001-10000", "Pharmaceuticals",
      False, "", "UCB SA", "UCB.BR"),
    c("argenx", "argenx", "argenx.com", "BE", "Ghent", "1001-5000", "Biotechnology",
      False, "", "argenx SE", "ARGX.BR"),
    c("galapagos", "Galapagos", "galapagos.com", "BE", "Mechelen", "501-1000", "Biotechnology",
      False, "", "Galapagos NV", "GLPG.AS"),
    c("bavarian-nordic", "Bavarian Nordic", "bavarian-nordic.com", "DK", "Hellerup", "1001-5000",
      "Biotechnology", False, "", "Bavarian Nordic A/S", "BAVA.CO"),
    c("recordati", "Recordati", "recordati.com", "IT", "Milan", "1001-5000", "Pharmaceuticals",
      False, "", "Recordati S.p.A.", "REC.MI"),
    c("amplifon", "Amplifon", "amplifon.com", "IT", "Milan", "10001+", "Medical Devices",
      False, "", "Amplifon S.p.A.", "AMP.MI"),
    c("diasorin", "DiaSorin", "diasorin.com", "IT", "Saluggia", "1001-5000", "Diagnostics",
      False, "", "DiaSorin S.p.A.", "DIA.MI"),
    c("menarini", "Menarini", "menarini.com", "IT", "Florence", "10001+", "Pharmaceuticals",
      False, "Menarini Group", "Menarini Group"),
    c("chiesi", "Chiesi", "chiesi.com", "IT", "Parma", "5001-10000", "Pharmaceuticals",
      False, "Chiesi Farmaceutici", "Chiesi Farmaceutici S.p.A."),
    c("grifols", "Grifols", "grifols.com", "ES", "Barcelona", "10001+", "Pharmaceuticals",
      False, "", "Grifols, S.A.", "GRF.MC"),
    c("almirall", "Almirall", "almirall.com", "ES", "Barcelona", "1001-5000", "Pharmaceuticals",
      False, "", "Almirall, S.A.", "ALM.MC"),
    c("richter-gedeon", "Gedeon Richter", "richter.hu", "HU", "Budapest", "5001-10000", "Pharmaceuticals",
      False, "Richter Gedeon", "Richter Gedeon Vegyeszeti Gyar Nyrt", "RICHT.BD"),
    c("egis", "Egis Pharmaceuticals", "egis.hu", "HU", "Budapest", "1001-5000", "Pharmaceuticals",
      False, "Egis", "Egis Gyogyszergyar Zrt."),
    c("zentiva", "Zentiva", "zentiva.com", "CZ", "Prague", "1001-5000", "Pharmaceuticals",
      False, "Zentiva Group", "Zentiva, k.s."),
    c("krka", "Krka", "krka.si", "SI", "Novo Mesto", "5001-10000", "Pharmaceuticals",
      False, "Krka Group", "Krka, tovarna zdravil, d.d.", "KRKG.LJ"),
    c("sopharma", "Sopharma", "sopharma.bg", "BG", "Sofia", "1001-5000", "Pharmaceuticals",
      False, "", "Sopharma AD", "SFA.BG"),
    c("antibiotice-iasi", "Antibiotice Iasi", "antibiotice.ro", "RO", "Iasi", "501-1000", "Pharmaceuticals",
      False, "Antibiotice", "ANTIBIOTICE SA", "ATB.RO"),
    c("polpharma", "Polpharma", "polpharma.pl", "PL", "Starogard Gdanski", "1001-5000", "Pharmaceuticals",
      False, "Polpharma Group", "Polpharma S.A."),
    c("adamed", "Adamed", "adamed.com", "PL", "Pienkow", "1001-5000", "Pharmaceuticals",
      False, "Adamed Pharma", "Adamed Pharma S.A."),
    c("hikma", "Hikma Pharmaceuticals", "hikma.com", "GB", "London", "5001-10000", "Pharmaceuticals",
      False, "Hikma", "Hikma Pharmaceuticals PLC", "HIK.L"),
    c("teva", "Teva Pharmaceutical Industries", "tevapharm.com", "IL", "Tel Aviv", "10001+", "Pharmaceuticals",
      False, "Teva", "Teva Pharmaceutical Industries Limited", "TEVA"),
    c("medtronic", "Medtronic", "medtronic.com", "US", "Dublin", "10001+", "Medical Devices",
      False, "", "Medtronic plc", "MDT"),
    c("abbott", "Abbott", "abbott.com", "US", "Abbott Park", "10001+", "Healthcare",
      False, "Abbott Laboratories", "Abbott Laboratories", "ABT"),
    c("johnson-and-johnson", "Johnson & Johnson", "jnj.com", "US", "New Brunswick", "10001+", "Healthcare",
      False, "J&J", "Johnson & Johnson", "JNJ"),
    c("pfizer", "Pfizer", "pfizer.com", "US", "New York", "10001+", "Pharmaceuticals",
      False, "", "Pfizer Inc.", "PFE"),
    c("merck-and-co", "Merck & Co.", "merck.com", "US", "Rahway", "10001+", "Pharmaceuticals",
      False, "MSD", "Merck & Co., Inc.", "MRK"),
    c("eli-lilly", "Eli Lilly", "lilly.com", "US", "Indianapolis", "10001+", "Pharmaceuticals",
      False, "Lilly", "Eli Lilly and Company", "LLY"),
    c("amgen", "Amgen", "amgen.com", "US", "Thousand Oaks", "10001+", "Biotechnology",
      False, "", "Amgen Inc.", "AMGN"),
    c("gilead", "Gilead Sciences", "gilead.com", "US", "Foster City", "10001+", "Biotechnology",
      False, "Gilead", "Gilead Sciences, Inc.", "GILD"),
    c("boston-scientific", "Boston Scientific", "bostonscientific.com", "US", "Marlborough", "10001+",
      "Medical Devices", False, "", "Boston Scientific Corporation", "BSX"),
    c("stryker", "Stryker", "stryker.com", "US", "Kalamazoo", "10001+", "Medical Devices",
      False, "", "Stryker Corporation", "SYK"),
    c("ge-healthcare", "GE HealthCare", "gehealthcare.com", "US", "Chicago", "10001+", "Medical Devices",
      False, "GE Healthcare", "GE HealthCare Technologies Inc.", "GEHC"),
    c("siemens-healthineers", "Siemens Healthineers", "siemens-healthineers.com", "DE", "Erlangen", "10001+",
      "Medical Devices", False, "", "Siemens Healthineers AG", "SHL.DE"),
    c("elekta", "Elekta", "elekta.com", "SE", "Stockholm", "1001-5000", "Medical Devices",
      False, "", "Elekta AB", "EKTA-B.ST"),
    c("getinge", "Getinge", "getinge.com", "SE", "Gothenburg", "10001+", "Medical Devices",
      False, "", "Getinge AB", "GETI-B.ST"),
    c("sobi", "Swedish Orphan Biovitrum", "sobi.com", "SE", "Stockholm", "1001-5000", "Biotechnology",
      False, "Sobi", "Swedish Orphan Biovitrum AB", "SOBI.ST"),

    c("eon", "E.ON", "eon.com", "DE", "Essen", "10001+", "Utilities",
      False, "EON", "E.ON SE", "EOAN.DE"),
    c("rwe", "RWE", "rwe.com", "DE", "Essen", "10001+", "Utilities",
      False, "", "RWE AG", "RWE.DE"),
    c("enbw", "EnBW", "enbw.com", "DE", "Karlsruhe", "10001+", "Utilities",
      False, "Energie Baden-Wurttemberg", "EnBW Energie Baden-Wurttemberg AG", "EBK.DE"),
    c("uniper", "Uniper", "uniper.energy", "DE", "Dusseldorf", "5001-10000", "Energy Trading",
      False, "", "Uniper SE", "UN01.DE"),
    c("iberdrola", "Iberdrola", "iberdrola.com", "ES", "Bilbao", "10001+", "Utilities",
      False, "", "Iberdrola, S.A.", "IBE.MC"),
    c("endesa", "Endesa", "endesa.com", "ES", "Madrid", "10001+", "Utilities",
      False, "", "Endesa, S.A.", "ELE.MC"),
    c("naturgy", "Naturgy", "naturgy.com", "ES", "Barcelona", "10001+", "Utilities",
      False, "Gas Natural Fenosa", "Naturgy Energy Group, S.A.", "NTGY.MC"),
    c("repsol", "Repsol", "repsol.com", "ES", "Madrid", "10001+", "Oil & Gas",
      False, "", "Repsol, S.A.", "REP.MC"),
    c("galp", "Galp", "galp.com", "PT", "Lisbon", "5001-10000", "Oil & Gas",
      False, "Galp Energia", "Galp Energia, SGPS, S.A.", "GALP.LS"),
    c("edp", "EDP", "edp.com", "PT", "Lisbon", "10001+", "Utilities",
      False, "Energias de Portugal", "EDP - Energias de Portugal, S.A.", "EDP.LS"),
    c("enel", "Enel", "enel.com", "IT", "Rome", "10001+", "Utilities",
      False, "", "Enel S.p.A.", "ENEL.MI"),
    c("eni", "Eni", "eni.com", "IT", "Rome", "10001+", "Oil & Gas",
      False, "", "Eni S.p.A.", "ENI.MI"),
    c("snam", "Snam", "snam.it", "IT", "San Donato Milanese", "1001-5000", "Gas Infrastructure",
      False, "", "Snam S.p.A.", "SRG.MI"),
    c("terna", "Terna", "terna.it", "IT", "Rome", "1001-5000", "Electricity Grid",
      False, "", "Terna S.p.A.", "TRN.MI"),
    c("a2a", "A2A", "a2a.eu", "IT", "Brescia", "10001+", "Utilities",
      False, "", "A2A S.p.A.", "A2A.MI"),
    c("hera", "Hera", "gruppohera.it", "IT", "Bologna", "5001-10000", "Utilities",
      False, "Hera Group", "Hera S.p.A.", "HER.MI"),
    c("orsted", "Orsted", "orsted.com", "DK", "Fredericia", "5001-10000", "Renewable Energy",
      False, "Ørsted", "Orsted A/S", "ORSTED.CO"),
    c("equinor", "Equinor", "equinor.com", "NO", "Stavanger", "10001+", "Oil & Gas",
      False, "Statoil", "Equinor ASA", "EQNR.OL"),
    c("statkraft", "Statkraft", "statkraft.com", "NO", "Oslo", "1001-5000", "Renewable Energy",
      False, "", "Statkraft AS"),
    c("shell", "Shell", "shell.com", "GB", "London", "10001+", "Oil & Gas",
      False, "Royal Dutch Shell", "Shell plc", "SHEL.L"),
    c("bp", "BP", "bp.com", "GB", "London", "10001+", "Oil & Gas",
      False, "British Petroleum", "BP p.l.c.", "BP.L"),
    c("totalenergies", "TotalEnergies", "totalenergies.com", "FR", "Courbevoie", "10001+", "Oil & Gas",
      False, "Total", "TotalEnergies SE", "TTE.PA"),
    c("glencore", "Glencore", "glencore.com", "CH", "Baar", "10001+", "Mining",
      False, "", "Glencore plc", "GLEN.L"),
    c("rio-tinto", "Rio Tinto", "riotinto.com", "GB", "London", "10001+", "Mining",
      False, "", "Rio Tinto plc", "RIO.L"),
    c("anglo-american", "Anglo American", "angloamerican.com", "GB", "London", "10001+", "Mining",
      False, "", "Anglo American plc", "AAL.L"),
    c("arcelormittal", "ArcelorMittal", "arcelormittal.com", "LU", "Luxembourg", "10001+", "Steel",
      False, "", "ArcelorMittal S.A.", "MT"),
    c("bhp", "BHP", "bhp.com", "AU", "Melbourne", "10001+", "Mining",
      False, "BHP Billiton", "BHP Group Limited", "BHP"),
    c("norsk-hydro", "Norsk Hydro", "hydro.com", "NO", "Oslo", "10001+", "Aluminium",
      False, "Hydro", "Norsk Hydro ASA", "NHY.OL"),
    c("aurubis", "Aurubis", "aurubis.com", "DE", "Hamburg", "5001-10000", "Metals",
      False, "", "Aurubis AG", "NDA.DE"),
    c("k-plus-s", "K+S", "k-plus-s.com", "DE", "Kassel", "10001+", "Chemicals",
      False, "K+S AG", "K+S Aktiengesellschaft", "SDF.DE"),
    c("fortuna-entertainment", "Fortuna Entertainment Group", "fortunagroup.eu", "CZ", "Prague", "1001-5000",
      "Gaming", False, "Fortuna", "Fortuna Entertainment Group N.V."),

    c("accenture", "Accenture", "accenture.com", "IE", "Dublin", "10001+", "IT Services",
      True, "Accenture plc", "Accenture plc", "ACN"),
    c("capgemini", "Capgemini", "capgemini.com", "FR", "Paris", "10001+", "IT Services",
      True, "", "Capgemini SE", "CAP.PA"),
    c("cognizant", "Cognizant", "cognizant.com", "US", "Teaneck", "10001+", "IT Services",
      True, "Cognizant Technology Solutions", "Cognizant Technology Solutions Corporation", "CTSH"),
    c("infosys", "Infosys", "infosys.com", "IN", "Bengaluru", "10001+", "IT Services",
      True, "", "Infosys Limited", "INFY"),
    c("tcs", "Tata Consultancy Services", "tcs.com", "IN", "Mumbai", "10001+", "IT Services",
      True, "TCS", "Tata Consultancy Services Limited", "TCS.NS"),
    c("wipro", "Wipro", "wipro.com", "IN", "Bengaluru", "10001+", "IT Services",
      True, "", "Wipro Limited", "WIT"),
    c("hcltech", "HCLTech", "hcltech.com", "IN", "Noida", "10001+", "IT Services",
      True, "HCL Technologies", "HCL Technologies Limited", "HCLTECH.NS"),
    c("genpact", "Genpact", "genpact.com", "US", "New York", "10001+", "Business Process Outsourcing",
      True, "", "Genpact Limited", "G"),
    c("wns", "WNS", "wns.com", "IN", "Mumbai", "10001+", "Business Process Outsourcing",
      True, "WNS Global Services", "WNS (Holdings) Limited", "WNS"),
    c("teleperformance", "Teleperformance", "teleperformance.com", "FR", "Paris", "10001+",
      "Business Process Outsourcing", True, "TP", "Teleperformance SE", "TEP.PA"),
    c("concentrix", "Concentrix", "concentrix.com", "US", "Newark", "10001+", "Business Process Outsourcing",
      True, "Webhelp", "Concentrix Corporation", "CNXC"),
    c("foundever", "Foundever", "foundever.com", "US", "Miami", "10001+", "Business Process Outsourcing",
      True, "Sitel", "Foundever Group"),
    c("ttec", "TTEC", "ttec.com", "US", "Englewood", "10001+", "Business Process Outsourcing",
      True, "TeleTech", "TTEC Holdings, Inc.", "TTEC"),
    c("transcom", "Transcom", "transcom.com", "SE", "Stockholm", "10001+", "Business Process Outsourcing",
      True, "", "Transcom Worldwide AB"),
    c("atento", "Atento", "atento.com", "ES", "Madrid", "10001+", "Business Process Outsourcing",
      True, "", "Atento S.A."),
    c("majorel", "Majorel", "majorel.com", "LU", "Luxembourg", "10001+", "Business Process Outsourcing",
      True, "", "Majorel Group Luxembourg S.A."),
    c("arvato", "Arvato", "arvato.com", "DE", "Gutersloh", "10001+", "Business Process Outsourcing",
      True, "Bertelsmann Arvato", "Arvato GmbH"),
    c("bertelsmann", "Bertelsmann", "bertelsmann.com", "DE", "Gutersloh", "10001+", "Media",
      False, "", "Bertelsmann SE & Co. KGaA"),
    c("atos", "Atos", "atos.com", "FR", "Bezons", "10001+", "IT Services",
      True, "Atos SE", "Atos SE", "ATO.PA"),
    c("sopra-steria", "Sopra Steria", "soprasteria.com", "FR", "Paris", "10001+", "IT Services",
      True, "", "Sopra Steria Group SA", "SOP.PA"),
    c("cgi", "CGI", "cgi.com", "CA", "Montreal", "10001+", "IT Services",
      True, "CGI Group", "CGI Inc.", "GIB"),
    c("endava", "Endava", "endava.com", "GB", "London", "10001+", "IT Services",
      True, "", "Endava plc", "DAVA"),
    c("epam", "EPAM Systems", "epam.com", "US", "Newtown", "10001+", "IT Services",
      True, "EPAM", "EPAM Systems, Inc.", "EPAM"),
    c("globant", "Globant", "globant.com", "AR", "Buenos Aires", "10001+", "IT Services",
      True, "", "Globant S.A.", "GLOB"),
    c("nagarro", "Nagarro", "nagarro.com", "DE", "Munich", "10001+", "IT Services",
      True, "", "Nagarro SE", "NA9.DE"),
    c("gft-technologies", "GFT Technologies", "gft.com", "DE", "Stuttgart", "5001-10000", "IT Services",
      True, "GFT", "GFT Technologies SE", "GFT.DE"),
    c("adesso", "adesso", "adesso.de", "DE", "Dortmund", "5001-10000", "IT Services",
      True, "adesso SE", "adesso SE", "ADN1.DE"),
    c("devoteam", "Devoteam", "devoteam.com", "FR", "Levallois-Perret", "5001-10000", "IT Consulting",
      True, "", "Devoteam S.A."),
    c("reply", "Reply", "reply.com", "IT", "Turin", "10001+", "IT Services",
      True, "", "Reply S.p.A.", "REY.MI"),
    c("engineering", "Engineering Ingegneria Informatica", "eng.it", "IT", "Rome", "10001+", "IT Services",
      True, "Engineering Group", "Engineering Ingegneria Informatica S.p.A."),
    c("almaviva", "Almaviva", "almaviva.it", "IT", "Rome", "10001+", "IT Services",
      True, "Almaviva Group", "Almaviva S.p.A."),
    c("sia", "SIA", "sia.eu", "IT", "Milan", "1001-5000", "Financial Technology",
      True, "", "SIA S.p.A."),
    c("dxc-technology", "DXC Technology", "dxc.com", "US", "Ashburn", "10001+", "IT Services",
      True, "", "DXC Technology Company", "DXC"),
    c("kyndryl", "Kyndryl", "kyndryl.com", "US", "New York", "10001+", "IT Services",
      True, "", "Kyndryl Holdings, Inc.", "KD"),
    c("ibm", "IBM", "ibm.com", "US", "Armonk", "10001+", "IT Services",
      False, "International Business Machines", "International Business Machines Corporation", "IBM"),
    c("tietoevry", "Tietoevry", "tietoevry.com", "FI", "Espoo", "10001+", "IT Services",
      True, "Tieto", "Tietoevry Corporation", "TIETO.HE"),
    c("netcompany", "Netcompany", "netcompany.com", "DK", "Copenhagen", "5001-10000", "IT Services",
      True, "", "Netcompany Group A/S", "NETC.CO"),
    c("nnit", "NNIT", "nnit.com", "DK", "Soborg", "1001-5000", "IT Services",
      True, "", "NNIT A/S", "NNIT.CO"),
    c("computacenter", "Computacenter", "computacenter.com", "GB", "Hatfield", "10001+", "IT Services",
      True, "", "Computacenter plc", "CCC.L"),
    c("softcat", "Softcat", "softcat.com", "GB", "Marlow", "1001-5000", "IT Services",
      True, "", "Softcat plc", "SCT.L"),
    c("bechtle", "Bechtle", "bechtle.com", "DE", "Neckarsulm", "10001+", "IT Services",
      True, "", "Bechtle AG", "BC8.DE"),
    c("cancom", "Cancom", "cancom.com", "DE", "Munich", "1001-5000", "IT Services",
      True, "CANCOM SE", "CANCOM SE", "COK.DE"),
    c("asseco", "Asseco", "asseco.com", "PL", "Rzeszow", "10001+", "Software",
      True, "Asseco Poland", "Asseco Poland S.A.", "ACP.WA"),
    c("comarch", "Comarch", "comarch.com", "PL", "Krakow", "5001-10000", "Software",
      True, "", "Comarch SA", "CMR.WA"),
    c("sii-poland", "Sii Poland", "sii.pl", "PL", "Warsaw", "5001-10000", "IT Services",
      True, "Sii", "Sii Polska Sp. z o.o."),
    c("netguru", "Netguru", "netguru.com", "PL", "Poznan", "501-1000", "IT Services",
      True, "", "Netguru S.A."),
    c("future-processing", "Future Processing", "future-processing.com", "PL", "Gliwice", "501-1000",
      "IT Services", True, "", "Future Processing Sp. z o.o."),
    c("softserve", "SoftServe", "softserveinc.com", "UA", "Lviv", "10001+", "IT Services",
      True, "", "SoftServe, Inc."),
    c("ciklum", "Ciklum", "ciklum.com", "UA", "Kyiv", "1001-5000", "IT Services",
      True, "", "Ciklum Holding Ltd"),
    c("n-ix", "N-iX", "n-ix.com", "UA", "Lviv", "1001-5000", "IT Services",
      True, "", "N-iX Ltd"),
    c("sigma-software", "Sigma Software", "sigma.software", "UA", "Kharkiv", "1001-5000", "IT Services",
      True, "", "Sigma Software LLC"),
    c("intellias", "Intellias", "intellias.com", "UA", "Lviv", "1001-5000", "IT Services",
      True, "", "Intellias Ltd"),
    c("tremend", "Tremend", "tremend.com", "RO", "Bucharest", "501-1000", "IT Services",
      True, "", "Tremend Software Consulting S.R.L."),
    c("arobs", "AROBS", "arobs.com", "RO", "Cluj-Napoca", "1001-5000", "IT Services",
      True, "AROBS Transilvania Software", "AROBS Transilvania Software S.A.", "AROBS.RO"),
    c("fortech", "Fortech", "fortech.com", "RO", "Cluj-Napoca", "501-1000", "IT Services",
      True, "", "Fortech S.R.L."),
    c("bitdefender", "Bitdefender", "bitdefender.com", "RO", "Bucharest", "1001-5000", "Cybersecurity",
      False, "", "Bitdefender S.R.L."),
    c("amdaris", "Amdaris", "amdaris.com", "GB", "Bristol", "501-1000", "IT Services",
      True, "", "Amdaris Group Limited"),
    c("stefanini", "Stefanini", "stefanini.com", "BR", "Sao Paulo", "10001+", "IT Services",
      True, "Stefanini Group", "Stefanini IT Solutions S.A."),
    c("deloitte", "Deloitte", "deloitte.com", "GB", "London", "10001+", "Professional Services",
      True, "Deloitte Touche Tohmatsu", "Deloitte Touche Tohmatsu Limited"),
    c("pwc", "PwC", "pwc.com", "GB", "London", "10001+", "Professional Services",
      True, "PricewaterhouseCoopers", "PricewaterhouseCoopers International Limited"),
    c("kpmg", "KPMG", "kpmg.com", "NL", "Amstelveen", "10001+", "Professional Services",
      True, "", "KPMG International Limited"),
    c("ey", "EY", "ey.com", "GB", "London", "10001+", "Professional Services",
      True, "Ernst & Young", "Ernst & Young Global Limited"),
    c("mckinsey", "McKinsey & Company", "mckinsey.com", "US", "New York", "10001+", "Management Consulting",
      True, "McKinsey", "McKinsey & Company, Inc."),
    c("bain", "Bain & Company", "bain.com", "US", "Boston", "10001+", "Management Consulting",
      True, "", "Bain & Company, Inc."),
    c("bcg", "Boston Consulting Group", "bcg.com", "US", "Boston", "10001+", "Management Consulting",
      True, "BCG", "The Boston Consulting Group, Inc."),
    c("infosys-bpm", "Infosys BPM", "infosysbpm.com", "IN", "Bengaluru", "10001+",
      "Business Process Outsourcing", True, "", "Infosys BPM Limited"),

    c("sap", "SAP", "sap.com", "DE", "Walldorf", "10001+", "Enterprise Software",
      False, "SAP SE", "SAP SE", "SAP.DE"),
    c("software-ag", "Software AG", "softwareag.com", "DE", "Darmstadt", "1001-5000", "Enterprise Software",
      False, "Software AG", "Software Aktiengesellschaft"),
    c("teamviewer", "TeamViewer", "teamviewer.com", "DE", "Goppingen", "1001-5000", "Software",
      False, "", "TeamViewer SE", "TMV.DE"),
    c("nemetschek", "Nemetschek", "nemetschek.com", "DE", "Munich", "1001-5000", "Software",
      False, "Nemetschek Group", "Nemetschek SE", "NEM.DE"),
    c("compugroup", "CompuGroup Medical", "cgm.com", "DE", "Koblenz", "5001-10000", "Healthcare Software",
      False, "CGM", "CompuGroup Medical SE & Co. KGaA", "COP.DE"),
    c("sage", "Sage", "sage.com", "GB", "Newcastle upon Tyne", "10001+", "Enterprise Software",
      False, "Sage Group", "The Sage Group plc", "SGE.L"),
    c("nexi", "Nexi", "nexi.it", "IT", "Milan", "5001-10000", "Payments",
      False, "", "Nexi S.p.A.", "NEXI.MI"),
    c("worldline", "Worldline", "worldline.com", "FR", "Bezons", "10001+", "Payments",
      False, "", "Worldline SA", "WLN.PA"),
    c("ingenico", "Ingenico", "ingenico.com", "FR", "Suresnes", "1001-5000", "Payments",
      False, "", "Ingenico Group"),
    c("edenred", "Edenred", "edenred.com", "FR", "Issy-les-Moulineaux", "10001+", "Payments",
      False, "", "Edenred SE", "EDEN.PA"),
    c("indra", "Indra", "indra.es", "ES", "Alcobendas", "10001+", "IT Services",
      True, "Indra Sistemas", "Indra Sistemas, S.A.", "IDR.MC"),
    c("ntt-data", "NTT DATA", "nttdata.com", "JP", "Tokyo", "10001+", "IT Services",
      True, "", "NTT DATA Corporation"),
    c("fujitsu", "Fujitsu", "fujitsu.com", "JP", "Tokyo", "10001+", "IT Services",
      True, "", "Fujitsu Limited", "6702.T"),
    c("nec", "NEC", "nec.com", "JP", "Tokyo", "10001+", "IT Services",
      True, "NEC Corporation", "NEC Corporation", "6701.T"),
    c("hitachi", "Hitachi", "hitachi.com", "JP", "Tokyo", "10001+", "Industrial Technology",
      False, "Hitachi Ltd", "Hitachi, Ltd.", "6501.T"),
    c("serco", "Serco", "serco.com", "GB", "Hook", "10001+", "Public Services",
      True, "", "Serco Group plc", "SRP.L"),
    c("capita", "Capita", "capita.com", "GB", "London", "10001+", "Business Process Outsourcing",
      True, "", "Capita plc", "CPI.L"),
    c("leidos", "Leidos", "leidos.com", "US", "Reston", "10001+", "IT Services",
      True, "", "Leidos Holdings, Inc.", "LDOS"),
    c("booz-allen", "Booz Allen Hamilton", "boozallen.com", "US", "McLean", "10001+", "Consulting",
      True, "", "Booz Allen Hamilton Holding Corporation", "BAH"),
    c("maximus", "Maximus", "maximus.com", "US", "Tysons", "10001+", "Public Services",
      True, "", "Maximus, Inc.", "MMS"),
    c("thoughtworks", "Thoughtworks", "thoughtworks.com", "US", "Chicago", "10001+", "IT Services",
      True, "", "Thoughtworks Holding, Inc.", "TWKS"),
    c("ciandt", "CI&T", "ciandt.com", "BR", "Campinas", "5001-10000", "IT Services",
      True, "CI and T", "CI&T Inc.", "CINT"),
    c("visma", "Visma", "visma.com", "NO", "Oslo", "10001+", "Enterprise Software",
      False, "", "Visma AS"),
    c("simcorp", "SimCorp", "simcorp.com", "DK", "Copenhagen", "1001-5000", "Financial Software",
      False, "", "SimCorp A/S"),
    c("systematic", "Systematic", "systematic.com", "DK", "Aarhus", "1001-5000", "Software",
      False, "Systematic A/S", "Systematic A/S"),
    c("trifork", "Trifork", "trifork.com", "DK", "Aarhus", "1001-5000", "IT Services",
      True, "Trifork Group", "Trifork Group AG", "TRIFOR.CO"),
    c("knowit", "Knowit", "knowit.se", "SE", "Stockholm", "1001-5000", "IT Consulting",
      True, "", "Knowit AB", "KNOW.ST"),
    c("atea", "Atea", "atea.com", "NO", "Oslo", "5001-10000", "IT Infrastructure",
      True, "", "Atea ASA", "ATEA.OL"),
    c("bouvet", "Bouvet", "bouvet.no", "NO", "Oslo", "1001-5000", "IT Consulting",
      True, "", "Bouvet ASA", "BOUVET.OL"),
    c("gofore", "Gofore", "gofore.com", "FI", "Tampere", "501-1000", "IT Consulting",
      True, "", "Gofore Oyj", "GOFORE.HE"),
    c("siili-solutions", "Siili Solutions", "siili.com", "FI", "Helsinki", "201-500", "IT Consulting",
      True, "Siili", "Siili Solutions Oyj", "SIILI.HE"),
    c("dustin", "Dustin", "dustin.se", "SE", "Nacka", "1001-5000", "IT Reseller",
      True, "Dustin Group", "Dustin Group AB", "DUST.ST"),
    c("econocom", "Econocom", "econocom.com", "BE", "Brussels", "5001-10000", "IT Services",
      True, "", "Econocom Group SE", "ECONB.BR"),
    c("cegeka", "Cegeka", "cegeka.com", "BE", "Hasselt", "5001-10000", "IT Services",
      True, "", "Cegeka NV"),
    c("uipath", "UiPath", "uipath.com", "US", "New York", "1001-5000", "Automation Software",
      False, "", "UiPath Inc.", "PATH"),
    c("celonis", "Celonis", "celonis.com", "DE", "Munich", "1001-5000", "Process Mining",
      False, "", "Celonis SE"),
    c("pegasystems", "Pegasystems", "pega.com", "US", "Waltham", "5001-10000", "Software",
      False, "Pega", "Pegasystems Inc.", "PEGA"),
    c("appian", "Appian", "appian.com", "US", "McLean", "1001-5000", "Software",
      False, "", "Appian Corporation", "APPN"),
    c("nintex", "Nintex", "nintex.com", "US", "Bellevue", "501-1000", "Software",
      False, "", "Nintex Global Limited"),
    c("automation-anywhere", "Automation Anywhere", "automationanywhere.com", "US", "San Jose", "1001-5000",
      "Automation Software", False, "", "Automation Anywhere, Inc."),
    c("ss-and-c", "SS&C Technologies", "ssctech.com", "US", "Windsor", "10001+", "Financial Software",
      False, "SS&C", "SS&C Technologies Holdings, Inc.", "SSNC"),
    c("temenos", "Temenos", "temenos.com", "CH", "Geneva", "5001-10000", "Financial Software",
      False, "", "Temenos AG", "TEMN.SW"),
    c("finastra", "Finastra", "finastra.com", "GB", "London", "10001+", "Financial Software",
      False, "", "Finastra Group Holdings Limited"),
    c("misys", "Mambu", "mambu.com", "NL", "Amsterdam", "501-1000", "Financial Software",
      False, "", "Mambu GmbH"),
    c("backbase", "Backbase", "backbase.com", "NL", "Amsterdam", "1001-5000", "Financial Software",
      False, "", "Backbase B.V."),
    c("guidewire", "Guidewire Software", "guidewire.com", "US", "San Mateo", "1001-5000", "Insurance Software",
      False, "Guidewire", "Guidewire Software, Inc.", "GWRE"),
    c("duck-creek", "Duck Creek Technologies", "duckcreek.com", "US", "Boston", "1001-5000", "Insurance Software",
      False, "Duck Creek", "Duck Creek Technologies, Inc."),
    c("sapiens", "Sapiens", "sapiens.com", "IL", "Holon", "1001-5000", "Insurance Software",
      False, "Sapiens International", "Sapiens International Corporation N.V.", "SPNS"),
    c("crif", "CRIF", "crif.com", "IT", "Bologna", "1001-5000", "Credit Information",
      False, "", "CRIF S.p.A."),
    c("experian", "Experian", "experian.com", "IE", "Dublin", "10001+", "Credit Information",
      False, "", "Experian plc", "EXPN.L"),
    c("equifax", "Equifax", "equifax.com", "US", "Atlanta", "10001+", "Credit Information",
      False, "", "Equifax Inc.", "EFX"),
    c("transunion", "TransUnion", "transunion.com", "US", "Chicago", "10001+", "Credit Information",
      False, "", "TransUnion", "TRU"),

    c("uztel", "UZTEL", "uztel.ro", "RO", "Ploiesti", "201-500", "Oilfield Equipment",
      False, "UZTEL SA", "UZTEL SA"),
    c("vrancart", "Vrancart", "vrancart.ro", "RO", "Adjud", "501-1000", "Paper Packaging",
      False, "VRANCART SA", "VRANCART SA", "VNC.RO"),
    c("comelf", "Comelf", "comelf.ro", "RO", "Bistrita", "501-1000", "Industrial Machinery",
      False, "COMELF SA", "COMELF SA", "CMF.RO"),
    c("turbomecanica", "Turbomecanica", "turbomecanica.ro", "RO", "Bucharest", "201-500", "Aerospace Components",
      False, "TURBOMECANICA SA", "TURBOMECANICA SA", "TBM.RO"),
    c("iar-ghimbav", "IAR Ghimbav", "iar.ro", "RO", "Ghimbav", "201-500", "Aerospace",
      False, "IAR SA", "IAR SA", "IARV.RO"),
    c("rulmenti-barlad", "Rulmenti Barlad", "rulmenti.ro", "RO", "Barlad", "501-1000", "Bearings",
      False, "RULMENTI SA", "RULMENTI SA", "RBL.RO"),
    c("arctic-stream", "Arctic Stream", "arcticstream.ro", "RO", "Brasov", "201-500", "HVAC Equipment",
      False, "ARCTIC STREAM S.A.", "ARCTIC STREAM S.A.", "AST.RO"),
    c("neochim", "Neochim", "neochim.bg", "BG", "Dimitrovgrad", "201-500", "Chemicals",
      False, "Neochim AD", "НЕОХИМ"),
    c("wielton", "Wielton", "wielton.com.pl", "PL", "Wielun", "1001-5000", "Transport Equipment",
      False, "Wielton S.A.", "\"WIELTON\" SPÓŁKA AKCYJNA", "WLT.WA"),
    c("moldova-agroindbank", "Moldova Agroindbank", "maib.md", "MD", "Chisinau", "1001-5000", "Banking",
      False, "MAIB", "Moldova Agroindbank S.A."),
    c("victoriabank", "Victoriabank", "victoriabank.md", "MD", "Chisinau", "501-1000", "Banking",
      False, "", "Victoriabank S.A."),
    c("moldindconbank", "Moldindconbank", "micb.md", "MD", "Chisinau", "501-1000", "Banking",
      False, "MICB", "Moldindconbank S.A."),
    c("fincombank", "FinComBank", "fincombank.com", "MD", "Chisinau", "201-500", "Banking",
      False, "", "FinComBank S.A."),
    c("bucuria", "Bucuria", "bucuria.md", "MD", "Chisinau", "501-1000", "Food & Beverage",
      False, "Bucuria SA", "Bucuria S.A."),
    c("cricova", "Cricova", "cricova.md", "MD", "Cricova", "501-1000", "Wine",
      False, "Cricova Winery", "Cricova S.A."),
    c("franzeluta", "Franzeluta", "franzeluta.md", "MD", "Chisinau", "501-1000", "Food & Beverage",
      False, "Franzeluta SA", "Franzeluta S.A."),
    c("moldovagaz", "Moldovagaz", "moldovagaz.md", "MD", "Chisinau", "5001-10000", "Gas Distribution",
      False, "", "Moldovagaz S.A."),
    c("red-nord", "Red Nord", "rednord.md", "MD", "Balti", "501-1000", "Electricity Distribution",
      False, "", "Red Nord S.A."),
    c("moldovacredit", "Moldova Credit", "moldovacredit.md", "MD", "Chisinau", "201-500", "Leasing",
      False, "", "Moldova Credit S.A."),
]


def gn_search_url(query):
    return ("https://news.google.com/rss/search?q=" + urllib.parse.quote(query) +
            "&hl=en-US&gl=US&ceid=US:en")


def age_days(published):
    if not published:
        return None
    try:
        d = dt.datetime.strptime(published, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    except Exception:
        return None
    return (dt.datetime.now(dt.timezone.utc) - d).days


def collect_news_candidates(company, variants, token, recency):
    cands = {}
    for theme in THEME_ORDER:
        for tmpl in THEME_QUERIES[theme]:
            query = tmpl.format(company["name"])
            if recency:
                query = query + " " + recency
            try:
                raw, _, _ = http_get(gn_search_url(query), headers={"User-Agent": BROWSER_UA},
                                     host_limit=NEWS_WORKERS, timeout=25, retries=1)
            except Exception:
                bump("news_query_fail")
                continue
            bump("news_queries")
            xml = raw.decode("utf-8", "replace")
            for it in parse_rss_items(xml):
                title = re.sub(r"\s+", " ", strip_tags(it.get("title", ""))).strip()
                link = htmlmod.unescape(it.get("link", "")).strip()
                if not link.startswith("http"):
                    continue
                dedup = re.sub(r"[^a-z0-9]+", " ", title.lower()).strip()
                if not dedup:
                    continue
                pubdate = rfc822_to_iso(it.get("pubDate"))
                age = age_days(pubdate)
                if age is not None and age > MAX_AGE_DAYS:
                    bump("news_too_old")
                    continue
                prev = cands.get(dedup)
                if prev is not None:
                    if theme not in prev["themes"]:
                        prev["themes"].append(theme)
                    continue
                desc = clean_text(strip_tags(it.get("description", "")))
                src = re.sub(r"\s+", " ", strip_tags(it.get("source", ""))).strip()
                title_hit = mentions_company(title, variants)
                score = score_signal(title + "\n" + desc)
                if title_hit:
                    score += 4
                if token and token.lower() in title.lower():
                    score += 2
                cands[dedup] = {
                    "title": title[:400],
                    "link": link,
                    "desc": desc,
                    "source": src,
                    "published": pubdate,
                    "score": score,
                    "title_hit": title_hit,
                    "themes": [theme],
                }
    return cands


def rank_theme(items):
    return sorted(items, key=lambda x: (0 if x["title_hit"] else 1, -x["score"], x["title"]))


def select_by_theme(cands):
    picked = []
    used = set()
    for theme in THEME_ORDER:
        pool = [v for k, v in cands.items() if theme in v["themes"] and k not in used]
        for v in rank_theme(pool)[:THEME_TARGETS[theme]]:
            picked.append(v)
            used.add(re.sub(r"[^a-z0-9]+", " ", v["title"].lower()).strip())
    total_target = sum(THEME_TARGETS.values())
    if len(picked) < total_target:
        rest = [v for k, v in cands.items()
                if re.sub(r"[^a-z0-9]+", " ", v["title"].lower()).strip() not in used]
        for v in rank_theme(rest)[:total_target - len(picked)]:
            picked.append(v)
    return picked


def news_docs(company, observed):
    variants = name_variants(company)
    token = short_token(company["name"])
    cands = collect_news_candidates(company, variants, token, RECENCY_PRIMARY)
    picked = select_by_theme(cands)
    if len(picked) < sum(THEME_TARGETS.values()):
        extra = collect_news_candidates(company, variants, token, RECENCY_SECONDARY)
        for k, v in extra.items():
            if k not in cands:
                cands[k] = v
        picked = select_by_theme(cands)
    docs = []
    i = 0
    for item in picked:
        i += 1
        url = item["link"]
        publisher = item["source"] or "news.google.com"
        text = ""
        resolved = gn_resolve(url) if "news.google.com" in url else url
        if resolved:
            try:
                body = fetch_article_text(resolved)
            except Exception:
                body = ""
                bump("article_fail")
            if body and len(body) >= MIN_TEXT:
                low = body.lower()
                if any(v in low for v in variants):
                    text = body
                    url = resolved
                    publisher = urllib.parse.urlparse(resolved).netloc or publisher
                else:
                    bump("article_offtopic")
        if not text and len(item["desc"]) >= MIN_TEXT:
            text = clean_text(item["desc"])
        if not text or len(text) < MIN_TEXT or has_markup(text):
            continue
        docs.append(Doc("%s:nws-%d" % (company["key"], i), company["key"], url, publisher, "news",
                        item["published"], item["title"], text, observed))
    return docs


def board_belongs(company, url, text):
    return attribution.ats_board_belongs_to(company, url, text)


def job_tokens(company):
    name = company["name"]
    out = []
    a = slugify(name)
    if a:
        out.append(a)
    stripped = re.sub(
        r"\b(inc|ltd|limited|plc|nv|n\.v\.|gmbh|ag|sa|s\.a\.|srl|s\.r\.l\.|spa|s\.p\.a\.|"
        r"ab|asa|oyj|kk|as|group|group\.|holding|holdings|corp|corporation|company|"
        r"international|se|a/s|kgaa|inc\.)\b", " ", name, flags=re.I)
    b = slugify(stripped)
    if b and b not in out:
        out.append(b)
    compact = re.sub(r"[^a-z0-9]", "", name.lower())
    if compact and compact not in out and 3 < len(compact) < 30:
        out.append(compact)
    words = [w for w in re.split(r"[^A-Za-z0-9]+", stripped) if len(w) > 3]
    if words:
        w = words[0].lower()
        if w not in out:
            out.append(w)
    return out[:4]


def gh_jobs(tok):
    raw, _, _ = http_get("https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true" % tok,
                         headers={"User-Agent": PLAIN_UA}, host_limit=JOB_WORKERS, timeout=25)
    data = json.loads(raw.decode("utf-8", "replace"))
    jobs = data.get("jobs") or []
    out = []
    for j in jobs:
        body = htmlmod.unescape(j.get("content") or "")
        out.append({
            "id": str(j.get("id")),
            "title": strip_tags(j.get("title") or ""),
            "url": j.get("absolute_url") or "",
            "date": iso_from_any(j.get("updated_at")),
            "text": clean_text(strip_tags(body)),
            "provider": "gh",
        })
    return out


def lever_jobs(tok):
    raw, _, _ = http_get("https://api.lever.co/v0/postings/%s?mode=json" % tok,
                         headers={"User-Agent": PLAIN_UA}, host_limit=JOB_WORKERS, timeout=25)
    data = json.loads(raw.decode("utf-8", "replace"))
    out = []
    for j in data if isinstance(data, list) else []:
        body = j.get("descriptionPlain") or strip_tags(j.get("description") or "")
        out.append({
            "id": str(j.get("id")),
            "title": strip_tags(j.get("text") or ""),
            "url": j.get("hostedUrl") or "",
            "date": ms_to_iso(j.get("createdAt")),
            "text": clean_text(body),
            "provider": "lv",
        })
    return out


def ashby_jobs(tok):
    raw, _, _ = http_get(
        "https://api.ashbyhq.com/posting-api/job-board/%s?includeCompensation=true" % tok,
        headers={"User-Agent": PLAIN_UA}, host_limit=JOB_WORKERS, timeout=25)
    data = json.loads(raw.decode("utf-8", "replace"))
    jobs = data.get("jobs") or []
    out = []
    for j in jobs:
        body = j.get("descriptionPlain") or j.get("descriptionHtml") or j.get("description") or ""
        out.append({
            "id": str(j.get("id") or j.get("jobUrl") or ""),
            "title": strip_tags(j.get("title") or ""),
            "url": j.get("jobUrl") or j.get("applyUrl") or "",
            "date": iso_from_any(j.get("publishedAt") or j.get("updatedAt") or ""),
            "text": clean_text(strip_tags(body)),
            "provider": "ash",
        })
    return out


def sr_jobs(tok):
    raw, _, _ = http_get("https://api.smartrecruiters.com/v1/companies/%s/postings" % tok,
                         headers={"User-Agent": PLAIN_UA}, host_limit=JOB_WORKERS, timeout=25)
    data = json.loads(raw.decode("utf-8", "replace"))
    out = []
    for j in (data.get("content") or [])[:MAX_JOBS_PER_COMPANY]:
        jid = j.get("id")
        body = ""
        if jid:
            try:
                raw2, _, _ = http_get(
                    "https://api.smartrecruiters.com/v1/companies/%s/postings/%s" % (tok, jid),
                    headers={"User-Agent": PLAIN_UA}, host_limit=JOB_WORKERS, timeout=25)
                d2 = json.loads(raw2.decode("utf-8", "replace"))
                secs = ((d2.get("jobAd") or {}).get("sections") or {})
                parts = []
                for k in ("companyDescription", "jobDescription", "qualifications",
                          "additionalInformation"):
                    v = (secs.get(k) or {}).get("text")
                    if v:
                        parts.append(strip_tags(v))
                body = "\n\n".join(parts)
            except Exception:
                body = ""
        loc = ((j.get("location") or {}).get("city") or "")
        out.append({
            "id": str(jid),
            "title": strip_tags(j.get("name") or ""),
            "url": "https://jobs.smartrecruiters.com/%s/%s" % (tok, jid),
            "date": iso_from_any(j.get("releasedDate") or ""),
            "text": clean_text(strip_tags(body)),
            "provider": "sr",
            "city": loc,
        })
    return out


JOB_PROVIDERS = [("gh", gh_jobs), ("lv", lever_jobs), ("ash", ashby_jobs), ("sr", sr_jobs)]


def job_docs(company, observed):
    rng = random.Random(company["key"])
    tokens = job_tokens(company)
    postings = []
    provider_used = None
    for pname, fn in JOB_PROVIDERS:
        for tok in tokens:
            try:
                got = fn(tok)
            except Exception:
                bump("job_probe_fail")
                continue
            bump("job_probes")
            if not got:
                continue
            # A guessed slug that answers is not proof of ownership: "vinci",
            # "atlas", "maxima", "novo", "phoenix" and "technology" all resolved
            # to unrelated companies. Keep the board only when it can show the
            # company is its own.
            named = [p for p in got
                     if attribution.mentions_company(company, p["title"] + "\n" + p["text"])]
            if named:
                postings = named
                provider_used = (pname, tok)
                break
            bump("job_board_rejected")
        if postings:
            break
    if not postings:
        return []
    signal, noise = [], []
    for p in postings:
        if not p["url"].startswith("http"):
            continue
        blob = (p["title"] + "\n" + p["text"]).lower()
        if len(p["text"]) < MIN_TEXT:
            continue
        if any(w in blob for w in JOB_ROLE_WORDS) or score_signal(blob) > 0:
            signal.append(p)
        else:
            noise.append(p)
    rng.shuffle(noise)
    keep = signal[:MAX_JOBS_PER_COMPANY]
    if len(keep) < MAX_JOBS_PER_COMPANY:
        keep.extend(noise[:MAX_JOBS_PER_COMPANY - len(keep)])
    docs = []
    for p in keep:
        text = p["text"]
        if has_markup(text):
            continue
        docs.append(Doc("%s-%s" % (p["provider"], p["id"]), company["key"], p["url"],
                        urllib.parse.urlparse(p["url"]).netloc, "job_posting",
                        p["date"], (p["title"] or "")[:300], text, observed))
    company["_job_provider"] = provider_used
    return docs


def registry_doc(company, observed):
    q = company["gleif"]
    url = ("https://api.gleif.org/api/v1/lei-records?filter%5Bfulltext%5D=" +
           urllib.parse.quote(q) + "&page%5Bsize%5D=5")
    try:
        raw, _, _ = http_get(url, headers={"User-Agent": PLAIN_UA}, host_limit=REG_WORKERS,
                             timeout=30, retries=1)
        data = json.loads(raw.decode("utf-8", "replace"))
    except Exception:
        return None
    records = data.get("data") or []
    if not records:
        bump("registry_empty")
        return None
    targets = [company["name"], company["gleif"]] + company["aliases"]
    stop = {"group", "holding", "holdings", "international", "company", "limited",
            "corp", "corporation", "systems", "solutions", "services", "gmbh", "ag",
            "plc", "the", "and"}
    words = []
    for t in targets:
        for w in re.split(r"[^a-z0-9]+", t.lower()):
            if len(w) > 3 and w not in stop and w not in words:
                words.append(w)
    best = None
    for rec in records:
        a = rec.get("attributes") or {}
        name = ((a.get("entity") or {}).get("legalName") or {}).get("name") or ""
        low = name.lower()
        hits = sum(1 for w in words if w in low)
        if hits > (best[0] if best else -1):
            best = (hits, rec, name)
    if not best or best[0] == 0:
        return None
    rec = best[1]
    a = rec["attributes"]
    text = json.dumps(rec, ensure_ascii=False, indent=1)
    text = clean_text(text)
    if len(text) < MIN_TEXT:
        return None
    reg = a.get("registration") or {}
    published = iso_from_any(reg.get("lastUpdateDate") or reg.get("initialRegistrationDate") or "")
    return Doc("lei-%s" % rec.get("id"), company["key"],
               "https://api.gleif.org/api/v1/lei-records/" + str(rec.get("id")),
               "api.gleif.org", "registry", published,
               "LEI record: " + best[2], text, observed)


FILING_FORMS = {"10-K", "10-Q", "8-K", "20-F", "6-K", "DEF 14A", "40-F", "S-1"}


def filing_docs(company, observed):
    ticker = company.get("ticker")
    if not ticker or not TICKER_MAP:
        return []
    cik = TICKER_MAP.get(ticker.upper())
    if not cik:
        return []
    sub_url = "https://data.sec.gov/submissions/CIK%s.json" % cik
    try:
        raw, _, _ = http_get(sub_url, headers={"User-Agent": SEC_UA}, host_limit=FILING_WORKERS,
                             timeout=40, retries=1)
        sub = json.loads(raw.decode("utf-8", "replace"))
    except Exception:
        return []
    recent = (sub.get("filings") or {}).get("recent") or {}
    forms = recent.get("form") or []
    accs = recent.get("accessionNumber") or []
    docs_ = recent.get("primaryDocument") or []
    dates = recent.get("filingDate") or []
    descs = recent.get("primaryDocDescription") or []
    picked = []
    for i in range(len(forms)):
        if forms[i] in FILING_FORMS and i < len(accs) and i < len(docs_) and docs_[i]:
            picked.append(i)
        if len(picked) >= MAX_FILINGS_PER_COMPANY:
            break
    out = []
    for i in picked:
        acc = accs[i].replace("-", "")
        primary = docs_[i]
        doc_url = "https://www.sec.gov/Archives/edgar/data/%s/%s/%s" % (cik.lstrip("0"), acc, primary)
        try:
            raw2, ctype2, _ = http_get(doc_url, headers={"User-Agent": SEC_UA},
                                       host_limit=FILING_WORKERS, timeout=45, retries=1,
                                       limit_bytes=2_000_000)
            text = extract_article(raw2, ctype2)
        except Exception:
            continue
        if len(text) < MIN_TEXT or has_markup(text):
            continue
        title = "%s %s" % (forms[i], descs[i] if i < len(descs) else "")
        out.append(Doc("sec-%s" % accs[i], company["key"], doc_url, "sec.gov", "filing",
                       iso_from_any(dates[i] if i < len(dates) else ""),
                       re.sub(r"\s+", " ", title).strip()[:300], text, observed))
    return out


def build_company(company, observed, sources=None):
    docs = []
    wanted = sources or ("news", "job_posting", "registry", "filing")
    if "news" in wanted:
        try:
            docs.extend(news_docs(company, observed))
        except Exception as exc:
            bump("company_news_error")
    if "job_posting" in wanted:
        try:
            docs.extend(job_docs(company, observed))
        except Exception:
            bump("company_job_error")
    if "registry" in wanted:
        try:
            r = registry_doc(company, observed)
            if r:
                docs.append(r)
        except Exception:
            bump("company_registry_error")
    if "filing" in wanted:
        try:
            docs.extend(filing_docs(company, observed))
        except Exception:
            bump("company_filing_error")
    seen = set()
    uniq = []
    for d in docs:
        k = (d.source_kind, d.source_url)
        if k in seen:
            continue
        seen.add(k)
        uniq.append(d)
    return company, uniq


def coverage_note(company, docs):
    kinds = {}
    for d in docs:
        kinds[d.source_kind] = kinds.get(d.source_kind, 0) + 1
    parts = []
    for k in ("news", "job_posting", "registry", "filing"):
        if kinds.get(k):
            parts.append("%d %s" % (kinds[k], k))
    jp = company.get("_job_provider")
    if jp:
        parts.append("board=%s/%s" % jp)
    return "; ".join(parts) if parts else "no evidence retrieved"


def validate(path):
    with open(path, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    errs = []
    comps = data["companies"]
    ev = data["evidence"]
    keys = {c["key"] for c in comps}
    kinds = {}
    per_company = {}
    for e in ev:
        if e["sourceKind"] not in ("job_posting", "news", "registry", "filing"):
            errs.append("bad kind %s" % e["key"])
        kinds[e["sourceKind"]] = kinds.get(e["sourceKind"], 0) + 1
        per_company[e["companyKey"]] = per_company.get(e["companyKey"], 0) + 1
        if not e["sourceUrl"] or not str(e["sourceUrl"]).startswith("http"):
            errs.append("bad url %s" % e["key"])
        if not e["text"] or len(e["text"]) < MIN_TEXT:
            errs.append("short text %s (%d)" % (e["key"], len(e["text"])))
        if has_markup(e["text"]):
            errs.append("markup in %s" % e["key"])
        if e["contentHash"] != content_hash(e["text"]):
            errs.append("hash mismatch %s" % e["key"])
        if e["companyKey"] not in keys:
            errs.append("orphan %s" % e["key"])
    rich = sum(1 for k, v in per_company.items() if v >= 6)
    news_share = kinds.get("news", 0) / max(1, len(ev))
    ages = []
    undated = 0
    for e in ev:
        a = age_days(e["publishedAt"])
        if a is None:
            undated += 1
        else:
            ages.append(a)
    ages.sort()

    def pct(p):
        if not ages:
            return None
        return ages[min(len(ages) - 1, int(len(ages) * p))]

    theme_counts = {}
    company_themes = {}
    for e in ev:
        low = e["text"].lower()
        best, best_n = "other", 0
        for th in THEME_ORDER:
            n = sum(1 for w in THEME_WORDS[th] if w in low)
            if n > best_n:
                best, best_n = th, n
        theme_counts[best] = theme_counts.get(best, 0) + 1
        if best != "other":
            company_themes.setdefault(e["companyKey"], set()).add(best)
    tot_theme = sum(theme_counts.values()) or 1
    quiet = sum(1 for c in comps if per_company.get(c["key"], 0) <= 4)
    print(json.dumps({
        "companies": len(comps),
        "evidence": len(ev),
        "kinds": kinds,
        "companies_with_6_plus": rich,
        "competitors": sum(1 for c in comps if c["isCompetitor"]),
        "news_share": round(news_share, 3),
        "age_days": {"median": pct(0.5), "p25": pct(0.25), "p75": pct(0.75),
                     "max": ages[-1] if ages else None, "undated": undated},
        "within_12_months": sum(1 for a in ages if a <= 365),
        "theme_mix": {k: round(v / tot_theme, 3) for k, v in
                      sorted(theme_counts.items(), key=lambda x: -x[1])},
        "companies_with_2_plus_themes": sum(
            1 for c in comps if len(company_themes.get(c["key"], ())) >= 2),
        "thin_companies": quiet,
        "errors": len(errs),
    }, indent=1))
    for e in errs[:20]:
        print("  ERR", e)
    return len(errs) == 0


def carried_over(existing, evidence, companies_out):
    """A sweep replaces only the source it went and fetched, for the companies it touched.

    Running this with --only used to rewrite the corpus file with just the
    companies named, so a targeted rerun silently deleted every other company's
    evidence. Replacing a whole company is the same mistake one level down: a
    job-postings-only pass would clear that company's news, registry and filing
    documents too, which is what happened on the first run of this mode. A
    document survives unless this pass produced documents of its kind for its
    company.
    """
    replaced = {(d["companyKey"], d["sourceKind"]) for d in evidence}
    kept = [d for d in existing.get("evidence", [])
            if (d.get("companyKey"), d.get("sourceKind")) not in replaced]
    companies = {c["key"]: c for c in existing.get("companies", [])}
    for company in companies_out:
        companies[company["key"]] = company
    return kept, list(companies.values())


def main():
    only = None
    limit = None
    sources = None
    tier = "all"
    for arg in sys.argv[1:]:
        if arg.startswith("--only="):
            only = set(arg.split("=", 1)[1].split(","))
        elif arg.startswith("--limit="):
            limit = int(arg.split("=", 1)[1])
        elif arg.startswith("--sources="):
            sources = tuple(arg.split("=", 1)[1].split(","))
        elif arg.startswith("--tier="):
            tier = arg.split("=", 1)[1]
    observed = now_iso()
    load_sec_tickers()
    todo = [c2 for c2 in COMPANIES if only is None or c2["key"] in only]
    if tier == "empty":
        try:
            with open(OUT, encoding="utf-8") as fh:
                have = {e["companyKey"] for e in json.load(fh).get("evidence", [])}
        except (OSError, ValueError):
            have = set()
        todo = [c for c in todo if c["key"] not in have]
    if limit:
        todo = todo[:limit]
    print("[run] companies:", len(todo), "sources:", sources or "all", flush=True)
    results = {}
    done = 0
    with futures.ThreadPoolExecutor(max_workers=48) as pool:
        futs = {pool.submit(build_company, c2, observed, sources): c2 for c2 in todo}
        for fut in futures.as_completed(futs):
            comp = futs[fut]
            try:
                company, docs = fut.result()
            except Exception as exc:
                print("[fail]", comp["key"], type(exc).__name__, str(exc)[:100], flush=True)
                company, docs = comp, []
            results[company["key"]] = (company, docs)
            done += 1
            if done % 10 == 0 or done == len(todo):
                print("[%d/%d] %s -> %d docs" % (done, len(todo), company["key"], len(docs)),
                      flush=True)
    companies_out = []
    evidence = []
    for comp in todo:
        company, docs = results.get(comp["key"], (comp, []))
        companies_out.append({
            "key": company["key"],
            "name": company["name"],
            "domain": company["domain"],
            "country": company["country"],
            "hqCity": company["hqCity"],
            "sizeBand": company["sizeBand"],
            "industry": company["industry"],
            "isCompetitor": company["isCompetitor"],
            "coverageNotes": coverage_note(company, docs),
            "aliases": company["aliases"],
        })
        evidence.extend(d.to_json() for d in docs)
    fresh = []
    stale = {}
    for e in evidence:
        a = age_days(e["publishedAt"])
        if a is not None and a > MAX_AGE_DAYS:
            stale.setdefault(e["companyKey"], []).append(e)
            continue
        fresh.append(e)
    have = {e["companyKey"] for e in fresh}
    for key in sorted(stale):
        if key in have:
            continue
        keep = sorted(stale[key], key=lambda e: e["publishedAt"] or "")[-1]
        fresh.append(keep)
        bump("stale_kept_for_coverage")
    if len(fresh) >= 1600:
        bump("stale_dropped", len(evidence) - len(fresh))
        evidence = fresh
    evidence.sort(key=lambda e: (e["companyKey"], e["sourceKind"], e["key"]))
    try:
        with open(OUT, encoding="utf-8") as fh:
            existing = json.load(fh)
    except (OSError, ValueError):
        existing = {}
    kept, every_company = carried_over(existing, evidence, companies_out)
    if kept:
        print("[keep] %d documents carried over" % len(kept), flush=True)
    payload = {
        "generatedAt": observed,
        "companies": every_company,
        "evidence": kept + evidence,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print("[stats]", json.dumps(STATS, indent=1), flush=True)
    print("[out]", OUT, os.path.getsize(OUT), "bytes", flush=True)
    validate(OUT)


if __name__ == "__main__":
    main()

