#!/usr/bin/env python3
"""Validate active enterprise-profit alert governance and optional run evidence."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from datetime import date
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]


def read(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def validate_active(root: Path = ROOT) -> tuple[dict[str, Any], Path, dict[str, Any]]:
    pointer_path = root / "config/enterprise-profit-alert-rules/active.json"
    pointer = read(pointer_path)
    require(pointer.get("status") == "ACTIVE", "active pointer is not ACTIVE")
    rules_path = root / str(pointer.get("ruleset_path") or "")
    require(rules_path.is_file(), "active ruleset is missing")
    require(sha(rules_path) == pointer.get("ruleset_sha256"), "active ruleset hash mismatch")
    rules = read(rules_path)
    require(rules.get("ruleset_id") == "enterprise-profit-alert", "active ruleset id mismatch")
    require(rules.get("version") == pointer.get("active_version"), "active ruleset version mismatch")
    require(rules.get("status") == "APPROVED", "active pointer must reference an approved immutable ruleset")
    require((rules.get("baseline") or {}).get("method") == "SAME_CALENDAR_CLASS_MEDIAN_MAD",
            "active baseline method mismatch")
    entity = rules.get("entity") or {}
    require(entity.get("department_dependency") == "FORBIDDEN",
            "income-attribution department dependency must be forbidden")
    require(entity.get("department_display") == "FORBIDDEN",
            "income-attribution department display must be forbidden")
    require("收入归属部门" not in ((rules.get("display") or {}).get("fields") or []),
            "income-attribution department must not be an alert display field")
    international = (rules.get("sources") or {}).get("international") or {}
    domestic = (rules.get("sources") or {}).get("domestic") or {}
    if "customer_types" in domestic:
        require(domestic["customer_types"] == ["Customer", "QuasiCustomer", "Agent", "CHANNEL"],
                "active domestic customer type scope mismatch")
    gateway = international.get("gateway") or {}
    require(gateway.get("transport") == "LITELLM_REMOTE_MCP",
            "international source must use LiteLLM remote MCP")
    require(gateway.get("endpoint") == "https://flux-admin.yuexin.cn/mcp/",
            "LiteLLM MCP endpoint mismatch")
    require(gateway.get("remote_tool") ==
            "yuexin_mcp_gateway-billing_mcp__query_daily_stats_summary",
            "LiteLLM MCP remote tool mismatch")
    require(gateway.get("legacy_oauth_gateway") == "FORBIDDEN",
            "legacy OAuth gateway must be forbidden")
    require("106.14.207.126:8000" in (gateway.get("forbidden_endpoints") or []),
            "retired legacy gateway is not explicitly forbidden")
    delivery = rules.get("delivery") or {}
    require(delivery.get("sender_name") == "业财告警助手", "configured sender name mismatch")
    require(bool(delivery.get("sender_app_id")), "configured sender app id is missing")
    require(delivery.get("send_when_no_anomaly") is False, "zero-alert production policy mismatch")
    document = (root / "docs/企业客户利润异常告警.md").read_text(encoding="utf-8")
    require(f"<!-- ACTIVE_RULESET_VERSION: {rules['version']} -->" in document,
            "canonical document version marker mismatch")
    require(f"<!-- ACTIVE_RULESET_SHA256: {sha(rules_path)} -->" in document,
            "canonical document hash marker mismatch")
    return pointer, rules_path, rules


def validate_candidate(path: Path) -> tuple[dict[str, Any], Path, dict[str, Any]]:
    """Validate the explicitly authorized customer-scope migration before activation."""
    pointer, _, active = validate_active()
    path = path.resolve()
    require(path.parent == (ROOT / "config/enterprise-profit-alert-rules").resolve(),
            "candidate rules must be an enterprise-profit-alert ruleset")
    candidate = read(path)
    require(path.name == f"{candidate.get('version')}.json", "candidate path/version mismatch")
    require(candidate.get("supersedes") == active["version"], "candidate supersedes mismatch")
    expected = copy.deepcopy(active)
    expected.update(version=candidate.get("version"), supersedes=active["version"],
                    change_reason=candidate.get("change_reason"))
    expected["sources"]["domestic"]["customer_types"] = [
        "Customer", "QuasiCustomer", "Agent", "CHANNEL"]
    require(candidate == expected, "candidate changes exceed the authorized customer scope")
    return {**pointer, "ruleset_sha256": sha(path)}, path, candidate


def validate_delivery_receipt(receipt: dict[str, Any], *, alert_count: int,
                              delivery_mode: str) -> None:
    require(receipt.get("status") == "SUCCESS", f"{delivery_mode} delivery receipt is not SUCCESS")
    if delivery_mode == "dry-run":
        require(receipt.get("delivery_status") == "DRY_RUN_NOT_SENT",
                "dry-run delivery state mismatch")
        require(receipt.get("send_attempted") is False
                and receipt.get("message_count") == 0,
                "dry-run attempted message delivery")
        require(receipt.get("alert_count") == alert_count,
                "dry-run alert count mismatch")
        require(receipt.get("would_send") is (alert_count > 0),
                "dry-run would-send decision mismatch")
        return
    require(delivery_mode == "production", "unsupported delivery mode")
    if alert_count == 0:
        require(receipt.get("delivery_status") == "NO_SEND_NO_BUSINESS_ALERT",
                "zero-alert run delivery state mismatch")
        require(receipt.get("message_count") == 0, "zero-alert run sent a message")
    else:
        require(receipt.get("message_readback_confirmed") is True,
                "business alert message readback is not confirmed")


def validate_output(output_dir: Path, rules_path: Path, rules: dict[str, Any],
                    delivery_mode: str = "production") -> dict[str, Any]:
    history_path = output_dir / "enterprise-profit-calendar-history.json"
    alert_path = output_dir / "enterprise-profit-alert.json"
    card_path = output_dir / "enterprise-profit-alert-card.json"
    for path in (history_path, alert_path, card_path):
        require(path.is_file(), f"run artifact is missing: {path.name}")
    history, alert, card = read(history_path), read(alert_path), read(card_path)
    require(history.get("status") == "SUCCESS", "history is not SUCCESS")
    require(alert.get("status") == "SUCCESS", "alert is not SUCCESS")
    require(alert.get("ruleset_version") == rules.get("version"), "run ruleset version mismatch")
    require(alert.get("baseline_method") == rules["baseline"]["method"], "run baseline method mismatch")
    require((history.get("coverage") or {}).get("end") == alert.get("current_day"),
            "history/alert coverage mismatch")
    require(card.get("schema") == "2.0", "run card is not Card 2.0")
    require("收入归属部门" not in json.dumps(alert, ensure_ascii=False),
            "alert artifact contains forbidden income-attribution department")
    require("收入归属部门" not in json.dumps(card, ensure_ascii=False),
            "alert card contains forbidden income-attribution department")
    for source in history.get("input_sources") or []:
        source_path = Path(str(source.get("path") or ""))
        if not source_path.is_absolute():
            source_path = ROOT / source_path
        require(source_path.is_file(), f"run input is missing: {source_path}")
        require(sha(source_path) == source.get("sha256"), f"run input hash mismatch: {source_path}")
        if source.get("role") in ("domestic_history", "domestic_current"):
            scope = (rules.get("sources") or {}).get("domestic", {}).get("customer_types")
            if scope is not None:
                require(read(source_path).get("customer_types") == scope,
                        "domestic source customer scope mismatch")
    receipt_path = output_dir / f"enterprise-profit-alert-delivery-{delivery_mode}.json"
    require(receipt_path.is_file(), f"{delivery_mode} delivery receipt is missing")
    receipt = read(receipt_path)
    validate_delivery_receipt(receipt, alert_count=alert["alert_count"],
                              delivery_mode=delivery_mode)
    return {"current_day": alert["current_day"], "alert_count": alert["alert_count"],
            "delivery_status": receipt.get("delivery_status", "READBACK_CONFIRMED")}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--delivery-mode", choices=("production", "dry-run"),
                        default="production")
    args = parser.parse_args()
    pointer, rules_path, rules = validate_active(ROOT)
    result: dict[str, Any] = {"status": "SUCCESS", "active_version": rules["version"],
                              "ruleset_sha256": pointer["ruleset_sha256"]}
    if args.output_dir:
        result["run"] = validate_output(
            args.output_dir.resolve(), rules_path, rules, delivery_mode=args.delivery_mode)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
