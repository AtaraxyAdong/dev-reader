"""Validate source version, tag and stable-release acceptance before building."""
import argparse
import os
from pathlib import Path
from version_policy import validate_repository

parser = argparse.ArgumentParser()
parser.add_argument("--ref", default=os.environ.get("GITHUB_REF", "refs/heads/master"))
args = parser.parse_args()
metadata = validate_repository(Path(__file__).resolve().parents[2], args.ref)
print("Validated version policy:", metadata)
if os.environ.get("GITHUB_OUTPUT"):
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        for key, value in metadata.items():
            output.write(f"{key}={value}\n")
