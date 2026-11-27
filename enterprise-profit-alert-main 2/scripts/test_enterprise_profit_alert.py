#!/usr/bin/env python3
"""Regression tests for the independent enterprise profit alert."""

from __future__ import annotations

import unittest
import importlib
import json
from pathlib import Path
from decimal import Decimal
from tempfile import TemporaryDirectory

from scripts.build_enterprise_profit_alert import (
    attach_trends,
    build_alerts,
    build_calendar_alert,
    build_domestic_calendar_history,
    merge_calendar_history,
    evaluate_calendar_series,
)
from scripts.build_enterprise_profit_alert_card import build as build_card
from scripts.build_enterprise_profit_alert_history import merge_international_seeds
from scripts.send_enterprise_profit_alert_card import (
    decode,
    delivery_context,
    rendered_readback_markers,
    select_delivery,
    validate_card_contract,
    walk,
)


def row(name: str, profit: str, departments: tuple[str, ...] = ("运营部",)) -> dict:
    return {
        "enterprise_name": name, "profit": Decimal(profit),
        "income_departments": set(departments), "all_departments": set(departments),
    }


class ProfitAlertRuleTest(unittest.TestCase):
    def alert(self, current: str, comparison: str):
        return build_alerts(
            {"E1": row("企业一", current)}, {"E1": row("企业一", comparison)},
            "国内", Decimal("200"), Decimal("10"),
        )

    def test_thresholds_are_exclusive_and_combined_with_and(self) -> None:
        alerts, _ = self.alert("1200", "1000")
        self.assertEqual([], alerts)  # amount is exactly 200
        alerts, _ = self.alert("3201", "3000")
        self.assertEqual([], alerts)  # amount > 200, but rate is not > 10%
        alerts, _ = self.alert("1201", "1000")
        self.assertEqual(1, len(alerts))

    def test_negative_change_uses_absolute_thresholds(self) -> None:
        alerts, _ = self.alert("700", "1000")
        self.assertEqual(1, len(alerts))
        self.assertEqual("-300", alerts[0]["利润变化额"])
        self.assertEqual("-30.0", alerts[0]["利润变化率"])

    def test_zero_comparison_is_unrated_not_alerted(self) -> None:
        alerts, unrated = self.alert("500", "0")
        self.assertEqual([], alerts)
        self.assertEqual(1, len(unrated))
        self.assertIsNone(unrated[0]["利润变化率"])

    def test_departments_are_never_copied_to_alerts(self) -> None:
        alerts, _ = build_alerts(
            {"E1": row("企业一", "1500", ("运营部", "重客部"))},
            {"E1": row("企业一", "1000")},
            "国内", Decimal("200"), Decimal("10"),
        )
        self.assertNotIn("收入归属部门", alerts[0])

    def test_domestic_source_department_dimensions_are_ignored(self) -> None:
        from scripts.build_enterprise_profit_alert import domestic_day
        document = {
            "status": "SUCCESS",
            "meta": {
                "data_coverage_start": "2026-09-29",
                "data_coverage_end": "2026-09-29",
                "request_filters": {
                    "cycle": "DAY", "time": "2026-09-29",
                    "tax_basis": "INCLUDED", "indicator_view": "OPERATING",
                    "granularity": "CUSTOMER_PROFIT_DETAIL",
                },
            },
            "pagination": {"total": 1},
            "data": [{
                "fact_type": "incomeDetail",
                "source_fields": {"enterprise_code": "E1", "enterprise_name": "企业一"},
                "source_components": {"sms_income": "100", "sms_profit": "20"},
                "dimensions": [{"dimension_type": "DEPARTMENT",
                                "dimension_name": "不应进入告警"}],
            }],
        }
        rows, _ = domestic_day(document, "2026-09-29")
        self.assertEqual({"enterprise_code", "enterprise_name", "profit"},
                         set(rows["E1"]))


