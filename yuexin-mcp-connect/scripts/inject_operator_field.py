#!/usr/bin/env python3
"""给 export-catalog 导出的 tools-catalog.json 注入 _operator_open_id 字段。

mcp_gateway_server.py 在每次 tools/call 时要求 arguments 里带
`_operator_open_id`（当前操作人的飞书 open_id），并在转发到下游工具前
剥离该字段。但下游工具自身的 inputSchema 不含这个字段，`export-catalog`
直接拉取原始 schema，LLM 看到 schema 就不敢/不能传它（尤其当
`additionalProperties: false` 时）。

本脚本给每个工具 schema 打补丁：
1. properties 加入 `_operator_open_id`（string, 必填）
2. required 列表加入 `_operator_open_id`
3. description 前缀加一行提醒
4. `additionalProperties: false` 改为 true（避免 strict schema 校验副作用）

用法：
    python inject_operator_field.py <catalog.json>
    # 原地写回，同时备份为 <catalog>.bak.<timestamp>
"""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path

OPERATOR_FIELD = "_operator_open_id"

OPERATOR_FIELD_SCHEMA = {
    "type": "string",
    "description": (
        "当前会话发送者的飞书 open_id（形如 ou_xxxxxxxxxxxxxxxxxxxxxxxx）。"
        "从飞书群消息的 sender.sender_id.open_id 字段直接取用。"
        "Server 会剥离此字段，不会传给下游工具。"
    ),
    "pattern": r"^ou_[A-Za-z0-9]+$",
}

DESC_PREFIX = (
    "⚠️ 调用前必读：arguments 必须包含 _operator_open_id（当前会话发送者的飞书 open_id）。"
    "Server 会剥离该字段再转发。\n\n"
)


def patch_tool(tool: dict) -> bool:
    schema = tool.get("inputSchema")
    if not isinstance(schema, dict):
        return False

    props = schema.setdefault("properties", {})
    if not isinstance(props, dict):
        return False

    changed = False

    if OPERATOR_FIELD not in props:
        props[OPERATOR_FIELD] = OPERATOR_FIELD_SCHEMA
        changed = True

    required = schema.setdefault("required", [])
    if not isinstance(required, list):
        required = []
        schema["required"] = required
    if OPERATOR_FIELD not in required:
        required.append(OPERATOR_FIELD)
        changed = True

    if schema.get("additionalProperties") is False:
        schema["additionalProperties"] = True
        changed = True

    desc = tool.get("description", "") or ""
    if not desc.startswith("⚠️ 调用前必读"):
        tool["description"] = DESC_PREFIX + desc
        changed = True

    return changed


def main() -> None:
    if len(sys.argv) != 2:
        print("usage: inject_operator_field.py <catalog.json>", file=sys.stderr)
        sys.exit(2)

    path = Path(sys.argv[1])
    if not path.exists():
        print(f"file not found: {path}", file=sys.stderr)
        sys.exit(1)

    data = json.loads(path.read_text())
    if not isinstance(data, list):
        print("catalog root is not a list", file=sys.stderr)
        sys.exit(1)

    backup = path.with_suffix(path.suffix + f".bak.{int(time.time())}")
    backup.write_text(json.dumps(data, ensure_ascii=False, indent=2))

    patched = 0
    for tool in data:
        if isinstance(tool, dict) and patch_tool(tool):
            patched += 1

    path.write_text(json.dumps(data, ensure_ascii=False, indent=2))

    print(f"patched {patched}/{len(data)} tools")
    print(f"backup: {backup}")


if __name__ == "__main__":
    main()
