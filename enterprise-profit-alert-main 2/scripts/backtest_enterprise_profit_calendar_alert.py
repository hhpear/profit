#!/usr/bin/env python3
"""Backtest adjacent-day and calendar-aware alert volumes on frozen history."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path

from build_enterprise_profit_alert import evaluate_calendar_series


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", type=Path, required=True)
    parser.add_argument("--rules", type=Path, required=True)
    parser.add_argument("--start-day", required=True)
    parser.add_argument("--end-day", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    history = read(args.history)
    rules = read(args.rules)
    if history.get("status") != "SUCCESS":
        raise RuntimeError("backtest history is not SUCCESS")
    amount = Decimal(rules["thresholds"]["amount_exclusive"])
    rate = Decimal(rules["thresholds"]["rate_percent_exclusive"])
    rows = []
    current = date.fromisoformat(args.start_day)
    end = date.fromisoformat(args.end_day)
    while current <= end:
        day = current.isoformat()
        previous = (current - timedelta(days=1)).isoformat()
        counts = {"old_adjacent_alerts": 0, "new_business_alerts": 0,
                  "single_day_decline_watch": 0, "increase_observations": 0,
                  "unrated": 0, "evaluated_entities": 0}
        for entity in history.get("rows") or []:
            daily = entity.get("daily") or {}
            if day not in daily:
                continue
            counts["evaluated_entities"] += 1
            series = {key: Decimal(str(value.get("profit") or 0))
                      for key, value in daily.items()}
            evaluation = evaluate_calendar_series(series, day, rules)
            status = evaluation["status"]
            if status.startswith("ALERT_"):
                counts["new_business_alerts"] += 1
            elif status == "WATCH_SINGLE_DAY_DECLINE":
                counts["single_day_decline_watch"] += 1
            elif status == "OBSERVATION_INCREASE":
                counts["increase_observations"] += 1
            elif status.startswith("UNRATED_"):
                counts["unrated"] += 1
            if previous in daily:
                actual = series[day]
                comparison = series[previous]
                change = actual - comparison
                if (comparison != 0 and abs(change) > amount
                        and abs(change / abs(comparison) * Decimal(100)) > rate):
                    counts["old_adjacent_alerts"] += 1
        rows.append({"day": day, "weekday": current.weekday(), **counts})
        current += timedelta(days=1)
    totals = {key: sum(row[key] for row in rows)
              for key in rows[0] if key not in {"day", "weekday"}}
    result = {
        "schema_version": "1.0", "status": "SUCCESS",
        "scope": history.get("scope"), "business_labels": "NOT_AVAILABLE",
        "period": {"start": args.start_day, "end": args.end_day},
        "ruleset_version": rules["version"], "rows": rows, "totals": totals,
        "sources": {"history": {"path": str(args.history), "sha256": digest(args.history)},
                    "rules": {"path": str(args.rules), "sha256": digest(args.rules)}},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "totals": totals,
                      "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
