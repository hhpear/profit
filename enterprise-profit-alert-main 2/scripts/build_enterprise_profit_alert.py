#!/usr/bin/env python3
"""Build an independent enterprise profit-change alert from direct governed sources."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from statistics import median
from typing import Any


def read(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def dec(value: object) -> Decimal:
    return Decimal(str(value or 0))


def text(value: Decimal) -> str:
    return format(value, "f")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _calendar_point(series: dict[str, Decimal], target_day: str,
                    rules: dict[str, Any]) -> dict[str, Any]:
    target = date.fromisoformat(target_day)
    calendar = rules["calendar"]
    coverage_start = str(calendar["coverage_start"])
    coverage_end = str(calendar["coverage_end"])
    if not coverage_start <= target_day <= coverage_end:
        raise RuntimeError(f"calendar coverage does not include {target_day}")
    overrides = calendar.get("overrides") or {}
    target_class = str(overrides.get(target_day) or f"WEEKDAY_{target.weekday()}")

    def comparable(day: str) -> bool:
        if not coverage_start <= day <= coverage_end:
            return False
        value = date.fromisoformat(day)
        day_class = str(overrides.get(day) or f"WEEKDAY_{value.weekday()}")
        return day_class == target_class

    baseline_rules = rules["baseline"]
    minimum = int(baseline_rules["minimum_samples"])
    maximum = int(baseline_rules["maximum_samples"])
    candidates = sorted(
        (day for day in series if day < target_day and comparable(day)),
        reverse=True,
    )[:maximum]
    result: dict[str, Any] = {
        "day": target_day,
        "calendar_class": target_class,
        "baseline_days": sorted(candidates),
        "baseline_sample_count": len(candidates),
    }
    if target_day not in series:
        return {**result, "status": "UNRATED_MISSING_CURRENT_DAY"}
    if len(candidates) < minimum:
        return {**result, "status": "UNRATED_INSUFFICIENT_BASELINE"}
    values = [series[day] for day in candidates]
    baseline = Decimal(median(values))
    deviations = [abs(value - baseline) for value in values]
    mad = Decimal(median(deviations))
    actual = series[target_day]
    change = actual - baseline
    band = dec(baseline_rules["mad_multiplier_exclusive"]) * mad
    result.update({
        "实际利润": text(actual),
        "基线利润": text(baseline),
        "利润偏差额": text(change),
        "baseline_mad": text(mad),
        "正常波动下限": text(baseline - band),
        "正常波动上限": text(baseline + band),
    })
    if baseline == 0:
        return {**result, "利润偏差率": None, "status": "UNRATED_ZERO_BASELINE"}
    thresholds = rules["thresholds"]
    denominator = max(abs(baseline), dec(thresholds["rate_denominator_floor"]))
    rate = change / denominator * Decimal(100)
    result["利润偏差率"] = text(rate)
    amount_gate = abs(change) > dec(thresholds["amount_exclusive"])
    rate_gate = abs(rate) > dec(thresholds["rate_percent_exclusive"])
    mad_gate = abs(change) > band
    result["candidate_anomaly"] = amount_gate and rate_gate and mad_gate
    return result


def evaluate_calendar_series(series: dict[str, Decimal], current_day: str,
                             rules: dict[str, Any]) -> dict[str, Any]:
    """Evaluate one enterprise against its own same-weekday robust baseline."""
    current = _calendar_point(series, current_day, rules)
    if str(current.get("status") or "").startswith("UNRATED_"):
        return current
    if not current["candidate_anomaly"]:
        return {**current, "status": "NORMAL", "anomalous_days_in_window": 0}
    baseline = dec(current["基线利润"])
    actual = dec(current["实际利润"])
    if baseline > 0 and actual < 0:
        return {**current, "status": "ALERT_SIGN_FLIP", "anomalous_days_in_window": 1}
    if dec(current["利润偏差额"]) > 0:
        return {**current, "status": "OBSERVATION_INCREASE", "anomalous_days_in_window": 1}

    persistence = rules["persistence"]
    window = int(persistence["lookback_days"])
    required = int(persistence["minimum_anomalous_days"])
    end = date.fromisoformat(current_day)
    points = [_calendar_point(series, (end - timedelta(days=offset)).isoformat(), rules)
              for offset in range(window)]
    anomalous_days = sum(
        1 for point in points
        if point.get("candidate_anomaly") and dec(point.get("利润偏差额")) < 0
    )
    status = "ALERT_SUSTAINED_DECLINE" if anomalous_days >= required else "WATCH_SINGLE_DAY_DECLINE"
    return {**current, "status": status, "anomalous_days_in_window": anomalous_days}


def build_calendar_alert(history: dict[str, Any], current_day: str,
                         rules: dict[str, Any]) -> dict[str, Any]:
    if history.get("status") != "SUCCESS" or (history.get("coverage") or {}).get("end") != current_day:
        raise RuntimeError("calendar history coverage mismatch")
    trend_days = int(rules["trend"]["days"])
    end = date.fromisoformat(current_day)
    expected_trend_days = [(end - timedelta(days=offset)).isoformat()
                           for offset in range(trend_days - 1, -1, -1)]
    buckets = {"alerts": [], "observations": [], "watchlist": [], "unrated": []}
    for source in history.get("rows") or []:
        daily = source.get("daily") or {}
        series = {day: dec(values.get("profit")) for day, values in daily.items()}
        evaluation = evaluate_calendar_series(series, current_day, rules)
        item = {
            "stable_key": f"{source['business_type']}|{source['enterprise_code']}",
            "business_type": source["business_type"],
            "enterprise_code": source["enterprise_code"],
            "企业名称": source["enterprise_name"],
            **evaluation,
        }
        status = evaluation["status"]
        if status.startswith("ALERT_"):
            if any(day not in daily for day in expected_trend_days):
                raise RuntimeError(f"alert enterprise trend is incomplete: {item['stable_key']}")
            item["近15日利润走势"] = [
                {"日期": day, "利润": text(dec(daily[day].get("profit")))}
                for day in expected_trend_days
            ]
            buckets["alerts"].append(item)
        elif status == "OBSERVATION_INCREASE":
            buckets["observations"].append(item)
        elif status == "WATCH_SINGLE_DAY_DECLINE":
            buckets["watchlist"].append(item)
        elif status.startswith("UNRATED_"):
            buckets["unrated"].append(item)
    for values in buckets.values():
        values.sort(key=lambda item: (
            -abs(dec(item.get("利润偏差额"))), item["business_type"], item["enterprise_code"]))
    return {
        "schema_version": "2.0",
        "status": "SUCCESS",
        "task": "enterprise-profit-alert",
        "ruleset_version": rules["version"],
        "current_day": current_day,
        "baseline_method": rules["baseline"]["method"],
        "alert_count": len(buckets["alerts"]),
        "observation_count": len(buckets["observations"]),
        "watch_count": len(buckets["watchlist"]),
        "unrated_count": len(buckets["unrated"]),
        **buckets,
    }


def build_domestic_calendar_history(document: dict[str, Any], current_day: str) -> dict[str, Any]:
    pagination = document.get("pagination") or {}
    if (document.get("status") != "SUCCESS"
            or pagination.get("returned") != pagination.get("reported_total")):
        raise RuntimeError("domestic calendar source is incomplete")
    source_rows = document.get("rows") or []
    actual_days = sorted({str(item.get("date") or "") for item in source_rows if item.get("date")})
    if not actual_days or actual_days[-1] != current_day:
        raise RuntimeError(f"actual domestic source coverage does not end at {current_day}")
    grouped: dict[str, dict[str, Any]] = {}
    for source in source_rows:
        code = str(source.get("enterprise_code") or "").strip()
        name = str(source.get("enterprise_name") or "").strip()
        day = str(source.get("date") or "").strip()
        if not code or not name or not day:
            raise RuntimeError("domestic calendar row identity is incomplete")
        row = grouped.setdefault(code, {
            "business_type": "国内", "enterprise_code": code,
            "enterprise_name": name, "daily": {},
        })
        if row["enterprise_name"] != name:
            raise RuntimeError(f"domestic enterprise name changed: {code}")
        if day in row["daily"]:
            raise RuntimeError(f"domestic enterprise day is duplicated: {code}/{day}")
        row["daily"][day] = {"income": str(source.get("income") or "0"),
                             "profit": str(source.get("profit") or "0")}
    return {
        "schema_version": "2.0", "status": "SUCCESS",
        "entity_semantics": "ENTERPRISE_BY_BUSINESS_TYPE",
        "scope": "DOMESTIC_ONLY_BACKTEST",
        "coverage": {"start": actual_days[0], "end": actual_days[-1]},
        "rows": [grouped[key] for key in sorted(grouped)],
    }


def merge_calendar_history(domestic_history: dict[str, Any], domestic_current: dict[str, Any],
                           international_seed: dict[str, Any],
                           international_receipts: list[tuple[Path, dict[str, Any]]],
                           operating_rules: dict[str, Any], current_day: str) -> dict[str, Any]:
    """Merge governed domestic history and complete Pigeon day receipts."""
    if domestic_history.get("status") != "SUCCESS":
        raise RuntimeError("domestic history is not SUCCESS")
    domestic_enterprises, domestic_audit = domestic_day(domestic_current, current_day)
    history_current = {
        str(item["enterprise_code"]): dec(item["daily"][current_day]["profit"])
        for item in domestic_history.get("rows") or [] if current_day in item.get("daily", {})
    }
    if history_current != {code: item["profit"] for code, item in domestic_enterprises.items()}:
        raise RuntimeError("domestic current detail/history profit reconciliation mismatch")

    rows: dict[str, dict[str, Any]] = {}
    for source in domestic_history.get("rows") or []:
        code = str(source["enterprise_code"])
        rows[f"国内|{code}"] = dict(source)

    if international_seed.get("status") != "SUCCESS":
        raise RuntimeError("international seed history is not SUCCESS")
    if international_seed.get("entity_semantics") not in {
            "INTERNATIONAL_ENTERPRISE", "ENTERPRISE_BY_BUSINESS_TYPE"}:
        raise RuntimeError("international seed history semantics mismatch")
    for source in international_seed.get("rows") or []:
        code = str(source.get("enterprise_code") or "").strip()
        if not code:
            raise RuntimeError("international seed enterprise identity is incomplete")
        rows[f"国际|{code}"] = {
            "business_type": "国际", "enterprise_code": code,
            "enterprise_name": source["enterprise_name"],
            "daily": dict(source.get("daily") or {}),
        }

    receipt_days: set[str] = set()
    receipt_sources = []
    receipt_results = []
    for path, document in international_receipts:
        start = datetime.fromisoformat(str(document.get("start_time") or ""))
        day = start.date().isoformat()
        if day in receipt_days:
            raise RuntimeError(f"duplicate Pigeon receipt day: {day}")
        daily_rows, audit = international_day(document, day, operating_rules)
        receipt_days.add(day)
        receipt_sources.append({"day": day, "path": str(path), "sha256": sha256(path),
                                "row_count": document["row_count"], "audit": audit})
        receipt_results.append((day, daily_rows))

    known_codes = {key.split("|", 1)[1] for key in rows if key.startswith("国际|")}
    known_codes.update(code for _, daily_rows in receipt_results for code in daily_rows)
    names = {code: source["enterprise_name"] for _, daily_rows in receipt_results
             for code, source in daily_rows.items()}
    for code in sorted(known_codes):
        key = f"国际|{code}"
        if key not in rows:
            rows[key] = {
                "business_type": "国际", "enterprise_code": code,
                "enterprise_name": names[code],
                "daily": {},
            }
        row = rows[key]
        if code in names and row["enterprise_name"] != names[code]:
            raise RuntimeError(f"international enterprise name changed: {code}")
        for day, daily_rows in receipt_results:
            if day in row["daily"]:
                raise RuntimeError(f"international enterprise day is duplicated: {code}/{day}")
            source = daily_rows.get(code)
            if source:
                row["daily"][day] = {"income": text(source["income"]),
                                     "profit": text(source["profit"]),
                                     "observation_status": "SOURCE_FACT"}
            else:
                row["daily"][day] = {"income": "0", "profit": "0",
                                     "observation_status": "CONFIRMED_NO_ACTIVITY_IN_COMPLETE_RECEIPT"}

    if current_day not in receipt_days:
        raise RuntimeError("current Pigeon natural-day receipt is missing")
    coverage_starts = [str((domestic_history.get("coverage") or {}).get("start") or "")]
    seed_start = str((international_seed.get("coverage") or {}).get("start") or "")
    if seed_start:
        coverage_starts.append(seed_start)
    return {
        "schema_version": "2.0", "status": "SUCCESS",
        "entity_semantics": "ENTERPRISE_BY_BUSINESS_TYPE", "scope": "DOMESTIC_AND_INTERNATIONAL",
        "coverage": {"start": min(day for day in coverage_starts if day), "end": current_day},
        "rows": [rows[key] for key in sorted(rows)],
        "quality": {"domestic_current_audit": domestic_audit,
                    "international_complete_receipt_days": sorted(receipt_days)},
        "source_artifacts": receipt_sources,
    }


def validate_domestic(document: dict[str, Any], expected_day: str) -> None:
    meta = document.get("meta") or {}
    filters = meta.get("request_filters") or {}
    if document.get("status") != "SUCCESS":
        raise RuntimeError(f"domestic source is not SUCCESS: {expected_day}")
    if meta.get("data_coverage_start") != expected_day or meta.get("data_coverage_end") != expected_day:
        raise RuntimeError(f"domestic coverage mismatch: {expected_day}")
    expected_filters = {
        "cycle": "DAY", "time": expected_day, "tax_basis": "INCLUDED",
        "indicator_view": "OPERATING", "granularity": "CUSTOMER_PROFIT_DETAIL",
    }
    if any(filters.get(key) != value for key, value in expected_filters.items()):
        raise RuntimeError(f"domestic query contract mismatch: {expected_day}")
    income_facts = [item for item in document.get("data") or [] if item.get("fact_type") == "incomeDetail"]
    if len(income_facts) != (document.get("pagination") or {}).get("total"):
        raise RuntimeError(f"domestic pagination mismatch: {expected_day}")


def domestic_day(document: dict[str, Any], expected_day: str) -> tuple[dict[str, dict[str, Any]], dict[str, int]]:
    validate_domestic(document, expected_day)
    grouped: dict[str, dict[str, Any]] = {}
    audit = defaultdict(int)
    for fact in document.get("data") or []:
        if fact.get("fact_type") != "incomeDetail":
            continue
        source = fact.get("source_fields") or {}
        code = str(source.get("enterprise_code") or "").strip()
        name = str(source.get("enterprise_name") or "").strip()
        components = fact.get("source_components") or {}
        if not code or not name:
            if dec(components.get("sms_income")) == 0 and dec(components.get("sms_profit")) == 0:
                audit["zero_income_profit_identity_missing_excluded"] += 1
                continue
            raise RuntimeError(f"domestic nonzero row is missing enterprise identity: {expected_day}")
        row = grouped.setdefault(code, {
            "enterprise_code": code, "enterprise_name": name, "profit": Decimal(),
        })
        if row["enterprise_name"] != name:
            raise RuntimeError(f"domestic enterprise name is not unique: {code}")
        row["profit"] += dec(components.get("sms_profit"))
        audit["included_rows"] += 1
    return grouped, dict(audit)


def validate_international(document: dict[str, Any], expected_day: str) -> None:
    if document.get("truncated") is not False or not isinstance(document.get("rows"), list):
        raise RuntimeError(f"Pigeon receipt is incomplete: {expected_day}")
    if document.get("row_count") != len(document["rows"]):
        raise RuntimeError(f"Pigeon row count mismatch: {expected_day}")
    if document.get("source_platform") != "Pigeon":
        raise RuntimeError(f"Pigeon source platform mismatch: {expected_day}")
    if document.get("group_by") != ["enterprise_id", "account_id", "send_channel_id"]:
        raise RuntimeError(f"Pigeon group_by mismatch: {expected_day}")
    start_at = datetime.fromisoformat(str(document.get("start_time") or ""))
    end_at = datetime.fromisoformat(str(document.get("end_time") or ""))
    if start_at.date().isoformat() != expected_day or end_at.date().isoformat() != expected_day:
        raise RuntimeError(f"Pigeon natural-day coverage mismatch: {expected_day}")


def international_day(document: dict[str, Any], expected_day: str,
                      operating_rules: dict[str, Any]) -> tuple[dict[str, dict[str, Any]], dict[str, int]]:
    validate_international(document, expected_day)
    rules = operating_rules["sources"]["international_sms"]
    exclusion = rules["enterprise_test_exclusion"]
    test_enterprises = set(map(str, exclusion.get("enterprise_ids") or []))
    dedicated_pairs = {str(item["account_id"]): str(item["enterprise_id"])
                       for item in exclusion.get("known_account_enterprise_pairs") or []}
    test_accounts = {str(value).split("/", 1)[0] for value in rules.get("explicit_test_accounts") or []}
    keywords = tuple(rules.get("test_keywords") or [])
    legacy_channel = str((rules.get("oldli_channel_exclusion") or {}).get("contains") or "")
    grouped: dict[str, dict[str, Any]] = {}
    audit = defaultdict(int)
    for source in document["rows"]:
        enterprise_id = str(source.get("enterprise_id") or "").strip()
        enterprise_name = str(source.get("enterprise_name") or "").strip()
        account_id = str(source.get("account_id") or "").strip()
        channel_id = str(source.get("send_channel_id") or "").strip()
        income = dec(source.get("customer_total_amount"))
        cost = dec(source.get("channel_total_amount"))
        expected_test_enterprise = dedicated_pairs.get(account_id)
        if expected_test_enterprise and enterprise_id != expected_test_enterprise:
            raise RuntimeError(f"dedicated test account enterprise mismatch: {account_id}")
        identity = " ".join(str(source.get(field) or "") for field in
                            ("account_id", "account_name", "enterprise_id", "enterprise_name"))
        if enterprise_id in test_enterprises or account_id in test_accounts or any(key in identity for key in keywords):
            audit["test_rows_excluded"] += 1
            continue
        if legacy_channel and legacy_channel in channel_id:
            audit["legacy_channel_rows_excluded"] += 1
            continue
        if (income or cost) and (not enterprise_id or not enterprise_name or not account_id):
            raise RuntimeError(f"nonzero Pigeon row is missing enterprise/account identity: {expected_day}")
        if cost and not channel_id:
            raise RuntimeError(f"nonzero Pigeon cost row is missing channel identity: {expected_day}")
        if not enterprise_id:
            continue
        row = grouped.setdefault(enterprise_id, {
            "enterprise_code": enterprise_id, "enterprise_name": enterprise_name,
            "income": Decimal(), "profit": Decimal(),
        })
        if row["enterprise_name"] != enterprise_name:
            raise RuntimeError(f"international enterprise name is not unique: {enterprise_id}")
        row["income"] += income
        row["profit"] += income - cost
        audit["included_rows"] += 1
    return grouped, dict(audit)


def build_alerts(current: dict[str, dict[str, Any]], comparison: dict[str, dict[str, Any]],
                 business_type: str, amount_threshold: Decimal,
                 rate_threshold: Decimal) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    alerts = []
    unrated = []
    for code in sorted(set(current) | set(comparison)):
        current_row = current.get(code)
        comparison_row = comparison.get(code)
        identity = current_row or comparison_row
        current_profit = (current_row or {}).get("profit", Decimal())
        comparison_profit = (comparison_row or {}).get("profit", Decimal())
        change = current_profit - comparison_profit
        base = {
            "stable_key": f"{business_type}|{code}",
            "business_type": business_type,
            "enterprise_code": code,
            "企业名称": identity["enterprise_name"],
            "利润变化额": text(change),
            "昨日利润": text(current_profit),
            "对比利润": text(comparison_profit),
        }
        if comparison_profit == 0:
            if abs(change) > amount_threshold:
                unrated.append({**base, "利润变化率": None, "status": "UNRATED_ZERO_COMPARISON"})
            continue
        rate = change / abs(comparison_profit) * Decimal(100)
        if abs(change) > amount_threshold and abs(rate) > rate_threshold:
            alerts.append({**base, "利润变化率": text(rate), "status": "ALERT"})
    alerts.sort(key=lambda item: (-abs(dec(item["利润变化额"])), item["business_type"], item["enterprise_code"]))
    return alerts, unrated


def attach_trends(alerts: list[dict[str, Any]], document: dict[str, Any],
                  current_day: str, comparison_day: str, trend_days: int) -> None:
    if document.get("status") != "SUCCESS":
        raise RuntimeError("enterprise profit trend source is not SUCCESS")
    coverage = document.get("coverage") or {}
    end = date.fromisoformat(current_day)
    expected_days = [(end - timedelta(days=offset)).isoformat()
                     for offset in range(trend_days - 1, -1, -1)]
    if coverage != {"start": expected_days[0], "end": current_day, "days": trend_days}:
        raise RuntimeError("enterprise profit trend coverage mismatch")
    if document.get("entity_semantics") != "ENTERPRISE_BY_BUSINESS_TYPE":
        raise RuntimeError("enterprise profit trend entity semantics mismatch")
    index: dict[str, dict[str, Any]] = {}
    for row in document.get("rows") or []:
        key = f"{row.get('business_type')}|{row.get('enterprise_code')}"
        if key in index:
            raise RuntimeError(f"enterprise profit trend stable key is duplicated: {key}")
        daily = row.get("daily") or {}
        if sorted(daily) != expected_days:
            raise RuntimeError(f"enterprise profit trend does not contain {trend_days} real days: {key}")
        index[key] = row
    for alert in alerts:
        key = alert["stable_key"]
        row = index.get(key)
        if row is None:
            raise RuntimeError(f"alert enterprise profit trend is missing: {key}")
        if row.get("enterprise_name") != alert["企业名称"]:
            raise RuntimeError(f"alert enterprise profit trend name mismatch: {key}")
        daily = row["daily"]
        if dec(daily[current_day].get("profit")) != dec(alert["昨日利润"]):
            raise RuntimeError(f"alert current profit does not reconcile to trend: {key}")
        if dec(daily[comparison_day].get("profit")) != dec(alert["对比利润"]):
            raise RuntimeError(f"alert comparison profit does not reconcile to trend: {key}")
        alert["近15日利润走势"] = [
            {"日期": day, "利润": text(dec(daily[day].get("profit")))} for day in expected_days
        ]


def build(args: argparse.Namespace) -> dict[str, Any]:
    rules = read(args.alert_rules)
    operating_rules = read(args.operating_rules)
    current_day = date.fromisoformat(args.current_day)
    comparison_day = date.fromisoformat(args.comparison_day)
    if current_day - comparison_day != timedelta(days=1):
        raise RuntimeError("current day and comparison day must be adjacent natural days")
    comparison_rules = rules["comparison"]
    amount_threshold = dec(comparison_rules["amount_threshold_exclusive"])
    rate_threshold = dec(comparison_rules["rate_threshold_percent_exclusive"])
    domestic_current_doc = read(args.domestic_current)
    domestic_compare_doc = read(args.domestic_comparison)
    international_current_doc = read(args.international_current)
    international_compare_doc = read(args.international_comparison)
    domestic_current, domestic_current_audit = domestic_day(domestic_current_doc, args.current_day)
    domestic_compare, domestic_compare_audit = domestic_day(domestic_compare_doc, args.comparison_day)
    international_current, current_audit = international_day(
        international_current_doc, args.current_day, operating_rules)
    international_compare, compare_audit = international_day(
        international_compare_doc, args.comparison_day, operating_rules)

    domestic_alerts, domestic_unrated = build_alerts(
        domestic_current, domestic_compare, "国内", amount_threshold, rate_threshold)
    international_alerts, international_unrated = build_alerts(
        international_current, international_compare, "国际", amount_threshold, rate_threshold)
    alerts = sorted(domestic_alerts + international_alerts,
                    key=lambda item: (-abs(dec(item["利润变化额"])), item["business_type"], item["enterprise_code"]))
    trend_days = int(rules["sources"]["trend"]["days"])
    attach_trends(alerts, read(args.trend), args.current_day, args.comparison_day, trend_days)
    sources = [
        ("domestic_current", args.domestic_current),
        ("domestic_comparison", args.domestic_comparison),
        ("international_current", args.international_current),
        ("international_comparison", args.international_comparison),
        ("operating_rules", args.operating_rules),
        ("alert_rules", args.alert_rules),
        ("enterprise_profit_trend", args.trend),
    ]
    return {
        "schema_version": "1.0", "status": "SUCCESS",
        "task": "enterprise-profit-alert", "ruleset_version": rules["version"],
        "current_day": args.current_day, "comparison_day": args.comparison_day,
        "thresholds": {"profit_change_amount_exclusive": text(amount_threshold),
                       "profit_change_rate_percent_exclusive": text(rate_threshold),
                       "mode": "ABSOLUTE_AND"},
        "alert_count": len(alerts),
        "trend": {"days": trend_days, "end_day": args.current_day,
                  "enterprise_count": len(alerts), "reconciled": True},
        "counts": {"国内": len(domestic_alerts), "国际": len(international_alerts)},
        "alerts": alerts,
        "unrated_zero_comparison": domestic_unrated + international_unrated,
        "quality": {
            "domestic_current_enterprises": len(domestic_current),
            "domestic_comparison_enterprises": len(domestic_compare),
            "domestic_current_audit": domestic_current_audit,
            "domestic_comparison_audit": domestic_compare_audit,
            "international_current_enterprises": len(international_current),
            "international_comparison_enterprises": len(international_compare),
            "international_current_audit": current_audit,
            "international_comparison_audit": compare_audit,
            "dashboard_base_consumed": False,
            "dashboard_card_consumed": False,
            "dashboard_publish_state_consumed": False,
        },
        "source_artifacts": [{"role": role, "path": str(path), "sha256": sha256(path)}
                             for role, path in sources],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--alert-rules", type=Path, required=True)
    parser.add_argument("--operating-rules", type=Path, required=True)
    parser.add_argument("--current-day", required=True)
    parser.add_argument("--comparison-day", required=True)
    parser.add_argument("--domestic-current", type=Path, required=True)
    parser.add_argument("--domestic-comparison", type=Path, required=True)
    parser.add_argument("--international-current", type=Path, required=True)
    parser.add_argument("--international-comparison", type=Path, required=True)
    parser.add_argument("--trend", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = build(args)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "alert_count": result["alert_count"],
                      "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
