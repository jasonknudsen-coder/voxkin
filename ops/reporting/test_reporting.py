import http.client
import json
from pathlib import Path
import tempfile
import threading
import unittest
import uuid
from server import BoundedServer, Limiter, MAX_BODY, RETENTION, Rejection, Store, handler, validate


def report(**changes):
    return dict(reportId=str(uuid.uuid4()), response="Fictional selected reply", reason="other", note="", **changes)


class ReportingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 10000000
        self.store = Store(Path(self.tmp.name) / "db", lambda: self.now)

    def tearDown(self):
        self.store.db.close()
        self.tmp.cleanup()

    def test_accept_duplicate_conflict_without_resetting_retention(self):
        data = report()
        self.assertEqual(self.store.accept(data), 201)
        self.now += 100
        self.assertEqual(self.store.accept(data), 200)
        with self.assertRaises(Rejection) as error:
            self.store.accept(dict(data, note="changed"))
        self.assertEqual(error.exception.status, 409)
        self.now += RETENTION - 100
        self.store.purge()
        self.assertEqual(self.store.db.execute("SELECT count(*) FROM reports").fetchone()[0], 0)

    def test_exact_allowlist_no_hidden_metadata(self):
        base = report()
        for data in [dict(base, gateway="secret"), dict(base, reason="invalid"), dict(base, note=[]), dict(base, reportId="bad"), []]:
            with self.assertRaises(Rejection):
                validate(json.dumps(data).encode())
        with self.assertRaises(Rejection):
            validate(b'{"reportId":"a","reportId":"b"}')
        with self.assertRaises(Rejection):
            validate(json.dumps(dict(base, note="\ud800")).encode())

    def test_redaction_and_utf16_limits(self):
        base = report()
        self.assertEqual(validate(json.dumps(dict(base, response="")).encode())["response"], "")
        validate(json.dumps(dict(base, response="😀" * 6000, note="😀" * 1000)).encode())
        for data in [dict(base, response="😀" * 6001), dict(base, note="😀" * 1001)]:
            with self.assertRaises(Rejection):
                validate(json.dumps(data).encode())

    def test_rate_limit_expires_without_retaining_addresses(self):
        limiter = Limiter(lambda: self.now)
        for _ in range(3):
            self.assertTrue(limiter.allow("192.0.2.1"))
        self.assertFalse(limiter.allow("192.0.2.1"))
        self.assertTrue(limiter.allow("192.0.2.2"))
        self.assertNotIn("192.0.2.1", repr(limiter.buckets))
        self.now += 3601
        self.assertTrue(limiter.allow("192.0.2.1"))
        self.assertEqual(len(limiter.buckets), 1)

    def test_http_receipt_errors_and_no_read_endpoint(self):
        service = BoundedServer(("127.0.0.1", 0), handler(self.store, Limiter()))
        thread = threading.Thread(target=service.serve_forever, daemon=True)
        thread.start()
        def request(method, path, body=None, headers=None):
            client = http.client.HTTPConnection(*service.server_address)
            client.request(method, path, body, headers or {})
            result = client.getresponse()
            value = (result.status, result.read(), result.getheader("Cache-Control"))
            client.close()
            return value
        try:
            data = report()
            headers = {"Content-Type": "application/json"}
            status, body, cache = request("POST", "/api/reports", json.dumps(data), headers)
            self.assertEqual(status, 201)
            self.assertEqual(json.loads(body), {"reportId": data["reportId"]})
            self.assertEqual(cache, "no-store")
            self.assertEqual(request("POST", "/api/reports", json.dumps(data), headers)[0], 200)
            self.assertEqual(request("POST", "/api/reports", "x" * (MAX_BODY + 1), headers)[0], 413)
            self.assertEqual(request("GET", "/api/reports")[0], 405)
            self.assertEqual(request("POST", "/api/reports?token=x", "{}")[0], 404)
        finally:
            service.shutdown()
            service.server_close()


if __name__ == "__main__":
    unittest.main()
