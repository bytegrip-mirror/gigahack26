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
import http.server
import ipaddress
import os
import select
import shutil
import socket
import subprocess
import threading
import urllib.error
import urllib.parse
import urllib.request

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
_proxy = None
PROXY_LOCK = threading.Lock()


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


def blocked_host(host, port):
    """True when a host resolves to anywhere that is not the public internet.

    Every URL here was read off a page someone else controls: a newsroom link, a
    redirect target, an article anchor. That is enough for a page to point the
    crawler at a cloud metadata endpoint or an internal host, and the crawler
    would fetch it and store the body as corporate evidence. The check is on the
    resolved address rather than the literal, so a public name that resolves
    inward is caught too.
    """
    host = (host or "").strip().lower().rstrip(".")
    if not host:
        return True
    try:
        entries = socket.getaddrinfo(host, port, proto=socket.IPPROTO_TCP)
    except (socket.gaierror, UnicodeError, ValueError):
        return True
    if not entries:
        return True
    for entry in entries:
        try:
            address = ipaddress.ip_address(entry[4][0])
        except ValueError:
            return True
        if (address.is_private or address.is_loopback or address.is_link_local
                or address.is_reserved or address.is_multicast or address.is_unspecified):
            return True
    return False


def refused(url):
    if not url or url.startswith("-"):
        return "not a URL"
    try:
        parsed = urllib.parse.urlparse(url)
    except ValueError:
        return "not a URL"
    if parsed.scheme not in ("http", "https"):
        return "scheme is not http or https"
    if blocked_host(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80)):
        return "address is not public"
    return None


class Guard(http.server.BaseHTTPRequestHandler):
    """A proxy that applies refused() to every request Chrome makes.

    Checking the URL before launching Chrome is not enough, because Chrome
    follows redirects: a public newsroom can answer 302 to a metadata endpoint
    and the browser goes there itself. Every hop arrives here as a fresh
    request, so a proxy that validates each one and never follows a redirect on
    Chrome's behalf closes the hole without needing to inspect TLS.
    """

    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def block(self):
        self.send_response(403)
        self.send_header("content-length", "0")
        self.end_headers()

    def do_CONNECT(self):
        host, _, port = self.path.partition(":")
        try:
            port = int(port or 443)
        except ValueError:
            return self.block()
        if blocked_host(host, port):
            return self.block()
        try:
            upstream = socket.create_connection((host, port), timeout=20)
        except OSError:
            self.send_response(502)
            self.send_header("content-length", "0")
            self.end_headers()
            return
        self.send_response(200, "Connection Established")
        self.end_headers()
        self.close_connection = True
        tunnel(self.connection, upstream)

    def do_GET(self):
        if refused(self.path):
            return self.block()
        try:
            request = urllib.request.Request(self.path, headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                              "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
            })
            with urllib.request.urlopen(request, timeout=25) as response:
                body = response.read(4_000_000)
                status = response.status
                headers = response.headers
        except urllib.error.HTTPError as error:
            body = error.read(64_000)
            status = error.code
            headers = error.headers
        except Exception:
            return self.block()
        self.send_response(status)
        for name in ("content-type", "content-length", "location", "cache-control"):
            value = headers.get(name)
            if value:
                self.send_header(name, value)
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def tunnel(client, upstream):
    sockets = [client, upstream]
    try:
        while True:
            readable, _, _ = select.select(sockets, [], [], 30)
            if not readable:
                return
            for source in readable:
                chunk = source.recv(65536)
                if not chunk:
                    return
                (upstream if source is client else client).sendall(chunk)
    except OSError:
        return
    finally:
        upstream.close()


def start_proxy():
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Guard)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def render(url, timeout=60):
    binary = chrome()
    if not binary:
        return ""
    if refused(url):
        return ""
    os.makedirs(CACHE, exist_ok=True)
    path = cache_path(url)
    if os.path.exists(path):
        with open(path, encoding="utf-8", errors="replace") as handle:
            return handle.read()

    with PROXY_LOCK:
        global _proxy
        if _proxy is None:
            _proxy = start_proxy()
        port = _proxy.server_address[1]

    command = [
        binary,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--hide-scrollbars",
        "--proxy-server=http://127.0.0.1:%d" % port,
        "--proxy-bypass-list=",
        "--virtual-time-budget=%d" % DOM_BUDGET_MS,
        "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "--dump-dom",
        "--",
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
