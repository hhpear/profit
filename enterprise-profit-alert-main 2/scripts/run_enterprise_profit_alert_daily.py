#!/usr/bin/env python3
"""Run the governed daily enterprise-profit alert from live or frozen sources."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

try:
    from .build_enterprise_profit_alert import (
        build_calendar_alert, build_domestic_calendar_history, merge_calendar_history)
    from .build_enterprise_profit_alert_card import build as build_card
    from .validate_enterprise_profit_alert_governance import (
        ROOT, read, sha, validate_active, validate_candidate, validate_output)
except ImportError:
    from build_enterprise_profit_alert import (
        build_calendar_alert, build_domestic_calendar_history, merge_calendar_history)
    from build_enterprise_profit_alert_card import build as build_card
    from validate_enterprise_profit_alert_governance import (
        ROOT, read, sha, validate_active, validate_candidate, validate_output)


DOMESTIC_QUERY = ROOT / "scripts/query_enterprise_profit_alert_domestic.py"
DOMESTIC_DETAIL_QUERY = DOMESTIC_QUERY


def delivery_decision(alert: dict[str, Any]) -> dict[str, Any]:
    if alert.get("status") != "SUCCESS":
        raise RuntimeError("alert result is not SUCCESS")
    if int(alert.get("alert_count") or 0) == 0:
        return {"status": "SUCCESS", "delivery_status": "NO_SEND_NO_BUSINESS_ALERT",
                "message_count": 0}
    return {"status": "READY_TO_SEND", "delivery_status": "BUSINESS_ALERT_REQUIRED",
            "message_count": None}


def dry_run_decision(alert: dict[str, Any]) -> dict[str, Any]:
    if alert.get("status") != "SUCCESS":
        raise RuntimeError("alert result is not SUCCESS")
    return {
        "status": "SUCCESS",
        "delivery_status": "DRY_RUN_NOT_SENT",
        "message_count": 0,
        "send_attempted": False,
        "would_send": int(alert.get("alert_count") or 0) > 0,
    }


def run(command: list[str]) -> None:
    completed = subprocess.run(command, cwd=ROOT, text=True)
    if completed.returncode:
        raise RuntimeError(f"daily runner command failed: {' '.join(command[:3])}")


def active_operating_rules() -> Path:
    pointer = read(ROOT / "config/operating-dashboard-rules/active.json")
    rules_path = ROOT / pointer["ruleset_path"]
    if sha(rules_path) != pointer.get("ruleset_sha256"):
        raise RuntimeError("operating rules pointer/hash mismatch")
    return rules_path


def international_seed_from_history(history: dict[str, Any]) -> dict[str, Any]:
    rows = [item for item in history.get("rows") or [] if item.get("business_type") == "国际"]
    return {"status": "SUCCESS", "entity_semantics": "INTERNATIONAL_ENTERPRISE",
            "coverage": dict(history.get("coverage") or {}), "rows": rows}


def validate_recovery_receipt_days(previous_history: dict[str, Any], current_day: str,
                                   receipts: list[tuple[Path, dict[str, Any]]]) -> list[str]:
    if previous_history.get("status") != "SUCCESS":
        raise RuntimeError("previous history is not SUCCESS")
    previous_end = date.fromisoformat(str((previous_history.get("coverage") or {}).get("end") or ""))
    current = date.fromisoformat(current_day)
    if previous_end >= current:
        raise RuntimeError("previous history must end before current day")
    expected = []
    day = previous_end + timedelta(days=1)
    while day <= current:
        expected.append(day.isoformat())
        day += timedelta(days=1)
    actual = []
    for _, document in receipts:
        actual.append(datetime.fromisoformat(str(document.get("start_time") or "")).date().isoformat())
    if actual != expected:
        raise RuntimeError(
            f"Pigeon recovery receipt days are not continuous: expected={expected}, actual={actual}")
    return expected


def freeze_input(role: str, path: Path) -> dict[str, str]:
    return {"role": role, "path": str(path), "sha256": sha(path)}


def acquire_live(current_day: str, output_dir: Path, previous_history: Path,
                 international_receipts: list[Path], candidate_rules: Path | None = None) -> dict[str, Any]:
    source_dir = output_dir / "source-receipts"
    source_dir.mkdir(parents=True, exist_ok=True)
    start_day = (date.fromisoformat(current_day) - timedelta(days=60)).isoformat()
    domestic_history = source_dir / f"domestic-customer-{start_day}-{current_day}.json"
    domestic_current = source_dir / f"domestic-detail-{current_day}.json"
    operating_rules = active_operating_rules()
    candidate_args = ["--candidate-rules", str(candidate_rules)] if candidate_rules else []
    run([sys.executable, str(DOMESTIC_QUERY), "--start-date", start_day,
         "--end-date", current_day, "--output", str(domestic_history), *candidate_args])
    run([sys.executable, str(DOMESTIC_DETAIL_QUERY), "--start-date", current_day,
         "--end-date", current_day, "--current-detail", "--output", str(domestic_current), *candidate_args])
    previous = read(previous_history)
    seed = international_seed_from_history(previous)
    receipt_pairs = [(path, read(path)) for path in international_receipts]
    validate_recovery_receipt_days(previous, current_day, receipt_pairs)
    result = merge_calendar_history(
        build_domestic_calendar_history(read(domestic_history), current_day), read(domestic_current),
        seed, receipt_pairs, read(operating_rules), current_day)
    result["input_sources"] = [
        freeze_input("domestic_history", domestic_history),
        freeze_input("domestic_current", domestic_current),
        freeze_input("operating_rules", operating_rules),
        freeze_input("previous_history", previous_history),
        *[freeze_input("international_receipt", path) for path in international_receipts],
    ]
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--current-day", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--history", type=Path, help="Hash-verified frozen unified history for acceptance")
    parser.add_argument("--previous-history", type=Path)
    parser.add_argument("--international-receipt", type=Path, action="append", default=[])
    parser.add_argument("--acceptance-test", action="store_true")
    parser.add_argument("--dry-run", action="store_true",
                        help="Build and validate artifacts without invoking the sender")
    parser.add_argument("--candidate-rules", type=Path,
                        help="Customer-scope candidate; requires --dry-run")
    args = parser.parse_args()
    if args.dry_run and args.acceptance_test:
        raise RuntimeError("dry-run and acceptance-test are mutually exclusive")
    if args.candidate_rules and not args.dry_run:
        raise RuntimeError("candidate rules require dry-run; production requires active rules")

    pointer, rules_path, rules = (validate_candidate(args.candidate_rules) if args.candidate_rules
                                 else validate_active(ROOT))
    output_dir = args.output_dir.resolve()
    manifest_path = output_dir / "enterprise-profit-alert-run.json"
    if manifest_path.exists() and read(manifest_path).get("status") == "SUCCESS":
        existing = read(manifest_path)
        mode = "dry-run" if args.dry_run else "production"
        if (existing.get("current_day") != args.current_day
                or existing.get("ruleset_sha256") != pointer["ruleset_sha256"]
                or existing.get("execution_mode") != mode):
            raise RuntimeError("completed run date/rules/execution mode mismatch")
        validate_output(output_dir, rules_path, rules, delivery_mode=mode)
        print(json.dumps({"status": "ALREADY_COMPLETED", "output_dir": str(output_dir)}, ensure_ascii=False))
        return
    output_dir.mkdir(parents=True, exist_ok=True)
    if args.history:
        history = read(args.history.resolve())
        history["input_sources"] = history.get("input_sources") or [freeze_input("frozen_history", args.history.resolve())]
    else:
        if not args.previous_history or not args.international_receipt:
            raise RuntimeError("live run requires previous history and current Pigeon receipt")
        history = acquire_live(args.current_day, output_dir, args.previous_history.resolve(),
                               [path.resolve() for path in args.international_receipt],
                               rules_path if args.candidate_rules else None)
    history_path = output_dir / "enterprise-profit-calendar-history.json"
    alert_path = output_dir / "enterprise-profit-alert.json"
    card_path = output_dir / "enterprise-profit-alert-card.json"
    history_path.write_text(json.dumps(history, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    alert = build_calendar_alert(history, args.current_day, rules)
    alert_path.write_text(json.dumps(alert, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    card = build_card(alert, rules)
    card_path.write_text(json.dumps(card, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    execution_mode = "dry-run" if args.dry_run else "production"
    decision = dry_run_decision(alert) if args.dry_run else delivery_decision(alert)
    receipt_path = output_dir / f"enterprise-profit-alert-delivery-{execution_mode}.json"
    if args.dry_run:
        receipt_path.write_text(json.dumps({
            **decision, "schema_version": "1.0", "execution_mode": execution_mode,
            "ruleset_version": rules["version"],
            "ruleset_sha256": pointer["ruleset_sha256"],
            "current_day": args.current_day, "alert_count": alert["alert_count"],
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    elif decision["delivery_status"] == "NO_SEND_NO_BUSINESS_ALERT":
        receipt_path.write_text(json.dumps({**decision, "ruleset_version": rules["version"],
            "ruleset_sha256": pointer["ruleset_sha256"], "current_day": args.current_day},
            ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    elif args.acceptance_test:
        raise RuntimeError("business-alert acceptance must use the approved single-recipient sender flow")
    else:
        run([sys.executable, str(ROOT / "scripts/send_enterprise_profit_alert_card.py"),
             "--input-dir", str(output_dir)])
    validation = validate_output(output_dir, rules_path, rules, delivery_mode=execution_mode)
    manifest = {"schema_version": "1.0", "status": "SUCCESS", "current_day": args.current_day,
                "ruleset_version": rules["version"], "ruleset_sha256": pointer["ruleset_sha256"],
                "execution_mode": execution_mode, "output_dir": str(output_dir),
                "validation": validation}
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
