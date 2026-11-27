#!/usr/bin/env python3
"""Query BMP enterprise profit without department or CRM enrichment."""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from zoneinfo import ZoneInfo

DETAIL = "/api/bmp/bi/customer-profit/query"
CUSTOMER_TYPES = ["Customer", "QuasiCustomer", "Agent", "CHANNEL"]


def money(row: dict, field: str) -> Decimal:
    value = row.get(field)
    if value in (None, ""):
        raise RuntimeError(f"missing source amount: {field}")
    amount = Decimal(str(value))
    if not amount.is_finite():
        raise RuntimeError(f"invalid source amount: {field}")
    return amount


def query(start_day: str, end_day: str, request_fn, page_size: int = 200,
          current_detail: bool = False) -> dict:
    start, end = date.fromisoformat(start_day), date.fromisoformat(end_day)
    if start > end or page_size < 1 or (current_detail and start != end):
        raise RuntimeError("invalid query range/page size")
    common = {
        "customerIds": [], "businessBodyIds": [], "businessUserIds": [],
        "customerTypes": list(CUSTOMER_TYPES),
        "profitType": None, "marketIndex": False, "tax": True, "incrStock": None,
        "natures": [], "projectIds": [], "projectLeaderIds": [], "projectStates": [],
        "entCodes": [], "timeType": "Day", "cycle": "Day", "source": "Detail",
        "time": "", "startTime": f"{start_day} 00:00:00",
        "endTime": f"{end_day} 23:59:59", "profitIsAsc": False,
    }
    grouped = defaultdict(lambda: {"name": None, "income": Decimal(), "profit": Decimal()})
    facts, excluded = [], []
    total, returned, page = None, 0, 1
    while True:
        response = request_fn(DETAIL, {**common, "page": {
            "pageNumber": page, "pageSize": page_size,
            "sorts": [{"fieldName": "", "order": "Desc"}],
        }})
        if response.get("success") is False or response.get("code") not in (None, "GB:0000"):
            raise RuntimeError(f"BMP query failed: {response.get('code')}; "
                               f"{response.get('message') or response.get('msg') or 'source rejected query'}")
        batch = response.get("data")
        reported = response.get("totalCount")
        if not isinstance(batch, list) or not isinstance(reported, int) or reported < 0:
            raise RuntimeError("BMP page has invalid rows/totalCount")
        if total is None:
            total = reported
        if total != reported:
            raise RuntimeError("BMP totalCount changed during pagination")
        returned += len(batch)
        if returned > total:
            raise RuntimeError("BMP pagination exceeds totalCount")
        for source in batch:
            day = str(source.get("timeStr") or "")
            if not start_day <= day <= end_day or date.fromisoformat(day).isoformat() != day:
                raise RuntimeError("BMP source date outside requested range")
            income, profit = money(source, "smsIncome"), money(source, "smsProfit")
            code, name = str(source.get("entCode") or "").strip(), str(source.get("entName") or "").strip()
            if not code or not name:
                if income or profit:
                    raise RuntimeError("nonzero BMP fact missing enterprise identity")
                excluded.append({"date": day, "customer_id": str(source.get("customerId") or "")})
            else:
                item = grouped[(day, code)]
                if item["name"] not in (None, name):
                    raise RuntimeError("enterprise name changed within one day")
                item["name"] = name
                item["income"] += income
                item["profit"] += profit
            if current_detail:
                facts.append({"fact_type": "incomeDetail", "source_locator": DETAIL,
                    "source_fields": {"enterprise_code": code, "enterprise_name": name},
                    "source_components": {"sms_income": str(income), "sms_profit": str(profit)}})
        print(json.dumps({"status": "RUNNING", "page": page, "rows": returned,
                          "total": total}), flush=True)
        if returned == total:
            break
        if not batch:
            raise RuntimeError("BMP pagination ended early")
        page += 1
    base = {"schema_version": "1.0", "status": "SUCCESS", "currency": "CNY",
            "customer_types": list(CUSTOMER_TYPES),
            "source_interface": f"POST {DETAIL}"}
    if current_detail:
        return {**base, "data": facts, "pagination": {"total": total, "returned": returned, "pages": page},
            "meta": {"data_coverage_start": start_day, "data_coverage_end": end_day,
                "source_interfaces": [f"POST {DETAIL}"], "request_filters": {
                    "cycle": "DAY", "time": end_day, "tax_basis": "INCLUDED",
                    "indicator_view": "OPERATING", "granularity": "CUSTOMER_PROFIT_DETAIL"}}}
    return {**base, "coverage": {"start": start_day, "end": end_day},
        "tax_basis": "INCLUDED", "indicator_view": "OPERATING", "entity_semantics": "ENTERPRISE",
        "stable_key": "date + enterprise_code",
        "pagination": {"returned": returned, "reported_total": total, "pages": page},
        "zero_amount_missing_enterprise_exclusion": {"count": len(excluded), "rows": excluded},
        "rows": [{"date": day, "enterprise_code": code, "enterprise_name": item["name"],
                  "income": str(item["income"]), "profit": str(item["profit"])}
                 for (day, code), item in sorted(grouped.items())]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--start-date", required=True)
    parser.add_argument("--end-date", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--current-detail", action="store_true")
    parser.add_argument("--candidate-rules", type=Path,
                        help="Read-only source validation for a customer-scope candidate")
    args = parser.parse_args()
    if date.fromisoformat(args.end_date) >= datetime.now(ZoneInfo("Asia/Shanghai")).date():
        raise RuntimeError("only complete natural days may be queried")
    try:
        from .validate_enterprise_profit_alert_governance import validate_active, validate_candidate
    except ImportError:
        from validate_enterprise_profit_alert_governance import validate_active, validate_candidate
    _, _, rules = (validate_candidate(args.candidate_rules) if args.candidate_rules
                   else validate_active())
    if rules["sources"]["domestic"].get("customer_types") != CUSTOMER_TYPES:
        raise RuntimeError("domestic query customer types do not match active rules")
    sys.path.insert(0, str(Path.home() / ".codex/skills/_dashboard_shared"))
    from runtime import bmp_post
    result = query(args.start_date, args.end_date, bmp_post, current_detail=args.current_detail)
    try:
        from .normalize_pigeon_artifact_receipts import atomic_write
    except ImportError:
        from normalize_pigeon_artifact_receipts import atomic_write
    atomic_write(args.output, result)
    print(json.dumps({"status": "SUCCESS", "output": str(args.output)}))


if __name__ == "__main__":
    main()
