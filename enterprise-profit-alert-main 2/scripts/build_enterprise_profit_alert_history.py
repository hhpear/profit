#!/usr/bin/env python3
"""Normalize frozen enterprise-profit source receipts for v2 evaluation."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

try:
    from .build_enterprise_profit_alert import build_domestic_calendar_history, merge_calendar_history
except ImportError:
    from build_enterprise_profit_alert import build_domestic_calendar_history, merge_calendar_history


def merge_international_seeds(documents: list[dict]) -> dict:
    rows: dict[str, dict] = {}
    starts, ends = [], []
    for document in documents:
        if document.get("status") != "SUCCESS":
            raise RuntimeError("international seed history is not SUCCESS")
        coverage = document.get("coverage") or {}
        starts.append(str(coverage["start"])); ends.append(str(coverage["end"]))
        for source in document.get("rows") or []:
            code = str(source.get("enterprise_code") or "").strip()
            if not code:
                raise RuntimeError("international seed enterprise identity is incomplete")
            row = rows.setdefault(code, {"business_type": "国际", "enterprise_code": code,
                "enterprise_name": source["enterprise_name"], "daily": {}})
            if row["enterprise_name"] != source["enterprise_name"]:
                raise RuntimeError(f"international seed enterprise name changed: {code}")
            for day, values in (source.get("daily") or {}).items():
                if day in row["daily"] and row["daily"][day] != values:
                    raise RuntimeError(f"international seed overlap mismatch: {code}/{day}")
                row["daily"][day] = values
    return {"status": "SUCCESS", "entity_semantics": "INTERNATIONAL_ENTERPRISE",
            "coverage": {"start": min(starts), "end": max(ends)},
            "rows": [rows[key] for key in sorted(rows)]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--domestic-source", type=Path, required=True)
    parser.add_argument("--current-day", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--domestic-current", type=Path)
    parser.add_argument("--international-seed", type=Path, action="append", default=[])
    parser.add_argument("--international-receipt", type=Path, action="append", default=[])
    parser.add_argument("--operating-rules", type=Path)
    args = parser.parse_args()
    source = json.loads(args.domestic_source.read_text(encoding="utf-8"))
    domestic = build_domestic_calendar_history(source, args.current_day)
    unified = any((args.domestic_current, args.international_seed,
                   args.international_receipt, args.operating_rules))
    if unified:
        required = (args.domestic_current, args.operating_rules)
        if not all(required) or not args.international_receipt:
            raise RuntimeError("unified history requires all domestic/international inputs")
        result = merge_calendar_history(
            domestic, json.loads(args.domestic_current.read_text(encoding="utf-8")),
            (merge_international_seeds([json.loads(path.read_text(encoding="utf-8"))
                                        for path in args.international_seed])
             if args.international_seed else
             {"status": "SUCCESS", "entity_semantics": "INTERNATIONAL_ENTERPRISE",
              "coverage": {}, "rows": []}),
            [(path, json.loads(path.read_text(encoding="utf-8")))
             for path in args.international_receipt],
            json.loads(args.operating_rules.read_text(encoding="utf-8")), args.current_day)
        result["input_sources"] = [
            {"role": role, "path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            for role, path in ([
                ("domestic_history", args.domestic_source),
                ("domestic_current", args.domestic_current),
                ("operating_rules", args.operating_rules),
            ] + [("international_seed", path) for path in args.international_seed]
              + [("international_receipt", path) for path in args.international_receipt])
        ]
    else:
        result = domestic
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "scope": result["scope"],
                      "rows": len(result["rows"]), "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
