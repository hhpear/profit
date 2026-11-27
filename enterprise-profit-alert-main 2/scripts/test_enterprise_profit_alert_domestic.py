"""Exercise the actual isolated query path with a recording BMP transport."""
import json
import copy
import subprocess
import sys
import unittest
from unittest.mock import patch

from scripts.query_enterprise_profit_alert_domestic import CUSTOMER_TYPES, DETAIL, query
from scripts.run_enterprise_profit_alert_daily import DOMESTIC_QUERY, DOMESTIC_DETAIL_QUERY
from scripts.build_enterprise_profit_alert import domestic_day


class IsolatedDomesticTest(unittest.TestCase):
    def test_current_bmp_customer_types_are_used(self):
        self.assertEqual(["Customer", "QuasiCustomer", "Agent", "CHANNEL"], CUSTOMER_TYPES)

    def test_bmp_scope_rejection_keeps_source_error(self):
        with self.assertRaisesRegex(RuntimeError, "客户类型不在"):
            query("2026-10-14", "2026-10-14", lambda *_: {
                "success": False, "code": None, "data": None, "totalCount": None,
                "message": "客户类型不在利润报表和首页统计范围内，请刷新筛选条件"})

    def test_candidate_cannot_run_in_production(self):
        result = subprocess.run([
            sys.executable, str(DOMESTIC_QUERY.with_name("run_enterprise_profit_alert_daily.py")),
            "--current-day", "2026-11-05", "--output-dir", "/not-used",
            "--candidate-rules", "/not-used.json"], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("candidate rules require dry-run", result.stderr)

    def test_customer_scope_candidate_rejects_unrelated_threshold_change(self):
        from scripts import validate_enterprise_profit_alert_governance as governance
        _, active_path, active = governance.validate_active()
        candidate = copy.deepcopy(active)
        candidate.update(version="test-scope", supersedes=active["version"], change_reason="test")
        candidate["sources"]["domestic"]["customer_types"] = list(CUSTOMER_TYPES)
        candidate["thresholds"]["amount_exclusive"] = "1"
        path = active_path.parent / "test-scope.json"
        with patch.object(governance, "read", return_value=candidate), \
                patch.object(governance, "validate_active", return_value=({}, active_path, active)):
            with self.assertRaisesRegex(RuntimeError, "exceed the authorized customer scope"):
                governance.validate_candidate(path)

    def test_runner_uses_only_isolated_helper(self):
        self.assertEqual(DOMESTIC_QUERY, DOMESTIC_DETAIL_QUERY)
        self.assertEqual("query_enterprise_profit_alert_domestic.py", DOMESTIC_QUERY.name)

    def source(self, **changes):
        return {"timeStr": "2026-10-14", "entCode": "E1", "entName": "企业一",
                "smsIncome": "100", "smsProfit": "20", "belongDepartment": "FORBIDDEN", **changes}

    @patch("builtins.print")
    def test_full_pagination_calls_only_profit_endpoint_and_omits_departments(self, _):
        calls = []
        def request(path, payload):
            calls.append((path, payload))
            return {"data": [self.source()], "totalCount": 2}
        doc = query("2026-10-14", "2026-10-14", request, page_size=1, current_detail=True)
        self.assertEqual([DETAIL, DETAIL], [path for path, _ in calls])
        self.assertEqual([1, 2], [body["page"]["pageNumber"] for _, body in calls])
        self.assertEqual(CUSTOMER_TYPES, calls[0][1]["customerTypes"])
        self.assertNotIn("department", json.dumps(doc).lower())
        self.assertNotIn("department", json.dumps(calls).lower())
        rows, _ = domestic_day(doc, "2026-10-14")
        self.assertEqual("40", str(rows["E1"]["profit"]))

    @patch("builtins.print")
    def test_missing_money_and_nonzero_identity_fail(self, _):
        for row in (self.source(smsProfit=None), self.source(entCode=None)):
            with self.subTest(row=row), self.assertRaises(RuntimeError):
                query("2026-10-14", "2026-10-14", lambda *_: {"data": [row], "totalCount": 1})

    @patch("builtins.print")
    def test_incomplete_pagination_fails(self, _):
        with self.assertRaisesRegex(RuntimeError, "ended early"):
            query("2026-10-14", "2026-10-14", lambda *_: {"data": [], "totalCount": 1})

    @patch("builtins.print")
    def test_out_of_range_date_fails(self, _):
        with self.assertRaises(RuntimeError):
            query("2026-10-14", "2026-10-14", lambda *_: {
                "data": [self.source(timeStr="2026-10-13")], "totalCount": 1})


if __name__ == "__main__":
    unittest.main()
