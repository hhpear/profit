#!/usr/bin/env python3
"""Fetch exact OldSMS enterprise NameSpell values through governed LiteLLM MCP."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.request
from pathlib import Path

ENDPOINT = "https://flux-admin.yuexin.cn/mcp/"
TOOL_SUFFIX = "account_provision__query_enterprise"

def decode_response(response):
    raw = response.read().decode("utf-8")
    if not raw.strip():
        return {}
    if "text/event-stream" in response.headers.get("Content-Type", "") or raw.startswith("event:"):
        raw = "\n".join(line[6:] for line in raw.splitlines() if line.startswith("data: "))
    value = json.loads(raw)
    if not isinstance(value, dict):
        raise RuntimeError("LiteLLM MCP response is not an object")
    return value

def post(key: str, payload: dict, session_id: str | None = None):
    headers = {"Accept": "application/json, text/event-stream", "Content-Type": "application/json",
               "x-litellm-api-key": f"Bearer {key}"}
    if session_id:
        headers["Mcp-Session-Id"] = session_id
    request = urllib.request.Request(ENDPOINT, data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                                     headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=120) as response:
        return decode_response(response), response.headers.get("Mcp-Session-Id") or session_id

def extract_payload(value: object) -> dict:
    if isinstance(value, str):
        try:
            return extract_payload(json.loads(value))
        except json.JSONDecodeError as exc:
            raise RuntimeError("Invalid MCP JSON payload") from exc
    if isinstance(value, dict):
        if value.get("isError") or value.get("error") or value.get("ok") is False:
            raise RuntimeError("Enterprise MCP query failed")
        if "code" in value:
            if value["code"] != 0:
                raise RuntimeError("Enterprise MCP business query failed")
            if isinstance(value.get("data"), dict):
                return value
        for key in ("result", "structuredContent", "body", "content", "text"):
            if key in value:
                return extract_payload(value[key])
    if isinstance(value, list) and len(value) == 1:
        return extract_payload(value[0])
    raise RuntimeError("OldSMS enterprise response has no unique structured payload")


def fetch_master(codes, auth_path=None, _discovery_retries=2):
    """Only MCP tools/list + tools/call; never business HTTP or cached data."""
    auth_path = auth_path or Path.home() / ".codex/auth.json"
    auth = json.loads(auth_path.read_text(encoding="utf-8"))
    key = str(auth.get("OPENAI_API_KEY") or "").strip()
    if not key:
        raise RuntimeError("LiteLLM MCP API key is missing")
    codes = sorted(set(str(code).strip() for code in codes if str(code).strip()))
    if not codes:
        raise RuntimeError("enterprise code list is empty")
    initialized, session = post(key, {"jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                   "clientInfo": {"name": "kanban-enterprise-master", "version": "2.0"}}})
    if not session or "result" not in initialized or initialized.get("error"):
        raise RuntimeError("LiteLLM MCP initialize failed")
    post(key, {"jsonrpc": "2.0", "method": "notifications/initialized"}, session)
    names = set(); cursor = None; seen = set(); request_id = 2
    while True:
        response, _ = post(key, {"jsonrpc": "2.0", "id": request_id, "method": "tools/list",
            "params": {} if cursor is None else {"cursor": cursor}}, session)
        request_id += 1
        if response.get("error") or "result" not in response:
            raise RuntimeError("Enterprise MCP tool discovery failed")
        result = response["result"]
        names.update(t["name"] for t in result.get("tools", [])
                     if t.get("name") == TOOL_SUFFIX or t.get("name", "").endswith("-" + TOOL_SUFFIX))
        cursor = result.get("nextCursor")
        if not cursor:
            break
        if cursor in seen:
            raise RuntimeError("Enterprise MCP discovery pagination stalled")
        seen.add(cursor)
    if not names and _discovery_retries:
        time.sleep(2)
        return fetch_master(codes, auth_path, _discovery_retries - 1)
    if len(names) != 1:
        raise RuntimeError("Enterprise MCP tool missing or ambiguous")
    tool_name = names.pop(); rows = []
    for index, code in enumerate(codes):
        if index:
            time.sleep(1)
        payload = None
        for attempt in range(3):
            response, _ = post(key, {"jsonrpc": "2.0", "id": request_id, "method": "tools/call",
                "params": {"name": tool_name, "arguments": {"code": code}}}, session)
            request_id += 1
            try:
                payload = extract_payload(response)
                break
            except RuntimeError as exc:
                if attempt == 2:
                    if len(codes) > 1:
                        # Refresh the same MCP identity/session; never change data source.
                        isolated = fetch_master([code], auth_path)
                        rows.extend(isolated["rows"])
                        break
                    raise RuntimeError(f"Enterprise MCP query failed for exact code {code}") from exc
                time.sleep(attempt + 1)
        if payload is None:
            continue
        data = payload["data"]; matches = data.get("rows", [])
        if data.get("total") != 1 or len(matches) != 1 or str(matches[0].get("Code")) != code:
            raise RuntimeError(f"Enterprise MCP exact match incomplete or ambiguous: {code}")
        row = matches[0]
        if not str(row.get("Name") or "").strip():
            raise RuntimeError(f"Enterprise MCP name missing: {code}")
        rows.append({"enterprise_code": code, "enterprise_name": row["Name"],
                     "enterprise_type": row.get("enterpriseType"),
                     "enterprise_short_code": str(row.get("NameSpell") or "").strip()})
    from datetime import datetime, timezone
    return {"schema_version": "1.0", "status": "SUCCESS", "source_system": "OldSMS",
            "source_transport": "MCP", "source_tool": tool_name, "stable_key": "enterprise_code",
            "display_field": "nameSpell", "queried_at": datetime.now(timezone.utc).isoformat(),
            "query_arguments": {"codes": codes}, "fallback_used": False,
            "coverage": {"requested": len(codes), "resolved": len(rows)}, "rows": rows}

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codes", type=Path, required=True)
    parser.add_argument("--facts", type=Path, help="Deprecated; names never used for lookup")
    parser.add_argument("--auth", type=Path, default=Path.home() / ".codex/auth.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    codes = args.codes.read_text(encoding="utf-8").splitlines()
    artifact = fetch_master(codes, args.auth)
    rows = artifact["rows"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(f".{args.output.name}.tmp")
    temporary.write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(args.output)
    print(json.dumps({"status": "SUCCESS", "rows": len(rows), "output": str(args.output)}, ensure_ascii=False))

if __name__ == "__main__":
    main()
