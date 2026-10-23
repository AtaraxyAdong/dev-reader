"""Publish a successful prerequisite job's artifact; never overwrite an existing Release."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from version_policy import validate_repository


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
    run_id = env["GITHUB_RUN_ID"]
    notes = Path(root) / "release-notes-ci.md"
    record = json.loads((Path(root) / "release-acceptance" / f"{metadata['version']}.json").read_text())
    channel = "Early version; real critical acceptance is incomplete." if metadata["prerelease"] == "true" else "Critical acceptance is recorded; see the committed acceptance report."
    limits = "\n".join("- " + item for item in record.get("limitations", []))
    notes.write_text(f"{channel}\n\nInstall `{package.name}` through Settings > Plugins > Install Plugin from Disk.\n\n"
                     f"CI build: https://github.com/{repository}/actions/runs/{run_id}\n"
                     f"Commit: `{env['GITHUB_SHA']}`\nSHA-256: `{actual}`\n\n"
                     f"Policy: https://github.com/{repository}/blob/{tag}/VERSIONING.md\n"
                     f"Acceptance: https://github.com/{repository}/blob/{tag}/release-acceptance/{metadata['version']}.json\n\n{limits}\n")
    command = ["gh", "release", "create", tag, str(package), str(checksum), "--repo", repository,
               "--verify-tag", "--title", f"dev-reader {metadata['version']}", "--notes-file", str(notes)]
    if metadata["prerelease"] == "true":
        command.append("--prerelease")
    # A failed or uncertain attempt is not retried here. Inspect remote state first.
    run(command, check=True)


if __name__ == "__main__":
    publish(Path(__file__).resolve().parents[2], os.environ)
