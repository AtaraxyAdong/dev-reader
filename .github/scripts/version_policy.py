"""Numeric version and release identity policy; shared by CI and tests."""
import json
import re
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

PRODUCT = "dev-reader"
VERSION = re.compile(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)", re.ASCII)
CHECKS = ("install_startup", "real_account_content", "navigation_shortcuts", "markdown_images", "settings_persistence")


def properties(root):
    result = {}
    for line in (Path(root) / "gradle.properties").read_text().splitlines():
        if "=" not in line or line.lstrip().startswith("#"):
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key in result:
            raise ValueError(f"Duplicate property: {key}")
        result[key] = value
    return result


def validate_version(version, product, ref):
    if product != PRODUCT:
        raise ValueError(f"Stable product name must be {PRODUCT}")
    if not VERSION.fullmatch(version):
        raise ValueError("Version must be pure numeric X.Y.Z; suffixes are forbidden")
    if ref.startswith("refs/tags/") and ref != f"refs/tags/v{version}":
        raise ValueError("Tag must equal v + pluginVersion")
    return {"version": version, "zip_name": f"{product}-{version}.zip"}


def validate_acceptance(record, version):
    if record.get("version") != version or record.get("status") not in ("passed", "partial"):
        raise ValueError("Release requires a matching passed or honest partial acceptance record")
    for key in ("tester", "environment"):
        if not isinstance(record.get(key), str) or not record[key].strip():
            raise ValueError(f"Missing acceptance {key}")
    if not re.fullmatch(r"[0-9a-f]{40}", record.get("tested_commit", "")):
        raise ValueError("Acceptance tested_commit must be a full commit SHA")
    for key in CHECKS:
        item = record.get("checks", {}).get(key, {})
        evidence = item.get("evidence")
        if item.get("status") not in ("passed", "partial", "not_run") or not isinstance(evidence, str) or not evidence.strip():
            raise ValueError(f"Missing acceptance evidence or disclosed limit: {key}")
        if record["status"] == "passed" and item["status"] != "passed":
            raise ValueError(f"Passed acceptance requires actual completion: {key}")
    if record["status"] == "partial":
        if int(version.split(".")[0]) >= 1:
            raise ValueError("Stable 1.0.0+ requires real critical acceptance")
        limits = record.get("limitations")
        if not isinstance(limits, list) or not limits or not all(isinstance(x, str) and x.strip() for x in limits):
            raise ValueError("Early partial release must disclose limitations")


def validate_repository(root, ref):
    root = Path(root)
    config = properties(root)
    metadata = validate_version(config["pluginVersion"], config["pluginName"], ref)
    descriptor = ET.parse(root / "src/main/resources/META-INF/plugin.xml").getroot()
    if descriptor.find("version") is not None:
        raise ValueError("Source plugin.xml must receive version from Gradle, not duplicate it")
    if descriptor.findtext("name") != PRODUCT or descriptor.findtext("id") != "io.github.ataraxyadong.devreader":
        raise ValueError("Plugin descriptor identity changed")
    record_path = root / "release-acceptance" / f"{metadata['version']}.json"
    record = json.loads(record_path.read_text()) if record_path.exists() else None
    if record is not None:
        validate_acceptance(record, metadata["version"])
    metadata["prerelease"] = "false" if record and record["status"] == "passed" else "true"
    if ref.startswith("refs/tags/"):
        if record is None:
            raise ValueError("Tag release requires its acceptance/limitations record")
        subprocess.run(["git", "merge-base", "--is-ancestor", "HEAD", "origin/master"], cwd=root, check=True)
        tested = record["tested_commit"]
        subprocess.run(["git", "merge-base", "--is-ancestor", tested, "HEAD"], cwd=root, check=True)
        if record["status"] == "passed":
            subprocess.run(["git", "diff", "--exit-code", tested, "HEAD", "--", "src/main", "build.gradle",
                            "settings.gradle.kts", "gradle/libs.versions.toml", "gradle/wrapper"], cwd=root, check=True)
            old = subprocess.check_output(["git", "show", f"{tested}:gradle.properties"], cwd=root, text=True)
            current = (root / "gradle.properties").read_text()
            without_version = lambda text: re.sub(r"(?m)^pluginVersion\s*=.*$", "", text)
            if without_version(old) != without_version(current):
                raise ValueError("IDE/build properties changed after acceptance")
    return metadata