class CalendarAwareProfitAlertRuleTest(unittest.TestCase):
    def rules(self) -> dict:
        return {
            "baseline": {
                "minimum_samples": 6,
                "maximum_samples": 8,
                "mad_multiplier_exclusive": "3",
            },
            "thresholds": {
                "amount_exclusive": "200",
                "rate_percent_exclusive": "10",
                "rate_denominator_floor": "200",
            },
            "persistence": {"lookback_days": 3, "minimum_anomalous_days": 2},
            "calendar": {"coverage_start": "2026-01-01", "coverage_end": "2026-12-31",
                         "overrides": {}},
        }

    def series(self, current: str, previous_friday: str = "1000") -> dict[str, Decimal]:
        values = {
            "2026-07-17": Decimal("1000"), "2026-07-24": Decimal("1000"),
            "2026-07-31": Decimal("1000"), "2026-08-07": Decimal("1000"),
            "2026-08-14": Decimal("1000"), "2026-08-21": Decimal("1000"),
            "2026-07-18": Decimal("1000"), "2026-07-25": Decimal("1000"),
            "2026-08-01": Decimal("1000"), "2026-08-08": Decimal("1000"),
            "2026-08-15": Decimal("1000"), "2026-08-22": Decimal("1000"),
            "2026-08-27": Decimal("1000"), "2026-08-28": Decimal(previous_friday),
            "2026-08-29": Decimal(current),
        }
        return values

    def test_weekend_uses_same_weekday_history_not_previous_workday(self) -> None:
        result = evaluate_calendar_series(
            self.series("1000", previous_friday="3000"), "2026-08-29", self.rules())
        self.assertEqual("NORMAL", result["status"])
        self.assertEqual("1000", result["基线利润"])
        self.assertNotIn("2026-08-28", result["baseline_days"])

    def test_insufficient_same_weekday_history_is_unrated(self) -> None:
        history = {key: value for key, value in self.series("500").items()
                   if key >= "2026-08-14"}
        result = evaluate_calendar_series(history, "2026-08-29", self.rules())
        self.assertEqual("UNRATED_INSUFFICIENT_BASELINE", result["status"])
        self.assertEqual(2, result["baseline_sample_count"])

    def test_profit_increase_is_observation_not_business_alert(self) -> None:
        result = evaluate_calendar_series(self.series("1500"), "2026-08-29", self.rules())
        self.assertEqual("OBSERVATION_INCREASE", result["status"])
        self.assertEqual("500", result["利润偏差额"])

    def test_sign_flip_is_immediate_alert_without_persistence(self) -> None:
        result = evaluate_calendar_series(self.series("-500"), "2026-08-29", self.rules())
        self.assertEqual("ALERT_SIGN_FLIP", result["status"])
        self.assertEqual("-150.0", result["利润偏差率"])

    def test_ordinary_decline_requires_two_of_three_anomalous_days(self) -> None:
        history = self.series("500")
        for day in ("2026-07-16", "2026-07-23", "2026-07-30", "2026-08-06", "2026-08-13", "2026-08-20"):
            history[day] = Decimal("1000")
        history["2026-08-27"] = Decimal("500")
        result = evaluate_calendar_series(history, "2026-08-29", self.rules())
        self.assertEqual("ALERT_SUSTAINED_DECLINE", result["status"])
        self.assertEqual(2, result["anomalous_days_in_window"])

    def test_one_day_ordinary_decline_is_watch_not_alert(self) -> None:
        result = evaluate_calendar_series(self.series("500"), "2026-08-29", self.rules())
        self.assertEqual("WATCH_SINGLE_DAY_DECLINE", result["status"])

    def test_mad_gate_filters_normal_enterprise_variation(self) -> None:
        history = self.series("1400")
        for day, value in zip(
                ("2026-07-18", "2026-07-25", "2026-08-01", "2026-08-08", "2026-08-15", "2026-08-22"),
                ("800", "900", "1000", "1100", "1200", "1300")):
            history[day] = Decimal(value)
        result = evaluate_calendar_series(history, "2026-08-29", self.rules())
        self.assertEqual("NORMAL", result["status"])
        self.assertEqual("150", result["baseline_mad"])
        self.assertEqual(("600", "1500"),
                         (result["正常波动下限"], result["正常波动上限"]))

    def test_explicit_holiday_does_not_reuse_ordinary_weekday_baseline(self) -> None:
        rules = self.rules()
        rules["calendar"]["overrides"]["2026-08-29"] = "STATUTORY_HOLIDAY"
        result = evaluate_calendar_series(self.series("500"), "2026-08-29", rules)
        self.assertEqual("UNRATED_INSUFFICIENT_BASELINE", result["status"])
        self.assertEqual("STATUTORY_HOLIDAY", result["calendar_class"])

    def test_calendar_outside_confirmed_coverage_fails_closed(self) -> None:
        rules = self.rules()
        rules["calendar"]["coverage_end"] = "2026-08-28"
        with self.assertRaisesRegex(RuntimeError, "calendar coverage"):
            evaluate_calendar_series(self.series("500"), "2026-08-29", rules)

    def test_rate_uses_approved_amount_threshold_as_denominator_floor(self) -> None:
        history = self.series("202")
        for day in ("2026-07-18", "2026-07-25", "2026-08-01",
                    "2026-08-08", "2026-08-15", "2026-08-22"):
            history[day] = Decimal("1")
        result = evaluate_calendar_series(history, "2026-08-29", self.rules())
        self.assertEqual("100.500", result["利润偏差率"])


