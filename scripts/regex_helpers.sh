#!/usr/bin/env bash

regex_quiet() {
  if command -v rg >/dev/null 2>&1; then
    rg -q "$@"
    return $?
  fi
  regex_python quiet "$@"
}

regex_lines() {
  if command -v rg >/dev/null 2>&1; then
    rg -n "$@"
    return $?
  fi
  regex_python lines "$@"
}

regex_matches() {
  if command -v rg >/dev/null 2>&1; then
    rg -o "$@"
    return $?
  fi
  regex_python matches "$@"
}

regex_python() {
  local mode="$1"
  local pattern="$2"
  shift 2

  if ! command -v python3 >/dev/null 2>&1; then
    echo "regex helper requires either rg or python3" >&2
    return 127
  fi

  python3 - "$mode" "$pattern" "$@" <<'PY'
import os
import re
import sys

mode = sys.argv[1]
pattern = sys.argv[2]
roots = sys.argv[3:] or ["."]
skip_dirs = {".git", ".gradle", ".idea", "build", "node_modules"}

try:
    regex = re.compile(pattern)
except re.error as exc:
    print(f"invalid regex {pattern!r}: {exc}", file=sys.stderr)
    sys.exit(2)

def iter_files(root):
    if os.path.isfile(root):
        yield root
        return
    if not os.path.isdir(root):
        return
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [name for name in dirnames if name not in skip_dirs]
        for filename in filenames:
            yield os.path.join(dirpath, filename)

found = False
for root in roots:
    for path in iter_files(root):
        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as handle:
                for line_number, raw_line in enumerate(handle, 1):
                    line = raw_line.rstrip("\n")
                    matches = list(regex.finditer(line))
                    if not matches:
                        continue
                    found = True
                    if mode == "quiet":
                        sys.exit(0)
                    if mode == "lines":
                        print(f"{path}:{line_number}:{line}")
                    elif mode == "matches":
                        for match in matches:
                            print(match.group(0))
                    else:
                        print(f"unknown regex mode: {mode}", file=sys.stderr)
                        sys.exit(2)
        except OSError:
            continue

sys.exit(0 if found else 1)
PY
}
