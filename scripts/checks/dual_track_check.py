#!/usr/bin/env python3
"""dual_track_check.py -- dual-track convergence gate.

Fails when a Kotlin source file breaks the M3/Miuix track contract:

  * shared file calls a bare Material 3 / Material 2 component leaf   (DEBT)
  * *Miuix.kt file calls a bare Material 3 component leaf             (SEVERE)
  * shared file imports a miuix native component                      (WEIRD-SHARED)
  * shared file reads MaterialTheme.colorScheme / .typography         (THEME-API)

`MaterialTheme.shapes` in a shared file is reported as ADVISORY only: miuix
0.9.4 exposes no shape tokens, so there is no dual-track counterpart to move to.

Track rules:
  *Material.kt / *Expressive.kt -> M track (M3 is the intended half)
  *Miuix.kt                     -> X track (miuix is the intended half)
  everything else               -> shared (must go through the ui-kit)

Usage: dual_track_check.py [--root DIR] [--quiet]
Exit: 0 = clean, 1 = findings, 2 = usage error.
"""
import argparse
import os
import re
import sys

TRACK_M_SUFFIXES = ("Material.kt", "Expressive.kt")

LEAVES = [
    "Text", "IconButton", "Button", "TextButton", "OutlinedButton", "FilledTonalButton",
    "ElevatedButton", "AlertDialog", "BasicAlertDialog", "Switch", "Checkbox",
    "RadioButton", "Slider", "RangeSlider", "CircularProgressIndicator",
    "LinearProgressIndicator", "Surface", "HorizontalDivider", "VerticalDivider",
    "Divider", "Card", "OutlinedCard", "ElevatedCard", "ListItem", "SnackbarHost",
    "NavigationBar", "FloatingActionButton", "ExtendedFloatingActionButton", "Icon",
    "FilterChip", "AssistChip", "InputChip", "SuggestionChip", "DropdownMenu",
    "DropdownMenuItem", "SegmentedButton", "Badge", "TextField", "OutlinedTextField",
    "BasicTextField", "SecureTextField", "ExposedDropdownMenuBox", "ExposedDropdownMenu",
    "Tab", "TabRow", "Checkbox", "TopAppBar", "Scaffold", "ModalBottomSheet",
    "BottomSheetScaffold", "NavigationRail", "Card",
]
LEAF_RE = {c: re.compile(r"(?<![A-Za-z0-9_.])" + c + r"\s*\(") for c in sorted(set(LEAVES))}

SKIP_DIRS = {"build", ".git", ".gradle", ".idea", "magisk-ui-kit", "magisk-xposed-kit",
             "build-logic", "node_modules"}
SUBMODULE_HINT = ("smscode/core", "smscode/rules", "smscode-core")


def strip_strings_comments(src):
    """Blank out string/char/comment contents, preserving length and newlines."""
    out = list(src)
    i, n = 0, len(src)
    state = None
    depth = 0
    while i < n:
        ch = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if state is None:
            if ch == "/" and nxt == "/":
                state = "line"
                out[i] = " "
                i += 1
                continue
            if ch == "/" and nxt == "*":
                state = "block"
                depth = 1
                out[i] = " "
                i += 1
                continue
            if src.startswith('"""', i):
                state = "raw"
                out[i] = " "
                i += 3
                continue
            if ch == '"':
                state = "str"
                out[i] = " "
                i += 1
                continue
            if ch == "'":
                state = "char"
                out[i] = " "
                i += 1
                continue
            i += 1
            continue
        if state == "line":
            if ch == "\n":
                state = None
            else:
                out[i] = " "
            i += 1
            continue
        if state == "block":
            if ch == "*" and nxt == "/":
                depth -= 1
                out[i] = " "
                if depth == 0:
                    state = None
                i += 1
                continue
            if ch == "/" and nxt == "*":
                depth += 1
            out[i] = " " if ch != "\n" else "\n"
            i += 1
            continue
        if state == "raw":
            if src.startswith('"""', i):
                state = None
                out[i] = " "
                i += 3
                continue
            out[i] = " " if ch != "\n" else "\n"
            i += 1
            continue
        if state == "str":
            if ch == "\\":
                out[i] = " "
                if i + 1 < n:
                    out[i + 1] = " "
                i += 2
                continue
            if ch == '"':
                state = None
                out[i] = " "
                i += 1
                continue
            out[i] = " " if ch != "\n" else "\n"
            i += 1
            continue
        if state == "char":
            if ch == "\\":
                i += 2
                continue
            if ch == "'":
                state = None
            out[i] = " " if ch != "\n" else "\n"
            i += 1
            continue
    return "".join(out)


