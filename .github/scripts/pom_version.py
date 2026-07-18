#!/usr/bin/env python3
"""Read, bump and rewrite the Maven project's own <version> (never the parent's).

Used by .github/workflows/docker-publish.yml to give every master build a unique
image tag. Kept as a file rather than inlined in the workflow so it can be run and
tested locally:

    python3 .github/scripts/pom_version.py read pom.xml
    python3 .github/scripts/pom_version.py bump 1.5.0
    python3 .github/scripts/pom_version.py set pom.xml 1.5.1
"""
import re
import sys

# The project version is the first <version> following the closing </parent> tag.
# Rewriting via regex rather than parsing and re-serialising the XML keeps the
# file byte-for-byte identical apart from the version itself, so the commit the
# workflow pushes back is a clean one-line diff.
PATTERN = re.compile(r"(</parent>.*?<version>)([^<]+)(</version>)", re.DOTALL)


def read(text):
    match = PATTERN.search(text)
    if not match:
        raise SystemExit("could not locate the project <version> in pom.xml")
    return match.group(2).strip()


def write(text, new_version):
    if not PATTERN.search(text):
        raise SystemExit("could not locate the project <version> in pom.xml")
    return PATTERN.sub(lambda m: m.group(1) + new_version + m.group(3), text, count=1)


def bump_patch(version):
    parts = version.split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        raise SystemExit(
            f"version {version!r} is not MAJOR.MINOR.PATCH, so it cannot be "
            "auto-bumped; set a numeric version in pom.xml"
        )
    parts[2] = str(int(parts[2]) + 1)
    return ".".join(parts)


def main():
    command = sys.argv[1]
    if command == "read":
        with open(sys.argv[2]) as handle:
            print(read(handle.read()))
    elif command == "read-stdin":
        print(read(sys.stdin.read()))
    elif command == "bump":
        print(bump_patch(sys.argv[2]))
    elif command == "set":
        path, new_version = sys.argv[2], sys.argv[3]
        with open(path) as handle:
            original = handle.read()
        updated = write(original, new_version)
        with open(path, "w") as handle:
            handle.write(updated)
    else:
        raise SystemExit(f"unknown command {command!r}")


if __name__ == "__main__":
    main()