class CalendarAwareProfitAlertArtifactTest(unittest.TestCase):
    def test_late_enterprise_is_confirmed_zero_on_prior_complete_receipt(self) -> None:
        domestic = {"status": "SUCCESS", "coverage": {"start": "2026-08-01", "end": "2026-08-30"},
                    "rows": []}
        detail = {"status": "SUCCESS", "meta": {"data_coverage_start": "2026-08-30",
                  "data_coverage_end": "2026-08-30", "request_filters": {"cycle": "DAY",
                  "time": "2026-08-30", "tax_basis": "INCLUDED", "indicator_view": "OPERATING",
                  "granularity": "CUSTOMER_PROFIT_DETAIL"}}, "pagination": {"total": 0}, "data": []}
        mapping = {"status": "SUCCESS", "ruleset_version": "r1",
                   "pagination": {"has_more": False}, "mappings": []}
        operating = {"version": "r1", "sources": {"international_sms": {
            "enterprise_test_exclusion": {}, "explicit_test_accounts": [], "test_keywords": [],
            "oldli_channel_exclusion": {"contains": ""}}}}
        def receipt(day: str, rows: list[dict]) -> dict:
            return {"truncated": False, "source_platform": "Pigeon",
                    "group_by": ["enterprise_id", "account_id", "send_channel_id"],
                    "start_time": f"{day}T00:00:00", "end_time": f"{day}T23:59:59",
                    "row_count": len(rows), "rows": rows}
        source_row = {"enterprise_id": "I1", "enterprise_name": "国际一",
                      "account_id": "A1", "send_channel_id": "C1",
                      "customer_total_amount": "10", "channel_total_amount": "7"}
        with TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ("d1.json", "d2.json")]
            docs = [receipt("2026-08-29", []), receipt("2026-08-30", [source_row])]
            for path, document in zip(paths, docs):
                path.write_text(__import__("json").dumps(document), encoding="utf-8")
            result = merge_calendar_history(domestic, detail,
                {"status": "SUCCESS", "entity_semantics": "INTERNATIONAL_ENTERPRISE", "rows": []},
                list(zip(paths, docs)), operating, "2026-08-30")
        row = next(item for item in result["rows"] if item["enterprise_code"] == "I1")
        self.assertEqual("CONFIRMED_NO_ACTIVITY_IN_COMPLETE_RECEIPT",
                         row["daily"]["2026-08-29"]["observation_status"])

    def test_seed_overlap_must_match(self) -> None:
        base = {"status": "SUCCESS", "entity_semantics": "INTERNATIONAL_ENTERPRISE",
                "coverage": {"start": "2026-08-01", "end": "2026-08-15"},
                "rows": [{"enterprise_code": "I1", "enterprise_name": "国际一",
                          "daily": {"2026-08-15": {"income": "10", "profit": "2"}}}]}
        changed = {**base, "coverage": {"start": "2026-08-15", "end": "2026-08-29"},
                   "rows": [{"enterprise_code": "I1", "enterprise_name": "国际一",
                             "daily": {"2026-08-15": {"income": "10", "profit": "3"}}}]}
        with self.assertRaisesRegex(RuntimeError, "overlap mismatch"):
            merge_international_seeds([base, changed])

    def test_unified_history_requires_current_pigeon_receipt(self) -> None:
        domestic = {"status": "SUCCESS", "coverage": {"start": "2026-08-01", "end": "2026-08-30"},
                    "rows": []}
        detail = {"status": "SUCCESS", "meta": {"data_coverage_start": "2026-08-30",
                  "data_coverage_end": "2026-08-30", "request_filters": {"cycle": "DAY",
                  "time": "2026-08-30", "tax_basis": "INCLUDED", "indicator_view": "OPERATING",
                  "granularity": "CUSTOMER_PROFIT_DETAIL"}}, "pagination": {"total": 0}, "data": []}
        seed = {"status": "SUCCESS", "entity_semantics": "INTERNATIONAL_ENTERPRISE",
                "coverage": {"start": "2026-08-01", "end": "2026-08-29"}, "rows": []}
        mapping = {"status": "SUCCESS", "ruleset_version": "r1",
                   "pagination": {"has_more": False}, "mappings": []}
        operating = {"version": "r1", "sources": {"international_sms": {
            "enterprise_test_exclusion": {}, "explicit_test_accounts": [], "test_keywords": [],
            "oldli_channel_exclusion": {"contains": ""}}}}
        with self.assertRaisesRegex(RuntimeError, "current Pigeon"):
            merge_calendar_history(domestic, detail, seed, [], operating, "2026-08-30")

    def test_domestic_history_uses_actual_row_coverage(self) -> None:
        source = {"status": "SUCCESS", "coverage": {"start": "2026-08-27", "end": "2026-08-29"},
                  "pagination": {"returned": 2, "reported_total": 2}, "rows": [
                      {"date": "2026-08-27", "enterprise_code": "E1", "enterprise_name": "企业一",
                       "income": "100", "profit": "10"},
                      {"date": "2026-08-28", "enterprise_code": "E1", "enterprise_name": "企业一",
                       "income": "100", "profit": "20"},
                  ]}
        history = build_domestic_calendar_history(source, "2026-08-28")
        self.assertEqual({"start": "2026-08-27", "end": "2026-08-28"}, history["coverage"])
        self.assertEqual("20", history["rows"][0]["daily"]["2026-08-28"]["profit"])

    def test_domestic_history_rejects_requested_but_absent_current_day(self) -> None:
        source = {"status": "SUCCESS", "coverage": {"start": "2026-08-27", "end": "2026-08-29"},
                  "pagination": {"returned": 1, "reported_total": 1}, "rows": [
                      {"date": "2026-08-28", "enterprise_code": "E1", "enterprise_name": "企业一",
                       "income": "100", "profit": "20"},
                  ]}
        with self.assertRaisesRegex(RuntimeError, "actual domestic source coverage"):
            build_domestic_calendar_history(source, "2026-08-29")

    def test_build_separates_alert_observation_watch_and_unrated(self) -> None:
        days = [f"2026-08-{day:02d}" for day in range(15, 30)]
        saturday_baselines = {
            "2026-07-18": "1000", "2026-07-25": "1000", "2026-08-01": "1000",
            "2026-08-08": "1000", "2026-08-15": "1000", "2026-08-22": "1000",
        }
        def daily(current: str) -> dict:
            result = {day: {"profit": "1000"} for day in days}
            result.update({day: {"profit": value} for day, value in saturday_baselines.items()})
            result["2026-08-29"] = {"profit": current}
            return result
        history = {"status": "SUCCESS", "coverage": {"end": "2026-08-29"}, "rows": [
            {"business_type": "国内", "enterprise_code": "E1", "enterprise_name": "企业一",
             "income_departments": ["运营部"], "daily": daily("-500")},
            {"business_type": "国内", "enterprise_code": "E2", "enterprise_name": "企业二",
             "income_departments": ["重客部"], "daily": daily("1500")},
            {"business_type": "国内", "enterprise_code": "E3", "enterprise_name": "企业三",
             "income_departments": ["运营部"], "daily": daily("500")},
            {"business_type": "国际", "enterprise_code": "I1", "enterprise_name": "国际一",
             "income_departments": ["国际短信项目组"],
             "daily": {day: {"profit": "100"} for day in days}},
        ]}
        rules = {"version": "v2", "baseline": {"method": "SAME_CALENDAR_CLASS_MEDIAN_MAD",
                 "minimum_samples": 6, "maximum_samples": 8,
                 "mad_multiplier_exclusive": "3"},
                 "thresholds": {"amount_exclusive": "200", "rate_percent_exclusive": "10",
                                "rate_denominator_floor": "200"},
                 "persistence": {"lookback_days": 3, "minimum_anomalous_days": 2},
                 "calendar": {"coverage_start": "2026-01-01", "coverage_end": "2026-12-31",
                              "overrides": {}},
                 "trend": {"days": 15}}
        result = build_calendar_alert(history, "2026-08-29", rules)
        self.assertEqual(["E1"], [item["enterprise_code"] for item in result["alerts"]])
        self.assertEqual(["E2"], [item["enterprise_code"] for item in result["observations"]])
        self.assertEqual(["E3"], [item["enterprise_code"] for item in result["watchlist"]])
        self.assertEqual(["I1"], [item["enterprise_code"] for item in result["unrated"]])
        self.assertEqual(15, len(result["alerts"][0]["近15日利润走势"]))
        self.assertEqual(rules["baseline"]["method"], result["baseline_method"])


