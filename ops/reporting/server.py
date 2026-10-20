"""Voxkin's opt-in report receiver. No dependencies, credentials, or body logging."""
import collections
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

RETENTION = 30 * 24 * 60 * 60
MAX_BODY = 200000
REASONS = {"harmful", "hate", "sexual", "inaccurate", "other"}


class Rejection(Exception):
    def __init__(self, status):
        self.status = status


def validate(raw):
    def unique(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValueError("duplicate")
            value[key] = item
        return value
    try:
        data = json.loads(raw.decode("utf-8"), object_pairs_hook=unique)
        if not isinstance(data, dict) or set(data) != {"reportId", "response", "reason", "note"}:
            raise ValueError("fields")
        if not all(isinstance(value, str) for value in data.values()):
            raise ValueError("types")
        if str(uuid.UUID(data["reportId"])) != data["reportId"]:
            raise ValueError("uuid")
        if len(data["response"].encode("utf-16-le", errors="surrogatepass")) // 2 > 12000:
            raise ValueError("response")
        if data["reason"] not in REASONS or len(data["note"].encode("utf-16-le", errors="surrogatepass")) // 2 > 2000:
            raise ValueError("reason/note")
        if any(any(0xD800 <= ord(c) <= 0xDFFF for c in v) for v in data.values()):
            raise ValueError("surrogate")
        return data
    except (ValueError, UnicodeError, TypeError, RecursionError):
        raise Rejection(400) from None


class Store:
    def __init__(self, path, clock=time.time):
        self.clock = clock
        self.lock = threading.Lock()
        self.db = sqlite3.connect(path, check_same_thread=False)
        # Deleted content is overwritten in the main database; do not create WAL archives.
        self.db.execute("PRAGMA secure_delete=ON")
        self.db.execute("PRAGMA journal_mode=DELETE")
        self.db.execute("CREATE TABLE IF NOT EXISTS reports (id TEXT PRIMARY KEY, created REAL NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'new')")
        self.db.execute("CREATE INDEX IF NOT EXISTS report_created ON reports(created)")
        self.db.commit()
        self.purge()

    def purge(self):
        with self.lock, self.db:
            self.db.execute("DELETE FROM reports WHERE created <= ?", (self.clock() - RETENTION,))

    def accept(self, data):
        payload = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        with self.lock, self.db:
            self.db.execute("DELETE FROM reports WHERE created <= ?", (self.clock() - RETENTION,))
            prior = self.db.execute("SELECT payload FROM reports WHERE id=?", (data["reportId"],)).fetchone()
            if prior:
                if prior[0] != payload:
                    raise Rejection(409)
                return 200
            if self.db.execute("SELECT count(*) FROM reports").fetchone()[0] >= 10000:
                raise Rejection(503)
            self.db.execute("INSERT INTO reports (id,created,payload) VALUES (?,?,?)", (data["reportId"], self.clock(), payload))
        return 201


class Limiter:
    """Transient IP-derived buckets, process-random salt; no stored IPs or logs."""
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.salt = os.urandom(32)
        self.buckets = {}
        self.lock = threading.Lock()

    def allow(self, address):
        key = hashlib.sha256(self.salt + address.encode()).digest()
        now = self.clock()
        with self.lock:
            self.buckets = {k: v for k, v in self.buckets.items() if v[-1] > now - 3600}
            if key not in self.buckets and len(self.buckets) >= 4096:
                return False
            bucket = self.buckets.setdefault(key, collections.deque())
            while bucket and bucket[0] <= now - 3600:
                bucket.popleft()
            if len(bucket) >= 10 or sum(t > now - 60 for t in bucket) >= 3:
                return False
            bucket.append(now)
            return True


def handler(store, limiter):
    class Handler(BaseHTTPRequestHandler):
        server_version = "VoxkinReports"
        sys_version = ""

        def log_message(self, *_):
            pass

        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def reply(self, status, data):
            body = json.dumps(data).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            self.close_connection = True

        def do_POST(self):
            try:
                if self.path != "/api/reports":
                    raise Rejection(404)
                # This port binds loopback. Caddy overwrites this header, never trusts callers.
                address = self.headers.get("X-Report-Client-IP", self.client_address[0])
                if not limiter.allow(address):
                    raise Rejection(429)
                if self.headers.get("Transfer-Encoding") or self.headers.get("Content-Encoding"):
                    raise Rejection(400)
                if self.headers.get_content_type() != "application/json":
                    raise Rejection(415)
                lengths = self.headers.get_all("Content-Length", [])
                if len(lengths) != 1 or not lengths[0].isdigit():
                    raise Rejection(411)
                length = int(lengths[0])
                if length <= 0 or length > MAX_BODY:
                    raise Rejection(413)
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise Rejection(400)
                data = validate(raw)
                status = store.accept(data)
                self.reply(status, {"reportId": data["reportId"]})
            except Rejection as error:
                self.reply(error.status, {"error": "report_not_accepted"})
            except (sqlite3.Error, OSError):
                self.reply(503, {"error": "report_not_accepted"})

        def do_GET(self):
            self.reply(405, {"error": "method_not_allowed"})

    return Handler


class BoundedServer(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, *args, **kwargs):
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(*args, **kwargs)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()

    def handle_error(self, *_):
        # Never emit exceptions containing HTTP bodies/header data.
        pass


def main():
    os.umask(0o077)
    directory = Path(os.environ.get("VOXKIN_REPORT_DIR", "/var/lib/voxkin-reports"))
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(directory, 0o700)
    store = Store(directory / "reports.sqlite3")
    os.chmod(directory / "reports.sqlite3", 0o600)
    def cleanup():
        while True:
            time.sleep(30)
            try:
                store.purge()
            except sqlite3.Error:
                # Fail closed and let systemd restart/purge before accepting more reports.
                os._exit(1)
    threading.Thread(target=cleanup, daemon=True).start()
    BoundedServer(("127.0.0.1", 8793), handler(store, Limiter())).serve_forever()


if __name__ == "__main__":
    main()
