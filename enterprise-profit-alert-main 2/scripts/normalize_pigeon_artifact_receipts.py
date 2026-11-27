#!/usr/bin/env python3
"""Normalize atomically persisted billing_mcp envelopes into governed payloads."""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path


def atomic_write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def payload(document: dict) -> dict:
    if document.get("truncated") is False and isinstance(document.get("rows"), list):
        return document
    content = document.get("body", {}).get("content", document)
    if content.get("isError") is True:
        raise RuntimeError("persisted billing_mcp envelope reports an error")
    value = content.get("structuredContent")
    if not isinstance(value, dict):
        blocks = content.get("content") or []
        if not blocks or not isinstance(blocks[0].get("text"), str):
            raise RuntimeError("persisted billing_mcp envelope has no payload")
        value = json.loads(blocks[0]["text"])
    if value.get("truncated") is not False:
        raise RuntimeError("persisted billing_mcp payload is truncated")
    return value


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, required=True)
    args = parser.parse_args()
    count = 0
    for source in sorted(args.input_dir.glob("*.envelope.json")):
        target = Path(str(source).removesuffix(".envelope.json") + ".json")
        atomic_write(target, payload(json.loads(source.read_text(encoding="utf-8"))))
        source.unlink()
        count += 1
    print(json.dumps({"status": "SUCCESS", "normalized": count}, ensure_ascii=False))


if __name__ == "__main__":
    main()
