#!/usr/bin/env python3
"""Fetch a company mark for every company in the corpus list.

Best effort by design: a company whose site will not part with an icon gets no
entry and the board draws a monogram instead. Three sources are tried in order —
the link the homepage declares, the conventional /favicon.ico, and a public
favicon service — and whatever comes back is normalised to one size so the board
never has to lay out fourteen aspect ratios.

Writes ui/static/logos/<key>.png and ui/src/lib/logos.json, which the board reads
at build time. Nothing is fetched at render time: the demo runs on venue wi-fi.

The one href here that is not ours is the icon a company's own homepage declares,
so it is treated as hostile input: http or https only, the host has to resolve
entirely to public addresses, and a redirect is re-checked before it is followed.
Nothing in this file is reachable from the deployed app — it runs against the
committed company list on a developer's machine — but a page that points its icon
at a link-local address should not be able to make this read one.
"""
import concurrent.futures as futures
import io
import ipaddress
import json
import os
import re
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_corpus import COMPANIES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATIC = os.path.join(ROOT, "ui", "static", "logos")
MANIFEST = os.path.join(ROOT, "ui", "src", "lib", "logos.json")

UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

SIZE = 32
WORKERS = 16
TIMEOUT = 8
MAX_BYTES = 400_000
MIN_BYTES = 200

ICON_LINK = re.compile(
    r"<link[^>]+rel=[\"'](?:shortcut\s+|apple-touch-)?icon[\"'][^>]*>", re.I
)
HREF = re.compile(r"href=[\"']([^\"']+)[\"']", re.I)
SIZES = re.compile(r"sizes=[\"'](\d+)", re.I)


def public(url: str) -> bool:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        return False
    try:
        resolved = socket.getaddrinfo(parsed.hostname, None)
    except OSError:
        return False
    for entry in resolved:
        try:
            address = ipaddress.ip_address(entry[4][0])
        except ValueError:
            return False
        if not address.is_global:
            return False
    return True


class GuardedRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not public(newurl):
            raise urllib.error.URLError(f"refused a redirect to {newurl}")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = urllib.request.build_opener(GuardedRedirect)


def get(url: str) -> bytes | None:
    if not public(url):
        return None
    try:
        request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
        with OPENER.open(request, timeout=TIMEOUT) as response:
            if response.status != 200:
                return None
            return response.read(MAX_BYTES)
    except Exception:
        return None


def decode(raw: bytes | None) -> Image.Image | None:
    if not raw or len(raw) < MIN_BYTES:
        return None
    try:
        image = Image.open(io.BytesIO(raw))
        image.load()
    except Exception:
        return None
    if image.width < 8 or image.height < 8:
        return None
    return image


def declared(domain: str) -> list[str]:
    page = get(f"https://{domain}/")
    if not page:
        return []
    html = page.decode("utf-8", "ignore")
    found = []
    for tag in ICON_LINK.findall(html):
        match = HREF.search(tag)
        if not match:
            continue
        url = urllib.parse.urljoin(f"https://{domain}/", match.group(1))
        size = SIZES.search(tag)
        found.append((int(size.group(1)) if size else 0, url))
    found.sort(reverse=True)
    return [url for _, url in found]


def candidates(domain: str) -> list[str]:
    return (
        declared(domain)
        + [f"https://{domain}/favicon.ico"]
        + [f"https://icons.duckduckgo.com/ip3/{domain}.ico"]
        + [f"https://www.google.com/s2/favicons?sz=128&domain={domain}"]
    )


def flatten(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    if image.getextrema()[3][0] < 255:
        canvas = Image.new("RGBA", image.size, (255, 255, 255, 255))
        canvas.alpha_composite(image)
        image = canvas
    return image.convert("RGB")


def one(company: dict) -> None:
    os.makedirs(STATIC, exist_ok=True)
    target = os.path.join(STATIC, f"{company['key']}.png")
    if os.path.exists(target) and os.path.getsize(target) > 0:
        return
    for url in candidates(company["domain"]):
        image = decode(get(url))
        if image is None:
            continue
        try:
            image = flatten(image)
        except Exception:
            continue
        # A square of one colour is a placeholder, not a mark. getcolors returns
        # None once the image holds more colours than it was asked to count,
        # which is a mark and not a placeholder.
        colors = image.getcolors(maxcolors=8)
        if colors is not None and len(colors) <= 1:
            continue
        image.resize((SIZE, SIZE), Image.LANCZOS).save(target, "PNG", optimize=True)
        return


def main() -> int:
    os.makedirs(STATIC, exist_ok=True)
    done = 0
    with futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for _ in pool.map(one, COMPANIES):
            done += 1
            if done % 25 == 0:
                print(f"  {done}/{len(COMPANIES)}", flush=True)

    kept = {}
    missing = []
    for company in COMPANIES:
        target = os.path.join(STATIC, f"{company['key']}.png")
        if os.path.exists(target) and os.path.getsize(target) > 0:
            kept[company["domain"]] = f"{company['key']}.png"
        else:
            missing.append(company["key"])

    with open(MANIFEST, "w") as handle:
        json.dump(dict(sorted(kept.items())), handle, indent="\t")
        handle.write("\n")

    print(f"{len(kept)} of {len(COMPANIES)} companies have a mark")
    if missing:
        print("no mark: " + ", ".join(missing))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
