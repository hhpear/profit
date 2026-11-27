#!/usr/bin/env python3
"""Build the v2 calendar-aware enterprise profit alert from frozen history."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from build_enterprise_profit_alert import build_calendar_alert


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", type=Path, required=True)
    parser.add_argument("--rules", type=Path, required=True)
    parser.add_argument("--current-day", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = build_calendar_alert(read(args.history), args.current_day, read(args.rules))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "alert_count": result["alert_count"],
                      "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