class ProfitAlertCardTest(unittest.TestCase):
    def test_card_contains_exact_required_fields(self) -> None:
        alert = {
            "status": "SUCCESS", "ruleset_version": "v1",
            "current_day": "2026-08-26", "comparison_day": "2026-08-25",
            "alert_count": 2, "counts": {"国内": 2, "国际": 0},
            "alerts": [{
                "stable_key": "国内|E1",
                "business_type": "国内", "企业名称": "企业一",
                "收入归属部门": ["运营部"], "利润变化额": "500",
                "利润变化率": "50", "昨日利润": "1500", "对比利润": "1000",
                "近15日利润走势": [{"日期": f"2026-08-{day:02d}", "利润": str(day)}
                                  for day in range(12, 27)],
            }, {
                "stable_key": "国内|E2",
                "business_type": "国内", "企业名称": "企业二",
                "收入归属部门": ["重客部"], "利润变化额": "-500",
                "利润变化率": "-50", "昨日利润": "500", "对比利润": "1000",
                "近15日利润走势": [{"日期": f"2026-08-{day:02d}", "利润": str(40 - day)}
                                  for day in range(12, 27)],
            }],
        }
        rules = {"version": "v1", "display": {"sections": [
            {"id": "profit_decrease", "title": "利润下降", "direction": "NEGATIVE", "color": "red"},
            {"id": "profit_increase", "title": "利润上升", "direction": "POSITIVE", "color": "green"},
        ]}}
        card = build_card(alert, rules)
        payload = str(card)
        for field in ("企业名称", "利润变化额", "利润变化率", "昨日利润", "对比利润"):
            self.assertIn(field, payload)
        self.assertNotIn("收入归属部门", payload)
        self.assertIn("profit_decrease_heading", payload)
        self.assertIn("profit_increase_heading", payload)
        charts = [item for section in card["body"]["elements"]
                  for column in section.get("columns", [])
                  for item in column.get("elements", []) if item.get("tag") == "chart"]
        self.assertEqual(2, len(charts))
        self.assertEqual([["red"], ["green"]], [item["chart_spec"]["color"] for item in charts])
        self.assertTrue(all(len(item["chart_spec"]["data"]["values"]) == 15 for item in charts))

    def test_calendar_card_only_details_business_alerts(self) -> None:
        trend = [{"日期": f"2026-08-{day:02d}", "利润": str(day)} for day in range(15, 30)]
        alert = {
            "schema_version": "2.0", "status": "SUCCESS", "ruleset_version": "v2",
            "current_day": "2026-08-29", "baseline_method": "SAME_WEEKDAY_MEDIAN_MAD",
            "alert_count": 1, "observation_count": 2, "watch_count": 3, "unrated_count": 4,
            "alerts": [{"stable_key": "国内|E1", "business_type": "国内",
                        "enterprise_code": "E1", "企业名称": "企业一", "收入归属部门": ["运营部"],
                        "status": "ALERT_SIGN_FLIP", "利润偏差额": "-1500",
                        "利润偏差率": "-150", "实际利润": "-500", "基线利润": "1000",
                        "正常波动下限": "1000", "正常波动上限": "1000",
                        "baseline_sample_count": 6, "近15日利润走势": trend}],
            "observations": [], "watchlist": [], "unrated": [],
        }
        rules = {"version": "v2", "baseline": {"minimum_samples": 6,
                 "mad_multiplier_exclusive": "3"},
                 "thresholds": {"amount_exclusive": "200", "rate_percent_exclusive": "10"},
                 "display": {"title": "企业客户利润异常告警", "trend_title": "近15日利润走势"}}
        card = build_card(alert, rules)
        payload = str(card)
        for field in ("实际利润", "基线利润", "利润偏差额", "利润偏差率", "正常波动区间", "6 个同星期样本"):
            self.assertIn(field, payload)
        self.assertIn("利润由正转负", payload)
        self.assertNotIn("收入归属部门", payload)
        self.assertNotIn("profit_increase_heading", payload)
        charts = [item for section in card["body"]["elements"]
                  for column in section.get("columns", [])
                  for item in column.get("elements", []) if item.get("tag") == "chart"]
        self.assertEqual(1, len(charts))


