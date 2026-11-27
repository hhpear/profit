#!/usr/bin/env python3
"""Recover continuous Pigeon receipts through the approved LiteLLM MCP only."""
from __future__ import annotations

import argparse
import json
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

try:
    from .fetch_oldsms_enterprise_master_litellm import post
    from .normalize_pigeon_artifact_receipts import atomic_write
    from .build_enterprise_profit_alert import international_day
    from .run_enterprise_profit_alert_daily import active_operating_rules
    from .validate_enterprise_profit_alert_governance import validate_active, read, sha
except ImportError:
    from fetch_oldsms_enterprise_master_litellm import post
    from normalize_pigeon_artifact_receipts import atomic_write
    from build_enterprise_profit_alert import international_day
    from run_enterprise_profit_alert_daily import active_operating_rules
    from validate_enterprise_profit_alert_governance import validate_active, read, sha

TOOL = "yuexin_mcp_gateway-billing_mcp__query_daily_stats_summary"


class SourceGateError(RuntimeError):
    """A downstream source failure must not be retried as a wrapper failure."""


def unwrap(value):
    if isinstance(value, str):
        try:
            return unwrap(json.loads(value))
        except json.JSONDecodeError:
            return None
    if isinstance(value, dict):
        if value.get("isError") or value.get("error"):
            raise SourceGateError("MCP reports a downstream error")
        if isinstance(value.get("rows"), list) and "truncated" in value:
            return value
        for field in ("result", "structuredContent", "content", "data", "text"):
            found = unwrap(value.get(field))
            if found is not None:
                return found
    if isinstance(value, list):
        for child in value:
            found = unwrap(child)
            if found is not None:
                return found
    return None


def validate(document, day, operating):
    if not isinstance(document, dict):
        raise RuntimeError("MCP response has no daily receipt")
    tz = ZoneInfo("Asia/Shanghai")
    for field, expected in (("start_time", time(0, 0, 0)), ("end_time", time(23, 59, 59))):
        stamp = datetime.fromisoformat(str(document.get(field) or ""))
        stamp = stamp.replace(tzinfo=tz) if stamp.tzinfo is None else stamp.astimezone(tz)
        if stamp.date().isoformat() != day or stamp.time() != expected:
            raise RuntimeError("Pigeon natural-day boundary mismatch")
    international_day(document, day, operating)
    # Check all identities before exclusions, as well as the calculation gate.
    from decimal import Decimal
    for row in document["rows"]:
        income, cost = (Decimal(str(row[field])) for field in ("customer_total_amount", "channel_total_amount"))
        if not income.is_finite() or not cost.is_finite():
            raise RuntimeError("invalid Pigeon source amount")
        if (income or cost) and any(not str(row.get(field) or "").strip()
                                   for field in ("enterprise_id", "enterprise_name", "account_id")):
            raise RuntimeError("nonzero Pigeon amount missing enterprise/account identity")
        if cost and not str(row.get("send_channel_id") or "").strip():
            raise RuntimeError("nonzero Pigeon cost missing channel identity")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--previous-history", type=Path, required=True)
    p.add_argument("--current-day", required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    args = p.parse_args()
    validate_active()
    previous = read(args.previous_history)
    if previous.get("status") != "SUCCESS":
        raise RuntimeError("previous history is not SUCCESS")
    day = date.fromisoformat(previous["coverage"]["end"]) + timedelta(days=1)
    end = date.fromisoformat(args.current_day)
    if day > end:
        raise RuntimeError("previous history is not a recovery base")
    operating = read(active_operating_rules())
    directory = args.output_dir / "source-receipts"
    directory.mkdir(parents=True, exist_ok=True)
    key = read(Path.home() / ".codex/auth.json").get("OPENAI_API_KEY")
    if not key:
        raise RuntimeError("configured LiteLLM credential is unavailable")
    response, session = post(key, {"jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                   "clientInfo": {"name": "enterprise-profit-alert", "version": "1.0"}}})
    if "result" not in response:
        raise RuntimeError("LiteLLM initialize failed")
    post(key, {"jsonrpc": "2.0", "method": "notifications/initialized"}, session)
    names, cursor, request_id = [], None, 2
    while True:
        listed, _ = post(key, {"jsonrpc": "2.0", "id": request_id, "method": "tools/list",
            "params": {} if cursor is None else {"cursor": cursor}}, session)
        request_id += 1
        result = listed.get("result") or {}
        names.extend(item["name"] for item in result.get("tools") or [])
        cursor = result.get("nextCursor")
        if not cursor:
            break
    if TOOL not in names:
        raise RuntimeError("approved Pigeon tool is absent from LiteLLM tools/list")
    atomic_write(directory / "mcp-preflight.json", {"status": "SUCCESS",
        "endpoint": "https://flux-admin.yuexin.cn/mcp/", "tool": TOOL,
        "tool_count": len(names), "legacy_gateway_used": False})
    attempts = []
    ledger = directory / "pigeon-query-attempts.json"
    if ledger.exists():
        attempts = read(ledger)["attempts"]
    while day <= end:
        day_text = day.isoformat()
        target = directory / f"pigeon-daily-overall-{day_text}.json"
        if target.exists():
            validate(read(target), day_text, operating)
            day += timedelta(days=1)
            continue
        used = sum(item["day"] == day_text for item in attempts)
        for attempt in range(used + 1, 4):
            entry = {"day": day_text, "attempt": attempt, "status": "STARTED"}
            attempts.append(entry)
            atomic_write(ledger, {"attempts": attempts})
            try:
                raw, _ = post(key, {"jsonrpc": "2.0", "id": request_id, "method": "tools/call",
                    "params": {"name": TOOL, "arguments": {
                        "start_time": f"{day_text}T00:00:00", "end_time": f"{day_text}T23:59:59",
                        "source_platform": "Pigeon", "group_by": ["enterprise_id", "account_id", "send_channel_id"]}}}, session)
                request_id += 1
                atomic_write(directory / f"pigeon-{day_text}.attempt-{attempt}.raw.json", raw)
                document = unwrap(raw)
                if document is None:
                    raise RuntimeError("MCP wrapping has no daily payload")
            except SourceGateError:
                entry.update(status="SOURCE_GATE_FAILED")
                atomic_write(ledger, {"attempts": attempts})
                raise
            except Exception as exc:
                entry.update(status="FAILED", error_type=type(exc).__name__)
                atomic_write(ledger, {"attempts": attempts})
                if attempt == 3:
                    raise RuntimeError(f"Pigeon transport/wrapper failed after 3 attempts: {day_text}") from exc
                continue
            validate(document, day_text, operating)
            atomic_write(target, document)
            validate(read(target), day_text, operating)
            entry.update(status="SUCCESS", row_count=document["row_count"], sha256=sha(target))
            atomic_write(ledger, {"attempts": attempts})
            print(json.dumps(entry), flush=True)
            break
        else:
            raise RuntimeError(f"Pigeon attempt budget exhausted: {day_text}")
        day += timedelta(days=1)


if __name__ == "__main__":
    main()