IMPORT_RE = re.compile(r"^import\s+([A-Za-z_][A-Za-z0-9_.]*?)(?:\.(\*))?\s*(?:as\s+([A-Za-z0-9_]+))?\s*$", re.M)


def parse_imports(src):
    """-> (exact: local_name -> fqn, wildcards: [pkg])"""
    exact, wild = {}, []
    for m in IMPORT_RE.finditer(src):
        fqn, star, alias = m.group(1), m.group(2), m.group(3)
        if star:
            wild.append(fqn)
            continue
        exact[alias or fqn.rsplit(".", 1)[1]] = fqn
    return exact, wild


def resolve(name, exact, wild):
    if name in exact:
        f = exact[name]
        if f.startswith("androidx.compose.material3."):
            return "M3"
        if f.startswith("androidx.compose.material."):
            return "M2"
        if f.startswith("top.yukonga."):
            return "MIUIX"
        if f.startswith("io.github.magisk317."):
            return "KIT"
        return None
    fams = set()
    for w in wild:
        if w == "androidx.compose.material3" or w.startswith("androidx.compose.material3."):
            fams.add("M3")
        elif w == "androidx.compose.material" or w.startswith("androidx.compose.material."):
            fams.add("M2")
        elif w.startswith("top.yukonga."):
            fams.add("MIUIX")
        elif w.startswith("io.github.magisk317."):
            fams.add("KIT")
    debt = fams & {"M3", "M2"}
    if len(fams) == 1 and debt:
        return "M3" if "M3" in fams else "M2"
    if len(fams) > 1 and debt:
        return "AMBIG"
    return None


def track_of(path):
    base = os.path.basename(path)
    if base.endswith("Miuix.kt"):
        return "X"
    if base.endswith(TRACK_M_SUFFIXES):
        return "M"
    return "shared"


def walk(root):
    for dirpath, dirnames, filenames in os.walk(root):
        rel = os.path.relpath(dirpath, root).replace(os.sep, "/")
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if rel.startswith(SUBMODULE_HINT) or "/" + rel + "/" in ("/smscode/core/",):
            continue
        if re.search(r"/src/(test|androidTest)(/|$)", "/" + rel):
            continue
        for f in filenames:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()
    root = os.path.abspath(args.root)

    findings = []
    advisory = []

    for fp in sorted(walk(root)):
        rel = os.path.relpath(fp, root).replace(os.sep, "/")
        if rel.startswith(SUBMODULE_HINT):
            continue
        track = track_of(fp)
        try:
            raw = open(fp, encoding="utf-8").read()
        except (OSError, UnicodeDecodeError):
            continue
        src = strip_strings_comments(raw)
        exact, wild = parse_imports(src)

        for name, rx in LEAF_RE.items():
            kind = resolve(name, exact, wild)
            if kind not in ("M3", "M2", "MIUIX", "AMBIG"):
                continue
            for m in rx.finditer(src):
                ln = src[:m.start()].count("\n") + 1
                if track == "M":
                    if kind == "MIUIX":
                        findings.append(("M-track uses miuix native", rel, ln, name))
                elif track == "X":
                    if kind in ("M3", "M2", "AMBIG"):
                        findings.append(("Miuix-track uses M3", rel, ln, name))
                else:
                    if kind in ("M3", "M2", "AMBIG"):
                        findings.append(("shared uses M3 component", rel, ln, name))
                    elif kind == "MIUIX":
                        findings.append(("shared uses miuix native", rel, ln, name))

        if track == "shared":
            for m in re.finditer(r"MaterialTheme\.(colorScheme|typography)\.", src):
                ln = src[:m.start()].count("\n") + 1
                findings.append(("shared uses M3 theme API (%s)" % m.group(1), rel, ln, m.group(0)))
            for m in re.finditer(r"MaterialTheme\.shapes\.", src):
                ln = src[:m.start()].count("\n") + 1
                advisory.append((rel, ln))

    if not args.quiet:
        print("== dual-track check: %s ==" % root)
        for kind, rel, ln, name in findings:
            print("FAIL  %-34s %s:%d  %s" % (kind, rel, ln, name))
        for rel, ln in advisory:
            print("ADV   MaterialTheme.shapes (no miuix counterpart)  %s:%d" % (rel, ln))
    print("SUMMARY fail=%d advisory=%d" % (len(findings), len(advisory)))
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
