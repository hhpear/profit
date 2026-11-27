#!/usr/bin/env python3
"""Narrow DeerFlow MCP surface for governed enterprise-profit alert previews."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import uuid
from datetime import date
from pathlib import Path
from typing import Any

from mcp.server.fastmcp import FastMCP


ROOT = Path(__file__).resolve().parents[2]
REGISTRY_PATH = ROOT / "config/enterprise-profit-alert-agent/preview-snapshots.json"
OUTPUT_ROOT = ROOT / "outputs/deerflow-enterprise-profit-alert"
RUNNER_PATH = ROOT / "scripts/run_enterprise_profit_alert_daily.py"
EXECUTION_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9-]{0,63}$")

mcp = FastMCP(
    "enterprise-profit-alert",
    instructions=(
        "Execute only governed enterprise-profit alert previews. "
        "Every invocation is a strict dry-run and cannot send Feishu messages."
    ),
)


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def resolve_snapshot(current_day: str) -> tuple[Path, dict[str, Any]]:
    try:
        date.fromisoformat(current_day)
    except ValueError as exc:
        raise ValueError("current_day must use YYYY-MM-DD") from exc
    registry = read_json(REGISTRY_PATH)
    if registry.get("status") != "ACTIVE":
        raise RuntimeError("profit-alert preview snapshot registry is not ACTIVE")
    snapshot = (registry.get("snapshots") or {}).get(current_day)
    if not isinstance(snapshot, dict):
        raise ValueError(f"no governed complete-day snapshot is registered for {current_day}")
    history_path = (ROOT / str(snapshot.get("history_path") or "")).resolve()
    if not history_path.is_relative_to(ROOT.resolve()) or not history_path.is_file():
        raise RuntimeError("registered profit-alert history is missing or outside the project")
    if sha256(history_path) != snapshot.get("history_sha256"):
        raise RuntimeError("registered profit-alert history hash mismatch")
    history = read_json(history_path)
    if history.get("status") != "SUCCESS" or (history.get("coverage") or {}).get("end") != current_day:
        raise RuntimeError("registered history is not a successful exact-day snapshot")
    return history_path, snapshot


def resolve_output_dir(execution_id: str | None) -> tuple[str, Path]:
    resolved_id = execution_id or f"preview-{uuid.uuid4().hex[:16]}"
    if not EXECUTION_ID_PATTERN.fullmatch(resolved_id):
        raise ValueError("execution_id must be 1-64 ASCII letters, digits, or hyphens")
    output_dir = (OUTPUT_ROOT / resolved_id).resolve()
    if not output_dir.is_relative_to(OUTPUT_ROOT.resolve()):
        raise ValueError("execution output must stay inside the governed output root")
    return resolved_id, output_dir


def build_runner_command(current_day: str, history_path: Path, output_dir: Path) -> list[str]:
    return [
        sys.executable,
        str(RUNNER_PATH),
        "--current-day",
        current_day,
        "--history",
        str(history_path),
        "--output-dir",
        str(output_dir),
        "--dry-run",
    ]


def run_preview(current_day: str, execution_id: str | None = None) -> dict[str, Any]:
    history_path, snapshot = resolve_snapshot(current_day)
    resolved_id, output_dir = resolve_output_dir(execution_id)
    completed = subprocess.run(
        build_runner_command(current_day, history_path, output_dir),
        cwd=ROOT,
        text=True,
        capture_output=True,
        timeout=180,
    )
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout).strip()
        raise RuntimeError(f"profit-alert dry-run failed: {detail[-2000:]}")

    manifest = read_json(output_dir / "enterprise-profit-alert-run.json")
    alert = read_json(output_dir / "enterprise-profit-alert.json")
    receipt = read_json(output_dir / "enterprise-profit-alert-delivery-dry-run.json")
    if (manifest.get("status") != "SUCCESS"
            or manifest.get("execution_mode") != "dry-run"
            or manifest.get("current_day") != current_day):
        raise RuntimeError("profit-alert manifest did not prove a successful dry-run")
    if (receipt.get("delivery_status") != "DRY_RUN_NOT_SENT"
            or receipt.get("send_attempted") is not False
            or receipt.get("message_count") != 0):
        raise RuntimeError("profit-alert receipt did not prove zero message delivery")

    alerts = [
        {
            "enterprise_code": item.get("enterprise_code"),
            "enterprise_name": item.get("企业名称"),
            "business_type": item.get("business_type"),
            "status": item.get("status"),
            "actual_profit": item.get("实际利润"),
            "baseline_profit": item.get("基线利润"),
            "profit_deviation": item.get("利润偏差额"),
            "profit_deviation_rate": item.get("利润偏差率"),
        }
        for item in (alert.get("alerts") or [])
    ]
    return {
        "status": "SUCCESS",
        "execution_id": resolved_id,
        "execution_mode": "dry-run",
        "current_day": current_day,
        "source_status": snapshot.get("source_status"),
        "history_sha256": snapshot.get("history_sha256"),
        "ruleset_version": manifest.get("ruleset_version"),
        "ruleset_sha256": manifest.get("ruleset_sha256"),
        "alert_count": alert.get("alert_count"),
        "observation_count": alert.get("observation_count"),
        "watch_count": alert.get("watch_count"),
        "unrated_count": alert.get("unrated_count"),
        "would_send": receipt.get("would_send"),
        "send_attempted": False,
        "message_count": 0,
        "alerts": alerts,
        "artifacts": {
            "manifest": str(output_dir / "enterprise-profit-alert-run.json"),
            "alert": str(output_dir / "enterprise-profit-alert.json"),
            "card": str(output_dir / "enterprise-profit-alert-card.json"),
            "delivery_receipt": str(output_dir / "enterprise-profit-alert-delivery-dry-run.json"),
        },
    }


@mcp.tool()
def run_enterprise_profit_alert_preview(
    current_day: str,
    execution_id: str | None = None,
) -> dict[str, Any]:
    """Run a governed complete-day enterprise-profit alert preview.

    This tool always executes in dry-run mode. It builds and validates the alert
    artifacts but never invokes the Feishu sender. current_day must have an exact
    hash-pinned snapshot in the active preview registry.
    """
    return run_preview(current_day, execution_id)


if __name__ == "__main__":
    mcp.run(transport="stdio")
