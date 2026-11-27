#!/usr/bin/env python3
"""Create immutable enterprise-profit alert rules 2026-11-06.1."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "config/enterprise-profit-alert-rules/2026-11-05.1.json"
TARGET = ROOT / "config/enterprise-profit-alert-rules/2026-11-06.1.json"


def main() -> None:
    if TARGET.exists():
        raise RuntimeError(f"immutable ruleset already exists: {TARGET}")
    rules = json.loads(SOURCE.read_text(encoding="utf-8"))
    rules["version"] = "2026-11-06.1"
    rules["status"] = "APPROVED"
    rules["supersedes"] = "2026-11-05.1"
    rules["change_reason"] = "同步 BMP 客户利润表最新客户类型范围：以 QuasiCustomer 替换 Internet"
    rules["sources"]["domestic"]["customer_types"] = [
        "Customer", "QuasiCustomer", "Agent", "CHANNEL"
    ]
    TARGET.write_text(json.dumps(rules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "output": str(TARGET)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