class ProfitAlertTrendTest(unittest.TestCase):
    def test_attach_trends_reconciles_both_comparison_days(self) -> None:
        alerts = [{"stable_key": "国内|E1", "企业名称": "企业一",
                   "昨日利润": "26", "对比利润": "25"}]
        document = {
            "status": "SUCCESS", "entity_semantics": "ENTERPRISE_BY_BUSINESS_TYPE",
            "coverage": {"start": "2026-08-12", "end": "2026-08-26", "days": 15},
            "rows": [{"business_type": "国内", "enterprise_code": "E1",
                      "enterprise_name": "企业一",
                      "daily": {f"2026-08-{day:02d}": {"profit": str(day)}
                                for day in range(12, 27)}}],
        }
        attach_trends(alerts, document, "2026-08-26", "2026-08-25", 15)
        self.assertEqual(15, len(alerts[0]["近15日利润走势"]))
        self.assertEqual({"日期": "2026-08-26", "利润": "26"},
                         alerts[0]["近15日利润走势"][-1])


class ProfitAlertDeliveryHelperTest(unittest.TestCase):
    def test_calendar_delivery_context_has_no_single_comparison_day(self) -> None:
        context = delivery_context({"schema_version": "2.0", "current_day": "2026-08-29",
                                    "baseline_method": "SAME_WEEKDAY_MEDIAN_MAD"})
        self.assertEqual({"current_day": "2026-08-29",
                          "baseline_method": "SAME_WEEKDAY_MEDIAN_MAD"}, context)

    def test_calendar_card_contract_accepts_red_alert_charts(self) -> None:
        trend = [{"日期": f"2026-08-{day:02d}", "利润": str(day)} for day in range(15, 30)]
        alert = {"schema_version": "2.0", "status": "SUCCESS", "ruleset_version": "v2",
                 "current_day": "2026-08-29", "alert_count": 1,
                 "observation_count": 0, "watch_count": 0, "unrated_count": 0,
                 "alerts": [{"stable_key": "国内|E1", "business_type": "国内",
                             "enterprise_code": "E1", "企业名称": "企业一",
                             "收入归属部门": ["运营部"], "status": "ALERT_SIGN_FLIP",
                             "利润偏差额": "-1500", "利润偏差率": "-150",
                             "实际利润": "-500", "基线利润": "1000",
                             "正常波动下限": "1000", "正常波动上限": "1000",
                             "baseline_sample_count": 6, "近15日利润走势": trend}],
                 "observations": [], "watchlist": [], "unrated": []}
        rules = {"version": "v2", "baseline": {"minimum_samples": 6,
                 "mad_multiplier_exclusive": "3"},
                 "thresholds": {"amount_exclusive": "200", "rate_percent_exclusive": "10"},
                 "display": {"title": "企业客户利润异常告警", "trend_title": "近15日利润走势",
                             "fields": ["企业名称", "业务类型", "实际利润", "基线利润",
                                        "利润偏差额", "利润偏差率", "正常波动区间", "基线样本"]}}
        card = build_card(alert, rules)
        validate_card_contract(alert, card, rules)

    def test_zero_alert_card_is_allowed_only_for_acceptance(self) -> None:
        alert = {"schema_version": "2.0", "status": "SUCCESS", "ruleset_version": "v2",
                 "current_day": "2026-08-30", "baseline_method": "SAME_WEEKDAY_MEDIAN_MAD",
                 "alert_count": 0, "observation_count": 1, "watch_count": 0,
                 "unrated_count": 2, "alerts": [], "observations": [],
                 "watchlist": [], "unrated": []}
        rules = {"version": "v2", "baseline": {"minimum_samples": 6,
                 "mad_multiplier_exclusive": "3"},
                 "thresholds": {"amount_exclusive": "200", "rate_percent_exclusive": "10"},
                 "display": {"title": "企业客户利润异常告警", "trend_title": "近15日利润走势",
                             "fields": ["企业名称"]},
                 "delivery": {"send_when_no_anomaly": False}}
        card = build_card(alert, rules)
        with self.assertRaisesRegex(RuntimeError, "no publishable"):
            validate_card_contract(alert, card, rules)
        validate_card_contract(alert, card, rules, acceptance_test=True)
        self.assertEqual(
            ["企业客户利润异常告警", "业务告警", "利润上升观察",
             "本次没有满足新规则的业务告警"],
            rendered_readback_markers(alert, rules, acceptance_test=True),
        )

    def test_card_walk_ignores_scalar_values_and_decode_still_parses_json(self) -> None:
        card = {"schema": "2.0", "config": {"update_multi": True},
                "body": {"elements": [{"tag": "markdown", "content": "ok"}]}}
        self.assertEqual(4, len(list(walk(card))))
        self.assertEqual({"ok": True}, decode('{"ok":true}'))

    def test_manual_resend_selects_one_allowlisted_recipient(self) -> None:
        delivery = {
            "acceptance_test_recipient": "寇誉博",
            "recipients": [
                {"name": "金长春", "email": "jinchangchun@yuexin.cn"},
                {"name": "寇誉博", "email": "kouyubo@yuexin.cn"},
            ],
        }
        recipients, mode, suffix = select_delivery(delivery, "金长春", False, True)
        self.assertEqual(["金长春"], [item["name"] for item in recipients])
        self.assertEqual("manual-resend", mode)
        self.assertEqual("manual-resend-金长春", suffix)

    def test_unapproved_single_recipient_mode_is_rejected(self) -> None:
        delivery = {"acceptance_test_recipient": "寇誉博",
                    "recipients": [{"name": "金长春", "email": "jinchangchun@yuexin.cn"}]}
        with self.assertRaisesRegex(RuntimeError, "explicit approved mode"):
            select_delivery(delivery, "金长春", False, False)

    def test_existing_acceptance_and_production_modes_are_preserved(self) -> None:
        delivery = {"acceptance_test_recipient": "寇誉博", "recipients": [
            {"name": "金长春", "email": "jinchangchun@yuexin.cn"},
            {"name": "寇誉博", "email": "kouyubo@yuexin.cn"},
        ]}
        acceptance, mode, suffix = select_delivery(delivery, "寇誉博", True, False)
        self.assertEqual((["寇誉博"], "acceptance-test", "acceptance-test"),
                         ([item["name"] for item in acceptance], mode, suffix))
        production, mode, suffix = select_delivery(delivery, None, False, False)
        self.assertEqual((["金长春", "寇誉博"], "production", "production"),
                         ([item["name"] for item in production], mode, suffix))

    def test_sender_identity_requires_configured_app_and_name(self) -> None:
        from scripts import send_enterprise_profit_alert_card as sender
        self.assertTrue(hasattr(sender, "validate_sender_identity"))
        rules = {"delivery": {"sender_app_id": "cli_expected", "sender_name": "业财告警助手"}}
        sender.validate_sender_identity({"id": "cli_expected", "name": "业财告警助手"}, rules)
        with self.assertRaisesRegex(RuntimeError, "sender identity mismatch"):
            sender.validate_sender_identity({"id": "cli_other", "name": "业财告警助手"}, rules)
        with self.assertRaisesRegex(RuntimeError, "sender identity mismatch"):
            sender.validate_sender_identity({"id": "cli_expected", "name": "旧名称"}, rules)

    def test_approved_rules_are_allowed_only_for_acceptance(self) -> None:
        from scripts import send_enterprise_profit_alert_card as sender
        original_root = sender.ROOT
        with TemporaryDirectory() as directory:
            root = Path(directory)
            rules_dir = root / "config/enterprise-profit-alert-rules"
            rules_dir.mkdir(parents=True)
            rules_path = rules_dir / "v2.json"
            rules_path.write_text(json.dumps({"version": "v2", "status": "APPROVED"}),
                                  encoding="utf-8")
            (rules_dir / "active.json").write_text(json.dumps({}), encoding="utf-8")
            sender.ROOT = root
            try:
                try:
                    loaded, loaded_path, _ = sender.load_rules(rules_path, acceptance_test=True)
                except RuntimeError as exc:
                    self.fail(f"approved acceptance rules rejected: {exc}")
                self.assertEqual("v2", loaded["version"])
                self.assertEqual(rules_path.resolve(), loaded_path)
                with self.assertRaisesRegex(RuntimeError, "only for acceptance"):
                    sender.load_rules(rules_path, acceptance_test=False)
            finally:
                sender.ROOT = original_root


