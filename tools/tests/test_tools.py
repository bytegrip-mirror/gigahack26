"""The corpus tools, checked where a mistake costs documents silently.

Run with: python3 -m unittest discover -s tools/tests -t .
"""

import json
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import browser_fetch
import model_overlay
import newsroom_corpus
import vendor_registry
import verify_corpus


class VendorRegistryTest(unittest.TestCase):

    def test_a_product_vendor_is_named(self):
        for name in ("UiPath", "Nintex", "Celonis", "Appian", "Automation Anywhere"):
            self.assertTrue(vendor_registry.is_vendor(name, "Software"), name)

    def test_a_legal_suffix_does_not_hide_one(self):
        self.assertTrue(vendor_registry.named_vendor("UiPath, Inc."))
        self.assertTrue(vendor_registry.named_vendor("Celonis SE"))
        self.assertTrue(vendor_registry.named_vendor("Nintex UK Ltd"))

    def test_a_buyer_is_not_a_vendor(self):
        for name, industry in (("Dassault Systemes", "Software"), ("TeamViewer", "Software"),
                               ("Siemens", "Industrial Automation"), ("Reckitt", "Consumer Goods"),
                               ("Virgin Media O2", "Telecom")):
            self.assertFalse(vendor_registry.is_vendor(name, industry), name)

    def test_null_is_not_a_vendor(self):
        self.assertFalse(vendor_registry.is_vendor(None, None))
        self.assertFalse(vendor_registry.named_vendor(None))


class OverlayLocateTest(unittest.TestCase):

    def test_an_exact_quote_resolves_to_the_documents_own_bytes(self):
        document = "The company said it will cut 4,000 roles by 2030. More text follows here."
        span = model_overlay.locate(document, "cut 4,000 roles by 2030")
        self.assertIsNotNone(span)
        self.assertIn(span, document)

    def test_a_quote_with_different_whitespace_still_resolves(self):
        document = "We   are\n\nhiring automation engineers across the group."
        span = model_overlay.locate(document, "We are hiring automation engineers")
        self.assertIsNotNone(span)

    def test_a_quote_that_is_not_there_is_refused(self):
        self.assertIsNone(model_overlay.locate("Nothing relevant in this text at all.",
                                               "a sentence that never appeared"))


class MissingPageTest(unittest.TestCase):

    def test_a_not_found_page_is_recognised(self):
        for text in ("Unfortunately the page you are looking for is unavailable or no longer exists.",
                     "404 - Page not found",
                     "Sorry, this page does not exist"):
            self.assertTrue(newsroom_corpus.MISSING.search(text), text)

    def test_ordinary_prose_is_not(self):
        for text in ("The company announced a cost programme worth 200 million euros.",
                     "We are hiring process excellence leads in Bucharest."):
            self.assertIsNone(newsroom_corpus.MISSING.search(text), text)


class BrowserGuardTest(unittest.TestCase):

    def test_private_and_non_http_targets_are_refused(self):
        for url in ("http://127.0.0.1:8080/", "http://169.254.169.254/latest/meta-data/",
                    "http://10.0.0.5/", "file:///etc/passwd", "--headless=x", ""):
            self.assertIsNotNone(browser_fetch.refused(url), url)

    def test_a_public_url_is_allowed(self):
        self.assertIsNone(browser_fetch.refused("https://example.com/news"))


class CorpusCheckTest(unittest.TestCase):

    def write(self, payload):
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        json.dump(payload, handle)
        handle.close()
        self.addCleanup(os.unlink, handle.name)
        return handle.name

    def corpus(self, evidence):
        return self.write({"generatedAt": "2026-01-01T00:00:00Z",
                           "companies": [{"key": "acme", "name": "Acme"}],
                           "evidence": evidence})

    def row(self, key, text="a real body of text long enough to be a document"):
        import hashlib
        return {"key": key, "companyKey": "acme", "sourceUrl": "https://example.com/a",
                "publisher": "example.com", "sourceKind": "news", "title": "A story",
                "text": text, "contentHash": "sha256:" + hashlib.sha256(text.encode()).hexdigest()}

    def test_a_clean_corpus_passes(self):
        path = self.corpus([self.row("a"), self.row("b")])
        report = verify_corpus.check(verify_corpus.Report(), path, "/nonexistent")
        self.assertEqual([], report.failures)

    def test_a_repeated_key_fails(self):
        path = self.corpus([self.row("nws-1"), self.row("nws-1")])
        report = verify_corpus.check(verify_corpus.Report(), path, "/nonexistent")
        self.assertTrue(any("repeat" in f for f in report.failures), report.failures)

    def test_a_hash_that_does_not_match_its_text_fails(self):
        row = self.row("a")
        row["contentHash"] = "sha256:" + "0" * 64
        report = verify_corpus.check(verify_corpus.Report(), self.corpus([row]), "/nonexistent")
        self.assertTrue(any("content hash" in f for f in report.failures), report.failures)

    def test_evidence_for_an_unknown_company_fails(self):
        row = self.row("a")
        row["companyKey"] = "nobody"
        report = verify_corpus.check(verify_corpus.Report(), self.corpus([row]), "/nonexistent")
        self.assertTrue(any("unknown company" in f for f in report.failures), report.failures)

    def test_an_overlay_pointing_at_missing_evidence_fails(self):
        path = self.corpus([self.row("a")])
        overlay = self.write({"engine": "x", "signals": [
            {"evidenceKey": "gone", "questionKey": "q", "verdict": "BUY", "confidence": 0.9,
             "quote": "x", "rationale": "y"}]})
        report = verify_corpus.check(verify_corpus.Report(), path, overlay)
        self.assertTrue(any("overlay rows point" in f for f in report.failures), report.failures)


if __name__ == "__main__":
    unittest.main()
