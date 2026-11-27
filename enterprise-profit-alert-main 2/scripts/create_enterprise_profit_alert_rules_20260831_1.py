#!/usr/bin/env python3
"""Create immutable enterprise-profit alert rules 2026-08-31.1."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "config/enterprise-profit-alert-rules/2026-08-30.1.json"
TARGET = ROOT / "config/enterprise-profit-alert-rules/2026-08-31.1.json"


def main() -> None:
    if TARGET.exists():
        raise RuntimeError(f"immutable ruleset already exists: {TARGET}")
    rules = json.loads(SOURCE.read_text(encoding="utf-8"))
    rules["version"] = "2026-08-31.1"
    rules["status"] = "APPROVED"
    rules["supersedes"] = "2026-08-30.1"
    rules["change_reason"] = "修正规则生命周期、算法元数据与机器人身份审计契约，不改变业务阈值"
    rules["delivery"]["sender_name"] = "业财告警助手"
    rules["delivery"]["sender_app_id"] = "cli_aaa38ddf53f8dbcf"
    TARGET.write_text(json.dumps(rules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "output": str(TARGET)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