class ProfitAlertHealthEntrypointTest(unittest.TestCase):
    def load_module(self, filename: str):
        path = Path(__file__).with_name(filename)
        self.assertTrue(path.is_file(), f"missing entrypoint: {filename}")
        return importlib.import_module(f"scripts.{path.stem}")

    def test_governance_rejects_active_pointer_to_candidate_rules(self) -> None:
        validator = self.load_module("validate_enterprise_profit_alert_governance.py")
        with TemporaryDirectory() as directory:
            root = Path(directory)
            rules_dir = root / "config/enterprise-profit-alert-rules"
            rules_dir.mkdir(parents=True)
            rules = {"schema_version": "2.0", "ruleset_id": "enterprise-profit-alert",
                     "version": "v1", "status": "CANDIDATE"}
            rules_path = rules_dir / "v1.json"
            rules_path.write_text(json.dumps(rules), encoding="utf-8")
            digest = __import__("hashlib").sha256(rules_path.read_bytes()).hexdigest()
            (rules_dir / "active.json").write_text(json.dumps({
                "status": "ACTIVE", "active_version": "v1",
                "ruleset_path": "config/enterprise-profit-alert-rules/v1.json",
                "ruleset_sha256": digest,
            }), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "approved immutable ruleset"):
                validator.validate_active(root)

    def test_active_delivery_allowlist_includes_current_recipients(self) -> None:
        validator = self.load_module("validate_enterprise_profit_alert_governance.py")
        _, _, rules = validator.validate_active()
        self.assertEqual(
            ["金长春", "寇誉博", "李童晶", "刘嘉玲"],
            [item["name"] for item in rules["delivery"]["recipients"]],
        )

    def test_active_rules_forbid_department_and_legacy_gateway(self) -> None:
        validator = self.load_module("validate_enterprise_profit_alert_governance.py")
        _, _, rules = validator.validate_active()
        self.assertEqual("FORBIDDEN", rules["entity"]["department_dependency"])
        self.assertEqual("FORBIDDEN", rules["entity"]["department_display"])
        self.assertNotIn("收入归属部门", rules["display"]["fields"])
        gateway = rules["sources"]["international"]["gateway"]
        self.assertEqual("https://flux-admin.yuexin.cn/mcp/", gateway["endpoint"])
        self.assertEqual(
            "yuexin_mcp_gateway-billing_mcp__query_daily_stats_summary",
            gateway["remote_tool"],
        )
        self.assertEqual("FORBIDDEN", gateway["legacy_oauth_gateway"])

    def test_daily_runner_zero_alert_terminal_state_is_no_send(self) -> None:
        runner = self.load_module("run_enterprise_profit_alert_daily.py")
        self.assertEqual(
            {"status": "SUCCESS", "delivery_status": "NO_SEND_NO_BUSINESS_ALERT",
             "message_count": 0},
            runner.delivery_decision({"status": "SUCCESS", "alert_count": 0}),
        )

    def test_daily_runner_dry_run_never_attempts_delivery(self) -> None:
        runner = self.load_module("run_enterprise_profit_alert_daily.py")
        self.assertEqual(
            {
                "status": "SUCCESS",
                "delivery_status": "DRY_RUN_NOT_SENT",
                "message_count": 0,
                "send_attempted": False,
                "would_send": True,
            },
            runner.dry_run_decision({"status": "SUCCESS", "alert_count": 1}),
        )

    def test_daily_runner_accepts_continuous_recovery_receipts(self) -> None:
        runner = self.load_module("run_enterprise_profit_alert_daily.py")
        receipts = [
            (Path(f"day-{day}.json"), {"start_time": f"2026-09-{day}T00:00:00+08:00"})
            for day in ("05", "06", "07")
        ]
        self.assertEqual(
            ["2026-09-05", "2026-09-06", "2026-09-07"],
            runner.validate_recovery_receipt_days(
                {"status": "SUCCESS", "coverage": {"end": "2026-09-04"}},
                "2026-09-07",
                receipts,
            ),
        )

    def test_daily_runner_rejects_recovery_receipt_gap(self) -> None:
        runner = self.load_module("run_enterprise_profit_alert_daily.py")
        receipts = [
            (Path("day-05.json"), {"start_time": "2026-09-05T00:00:00+08:00"}),
            (Path("day-07.json"), {"start_time": "2026-09-07T00:00:00+08:00"}),
        ]
        with self.assertRaisesRegex(RuntimeError, "not continuous"):
            runner.validate_recovery_receipt_days(
                {"status": "SUCCESS", "coverage": {"end": "2026-09-04"}},
                "2026-09-07",
                receipts,
            )

    def test_dry_run_receipt_rejects_any_send_attempt(self) -> None:
        validator = self.load_module("validate_enterprise_profit_alert_governance.py")
        with self.assertRaisesRegex(RuntimeError, "dry-run attempted message delivery"):
            validator.validate_delivery_receipt(
                {
                    "status": "SUCCESS",
                    "delivery_status": "DRY_RUN_NOT_SENT",
                    "message_count": 1,
                    "send_attempted": True,
                    "alert_count": 1,
                },
                alert_count=1,
                delivery_mode="dry-run",
            )


if __name__ == "__main__":
    unittest.main()
