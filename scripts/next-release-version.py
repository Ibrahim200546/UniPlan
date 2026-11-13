"""Choose the Android versionName for a release using the last published manifest."""
import json
import re
import sys
from pathlib import Path


def version_parts(value):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*)){2}", value):
        raise ValueError(f"Expected major.minor.patch, got {value!r}")
    return tuple(map(int, value.split(".")))


def next_version(minimum, previous, code):
    floor = version_parts(minimum)
    major, minor, patch = version_parts(previous["versionName"])
    if code < previous["versionCode"]:
        raise ValueError("A newer Android build is already published")
    if code == previous["versionCode"]:
        # A rerun of an already published workflow must keep its version.
        return previous["versionName"]
    return ".".join(map(str, max(floor, (major, minor, patch + 1))))


if __name__ == "__main__":
    minimum = Path("config/version.txt").read_text().strip()
    previous = json.loads(Path(sys.argv[1]).read_text())
    print(next_version(minimum, previous, int(sys.argv[2])))
