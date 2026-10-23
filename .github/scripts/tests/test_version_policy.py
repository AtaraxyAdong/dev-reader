import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import hashlib

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from version_policy import CHECKS, validate_acceptance, validate_version, validate_repository
spec = importlib.util.spec_from_file_location("publisher", SCRIPTS / "publish-ci-release.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class VersionPolicyTest(unittest.TestCase):
    def test_pure_numeric_versions(self):
        for value in ("0.1.0", "1.0.0", "12.34.56"):
            with self.subTest(value=value):
                result = validate_version(value, "dev-reader", "refs/tags/v" + value)
                self.assertEqual(f"dev-reader-{value}.zip", result["zip_name"])

    def test_rejects_custom_or_ambiguous_versions(self):
        for value in ("v1.0.0", "01.0.0", "1.0", "1.0.0-rc.1", "1.0.0-rc.0", "1.0.0-rc.01", "1.0.0-ui.1", "1.0.0-local", "1.0.0-20261023", "1.0.0+build", "1.0.0\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_version(value, "dev-reader", "refs/heads/master")

    def test_legacy_ui_value_is_forbidden_even_on_branches(self):
        for ref in ("refs/heads/master", "refs/tags/v0.5.15-ui.2"):
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                validate_version("0.5.15-ui.2", "dev-reader", ref)

    def test_tag_and_product_must_match(self):
        for product, ref in [("Mianshiya", "refs/tags/v1.0.0"), ("dev-reader", "refs/tags/v1.0.1"), ("dev-reader", "refs/tags/1.0.0")]:
            with self.subTest(product=product, ref=ref), self.assertRaises(ValueError):
                validate_version("1.0.0", product, ref)

    def test_real_acceptance_requires_all_critical_evidence(self):
        record = {"version": "1.0.0", "status": "passed", "tester": "Test maintainer",
                  "environment": "Mac / IDEA 252 / actual authorized account", "tested_commit": "a" * 40,
                  "checks": {k: {"status": "passed", "evidence": "Public summary of actual check"} for k in CHECKS}}
        validate_acceptance(record, "1.0.0")
        for key in CHECKS:
            for evidence in (None, "", " "):
                broken = copy.deepcopy(record)
                broken["checks"][key]["evidence"] = evidence
                with self.subTest(key=key, evidence=evidence), self.assertRaises(ValueError):
                    validate_acceptance(broken, "1.0.0")
        with self.assertRaises(ValueError):
            validate_acceptance(record, "1.0.1")
        record["status"] = "pending"
        with self.assertRaises(ValueError):
            validate_acceptance(record, "1.0.0")


class RepositoryGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "gradle.properties").write_text("pluginName = dev-reader\npluginVersion = 0.1.0\n")
        self.xml = self.root / "src/main/resources/META-INF/plugin.xml"
        self.xml.parent.mkdir(parents=True)
        self.xml.write_text("<idea-plugin><id>io.github.ataraxyadong.devreader</id><name>dev-reader</name></idea-plugin>")

    def test_build_does_not_require_or_fabricate_acceptance(self):
        result = validate_repository(self.root, "refs/heads/master")
        self.assertEqual("0.1.0", result["version"])
        self.assertEqual("true", result["prerelease"])

    def test_tag_requires_acceptance_record(self):
        with self.assertRaisesRegex(ValueError, "acceptance"):
            validate_repository(self.root, "refs/tags/v0.1.0")

    def test_duplicate_xml_version_is_rejected(self):
        self.xml.write_text(self.xml.read_text().replace("</idea-plugin>", "<version>0.1.0</version></idea-plugin>"))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            validate_repository(self.root, "refs/heads/master")

    def test_stable_partial_record_cannot_pass_as_verified(self):
        record = {"version": "1.0.0", "status": "partial", "tester": "tester", "environment": "IDEA252",
                  "tested_commit": "a" * 40, "limitations": ["Real account unavailable"],
                  "checks": {key: {"status": "not_run", "evidence": "Not performed"} for key in CHECKS}}
        with self.assertRaisesRegex(ValueError, "Stable"):
            validate_acceptance(record, "1.0.0")


class PublishSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / ".github").mkdir()
        (self.root / ".github/release-notes-template.md").write_text((SCRIPTS.parent / "release-notes-template.md").read_text())
        artifact = self.root / "release-artifact"
        artifact.mkdir()
        self.package = artifact / "dev-reader-0.1.0.zip"
        self.package.write_bytes(b"unit fixture, not a plugin distribution")
        self.checksum = artifact / "SHA256SUMS.txt"
        self.checksum.write_text(f"{hashlib.sha256(self.package.read_bytes()).hexdigest()}  {self.package.name}\n")
        self.env = {"GITHUB_EVENT_NAME": "push", "GITHUB_REF": "refs/tags/v0.1.0", "GITHUB_REPOSITORY": "owner/dev-reader", "GITHUB_RUN_ID": "123", "GITHUB_SHA": "a" * 40}
        acceptance = self.root / "release-acceptance"
        acceptance.mkdir()
        (acceptance / "0.1.0.json").write_text(json.dumps({"limitations": ["Actual account not tested"]}))
        self.metadata = {"version": "0.1.0", "zip_name": self.package.name, "prerelease": "true"}
        self.policy = patch.object(publisher, "validate_repository", return_value=self.metadata)
        self.policy.start()
        self.addCleanup(self.policy.stop)

    def test_public_notes_have_no_repeated_title_or_process_preamble(self):
        body = publisher.render_notes(self.root, self.metadata, self.env)
        self.assertTrue(body.startswith("## 功能\n"))
        self.assertEqual(["## 功能", "## 修复", "## 安装", "## 已知限制"], [line for line in body.splitlines() if line.startswith("#")])
        self.assertIn("dev-reader-0.1.0.zip", body)
        self.assertIn("Actual account not tested", body.split("## 已知限制")[1])
        self.assertNotIn("统一", body)
        self.assertNotIn("版本重置", body)

    def test_ordinary_push_cannot_publish(self):
        self.env["GITHUB_REF"] = "refs/heads/master"
        with self.assertRaises(ValueError):
            publisher.publish(self.root, self.env, run=lambda *a, **k: self.fail("No remote writes"))

    def test_existing_release_stops_without_mutation(self):
        calls = []
        def fake(command, **kwargs):
            calls.append(command)
            return subprocess.CompletedProcess(command, 0, json.dumps({"html_url": "https://example.invalid/existing"}), "")
        with self.assertRaisesRegex(ValueError, "already exists"):
            publisher.publish(self.root, self.env, run=fake)
        self.assertEqual(1, len(calls))
        self.assertEqual("api", calls[0][1])

    def test_uncertain_lookup_does_not_retry_or_publish(self):
        with self.assertRaisesRegex(RuntimeError, "investigation"):
            publisher.publish(self.root, self.env, run=lambda cmd, **kw: subprocess.CompletedProcess(cmd, 1, "", "network timeout"))

    def test_checksum_mismatch_stops_before_remote_call(self):
        self.package.write_bytes(b"altered")
        with self.assertRaisesRegex(ValueError, "checksum"):
            publisher.publish(self.root, self.env, run=lambda *a, **k: self.fail("No remote calls"))

    def test_early_numeric_release_only_uploads_expected_files(self):
        calls = []
        def fake(command, **kwargs):
            calls.append(command)
            return subprocess.CompletedProcess(command, 1 if command[1] == "api" else 0, "", "gh: Not Found (HTTP 404)")
        publisher.publish(self.root, self.env, run=fake)
        self.assertEqual(2, len(calls))
        self.assertIn("--verify-tag", calls[1])
        self.assertIn("--prerelease", calls[1])
        self.assertIn(str(self.package), calls[1])
        self.assertIn(str(self.checksum), calls[1])
        self.assertNotIn("--clobber", calls[1])


if __name__ == "__main__":
    unittest.main()
