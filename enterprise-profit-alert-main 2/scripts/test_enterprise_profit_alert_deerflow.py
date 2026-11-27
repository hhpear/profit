#!/usr/bin/env python3
"""Tests for the narrow DeerFlow enterprise-profit alert MCP adapter."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = (Path(__file__).resolve().parents[1]
               / "integrations/deerflow/enterprise_profit_alert_mcp.py")
SPEC = importlib.util.spec_from_file_location("enterprise_profit_alert_mcp", MODULE_PATH)
assert SPEC and SPEC.loader
adapter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(adapter)


class DeerFlowProfitAlertAdapterTest(unittest.TestCase):
    def test_registered_snapshot_is_hash_pinned_and_exact_day(self) -> None:
        path, snapshot = adapter.resolve_snapshot("2026-08-31")
        self.assertTrue(path.is_file())
        self.assertEqual(snapshot["history_sha256"], adapter.sha256(path))

    def test_unregistered_day_fails_closed(self) -> None:
        with self.assertRaisesRegex(ValueError, "no governed complete-day snapshot"):
            adapter.resolve_snapshot("2026-09-01")

    def test_execution_id_cannot_escape_output_root(self) -> None:
        with self.assertRaisesRegex(ValueError, "execution_id"):
            adapter.resolve_output_dir("../outside")

    def test_runner_command_is_unconditionally_dry_run(self) -> None:
        command = adapter.build_runner_command(
            "2026-08-31", Path("history.json"), Path("output"))
        self.assertIn("--dry-run", command)
        self.assertNotIn("send_enterprise_profit_alert_card.py", " ".join(command))


if __name__ == "__main__":
    unittest.main()
