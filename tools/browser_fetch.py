"""Fetch pages a plain request cannot get.

Corporate newsrooms sit behind WAFs that answer 403 to a library user-agent and
000 to a datacentre address. Measured on 2026-09-26: plain urllib gets 403 from
lufthansagroup.com and no connection at all from dhl.com, while the same URLs
render under a headless browser because the block is on the request's
fingerprint and its TLS handshake, not on its address.

Chrome is invoked one process per page. It is expensive — a few hundred
milliseconds of startup and a few hundred megabytes — so callers go through
fetch_many, which keeps a small pool and falls back to a plain request first.
"""

import hashlib
import os
import shutil
import subprocess
import threading

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache", "browser")
CHROME_CANDIDATES = [
    os.environ.get("CHROME_BIN", ""),
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
]
WORKERS = 4
MAX_CONCURRENT = threading.Semaphore(WORKERS)
DOM_BUDGET_MS = 9000

_found = None
_found_lock = threading.Lock()


def chrome():
    global _found
    with _found_lock:
        if _found is None:
            for candidate in CHROME_CANDIDATES:
                if candidate and shutil.which(candidate):
                    _found = candidate
                    break
            else:
                _found = ""
        return _found


def available():
    return bool(chrome())


def cache_path(url):
    return os.path.join(CACHE, hashlib.sha1(url.encode()).hexdigest() + ".html")


def render(url, timeout=60):
    binary = chrome()
    if not binary:
        return ""
    os.makedirs(CACHE, exist_ok=True)
    path = cache_path(url)
    if os.path.exists(path):
        with open(path, encoding="utf-8", errors="replace") as handle:
            return handle.read()

    command = [
        binary,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--hide-scrollbars",
        "--virtual-time-budget=%d" % DOM_BUDGET_MS,
        "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "--dump-dom",
        url,
    ]
    with MAX_CONCURRENT:
        try:
            done = subprocess.run(command, capture_output=True, timeout=timeout, check=False)
        except subprocess.TimeoutExpired:
            return ""
    dom = done.stdout.decode("utf-8", "replace")
    if len(dom) < 200:
        return ""
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(dom)
    return dom
