"""Publish a successful prerequisite job's artifact; never overwrite an existing Release."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from version_policy import validate_repository


def render_notes(root, metadata, env):
    root = Path(root)
    record = json.loads((root / "release-acceptance" / f"{metadata['version']}.json").read_text())
    template = (root / ".github/release-notes-template.md").read_text()
    body = template.format(
        zip_name=metadata["zip_name"],
        ci_url=f"https://github.com/{env['GITHUB_REPOSITORY']}/actions/runs/{env['GITHUB_RUN_ID']}",
        acceptance_url=f"https://github.com/{env['GITHUB_REPOSITORY']}/blob/v{metadata['version']}/release-acceptance/{metadata['version']}.json",
        known_limits="\n".join("- " + item for item in record.get("limitations", [])) or "- 关键验收记录已完成；使用前请核对 IDE 兼容范围。",
    )
    headings = [line for line in body.splitlines() if line.startswith("#")]
    if headings != ["## 功能", "## 修复", "## 安装", "## 已知限制"]:
        raise ValueError("Release body must use the four public-facing sections, without a repeated title")
    return body


def publish(root, env, run=subprocess.run):
    if env.get("GITHUB_EVENT_NAME") != "push" or not env.get("GITHUB_REF", "").startswith("refs/tags/v"):
        raise ValueError("Publication is allowed only for a pushed version tag")
    metadata = validate_repository(root, env["GITHUB_REF"])
    tag = "v" + metadata["version"]
    repository = env["GITHUB_REPOSITORY"]
    artifact = Path(root) / "release-artifact"
    package = artifact / metadata["zip_name"]
    checksum = artifact / "SHA256SUMS.txt"
    if {p.name for p in artifact.iterdir()} != {package.name, checksum.name}:
        raise ValueError("Release artifact must contain only the installation ZIP and checksum")
    actual = hashlib.sha256(package.read_bytes()).hexdigest()
    if checksum.read_text().strip() != f"{actual}  {package.name}":
        raise ValueError("CI artifact checksum does not match")
    # Read first: a prior partial or successful attempt must be inspected, never clobbered.
    prior = run(["gh", "api", f"repos/{repository}/releases/tags/{tag}"], capture_output=True, text=True)
    if prior.returncode == 0:
        existing = json.loads(prior.stdout)
        raise ValueError(f"Release already exists; inspect before retry: {existing.get('html_url', tag)}")
    if "HTTP 404" not in prior.stderr:
        raise RuntimeError(f"Release lookup failed; outcome requires investigation: {prior.stderr}")
    notes = Path(root) / "release-notes-ci.md"
    notes.write_text(render_notes(root, metadata, env))
    command = ["gh", "release", "create", tag, str(package), str(checksum), "--repo", repository,
               "--verify-tag", "--title", f"dev-reader {metadata['version']}", "--notes-file", str(notes)]
    if metadata["prerelease"] == "true":
        command.append("--prerelease")
    # A failed or uncertain attempt is not retried here. Inspect remote state first.
    run(command, check=True)


if __name__ == "__main__":
    publish(Path(__file__).resolve().parents[2], os.environ)
