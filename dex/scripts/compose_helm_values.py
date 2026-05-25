#!/usr/bin/env python3
# Compose Dex per-backend Helm overlays into a single connectors values file.
#
# Helm's overlay merge replaces YAML lists wholesale, so layering
# values-tls.yaml + values-google.yaml + values-microsoft.yaml via `-f` would
# silently drop all but the last connector. This script concatenates the
# `config.connectors` entries from the inputs into one merged file that
# helm_dex.sh then passes to helm in place of the per-backend overlays.
#
# Stdlib-only (no PyYAML) to match dex/scripts/render_config.py. Each input
# file MUST be shaped:
#
#   config:
#     connectors:
#       - <one or more list items>
#
# with no other keys under `config:`. Comments and blank lines are tolerated.
#
# Usage:
#   compose_helm_values.py --out <merged.yaml> <overlay.yaml> [<overlay.yaml> ...]

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


CONFIG_LINE = re.compile(r"^config:\s*$")
CONNECTORS_LINE = re.compile(r"^  connectors:\s*$")
ID_LINE = re.compile(r"^\s+id:\s*(\S+)\s*$")


def _leading_spaces(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def extract_connectors_block(path: Path) -> str:
    """Return the lines under `  connectors:` from a per-backend overlay.

    Enforces the documented shape: top-level `config:` containing a single
    `  connectors:` list. Captures stop as soon as indentation returns to
    the `config:`-child level so sibling keys (e.g. `staticClients:`) never
    bleed into the merged connectors list.
    """
    lines = path.read_text().splitlines()
    captured: list[str] = []
    state = "outside"
    for line in lines:
        if state == "outside":
            if CONFIG_LINE.match(line):
                state = "in_config"
        elif state == "in_config":
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if CONNECTORS_LINE.match(line):
                state = "in_connectors"
            elif _leading_spaces(line) == 0:
                break
        else:
            if line.strip() and _leading_spaces(line) <= 2:
                break
            captured.append(line)

    if state == "outside":
        raise SystemExit(f"{path}: missing top-level 'config:' key.")
    if state == "in_config":
        raise SystemExit(f"{path}: missing 'connectors:' key under 'config:'.")
    while captured and not captured[0].strip():
        captured.pop(0)
    while captured and not captured[-1].strip():
        captured.pop()
    if not captured:
        raise SystemExit(f"{path}: 'connectors:' block is empty.")
    return "\n".join(captured) + "\n"


def collect_connector_ids(block: str) -> list[str]:
    return [m.group(1) for line in block.splitlines() if (m := ID_LINE.match(line))]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path, help="merged output path")
    parser.add_argument("overlays", nargs="+", type=Path, help="per-backend overlay YAML files")
    args = parser.parse_args(argv)

    blocks: list[str] = []
    seen_ids: dict[str, Path] = {}
    for overlay in args.overlays:
        block = extract_connectors_block(overlay)
        for cid in collect_connector_ids(block):
            if cid in seen_ids:
                raise SystemExit(
                    f"Duplicate connector id '{cid}' in {overlay} (also defined in {seen_ids[cid]})."
                )
            seen_ids[cid] = overlay
        blocks.append(block)

    merged = "config:\n  connectors:\n" + "".join(blocks)
    args.out.write_text(merged)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
