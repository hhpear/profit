#!/usr/bin/env python3
"""CLI for standardizing Yuexin MCP Gateway auth and tool calls."""

from __future__ import annotations

import sys

if sys.version_info < (3, 11):
    sys.stderr.write(
        f"mcp_gateway_client.py requires Python >= 3.11, got "
        f"{sys.version_info.major}.{sys.version_info.minor}. "
        f"Please rerun with python3.11 or newer.\n"
    )
    sys.exit(2)

import argparse
import json
import os
import re
import socket
import stat
import tempfile
import threading
import time
import webbrowser
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, quote, urlencode, urlparse
from urllib.request import Request, urlopen

DEFAULT_TIMEOUT_SECONDS = 15.0
DEFAULT_SOURCE = "agent"
DEFAULT_MCP_TTL_SECONDS = 3600
DEFAULT_OPERATOR_TTL_SECONDS = 60
DEFAULT_GATEWAY_BASE_URL = "http://127.0.0.1:8000"
DEFAULT_FEISHU_API_BASE_URL = "https://open.feishu.cn/open-apis"
DEFAULT_LOCAL_FEISHU_API_BASE_URL = "http://127.0.0.1:18080/open-apis"
DEFAULT_OPENCLAW_USER_FILE = Path.home() / ".openclaw" / "workspace" / "USER.md"
DEFAULT_OPENCLAW_CONFIG_FILE = Path.home() / ".openclaw" / "openclaw.json"
DEFAULT_CREDENTIALS_FILE = Path.home() / ".mcp-gateway" / "credentials.json"
OAUTH_LOGIN_TIMEOUT_SECONDS = 120

COMMON_OPEN_ID_PATHS: tuple[tuple[str, ...], ...] = (
    ("open_id",),
    ("openId",),
    ("feishu_open_id",),
    ("feishuOpenId",),
    ("operator", "open_id"),
    ("operator", "openId"),
    ("operator", "feishu_open_id"),
    ("operator", "feishuOpenId"),
    ("user", "open_id"),
    ("user", "openId"),
    ("user", "feishu_open_id"),
    ("user", "feishuOpenId"),
)

OPENCLAW_USER_OPEN_ID_PATTERN = re.compile(
    r"Feishu Open ID:\**\s*(?P<open_id>ou_[A-Za-z0-9_]+)",
    re.IGNORECASE,
)


class UsageError(RuntimeError):
    """Raised when required user input is missing or malformed."""


@dataclass(slots=True)
class ApiError(RuntimeError):
    """Raised when an HTTP request fails."""

    path: str
    status: int | None
    body: Any
    headers: dict[str, str]
    message: str


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        args.handler(args)
    except UsageError as exc:
        emit_json(
            {
                "ok": False,
                "error": "usage_error",
                "message": str(exc),
            },
            pretty=True,
        )
        return 2
    except ApiError as exc:
        emit_json(
            {
                "ok": False,
                "error": "api_error",
                "message": exc.message,
                "path": exc.path,
                "status": exc.status,
                "headers": exc.headers,
                "body": exc.body,
            },
            pretty=True,
        )
        return 1
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Standardized CLI for Yuexin MCP Gateway auth and tool flows."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    resolve_open_id_parser = subparsers.add_parser(
        "resolve-open-id",
        aliases=["resolve-feishu-id"],
        help="Resolve the current operator open_id from flags, context, OpenClaw, or env.",
    )
    add_open_id_args(resolve_open_id_parser)
    add_pretty_arg(resolve_open_id_parser)
    resolve_open_id_parser.set_defaults(handler=handle_resolve_open_id)

    resolve_union_id_parser = subparsers.add_parser(
        "resolve-union-id",
        help="Resolve union_id from open_id via Feishu OpenAPI.",
    )
    add_open_id_args(resolve_union_id_parser)
    add_union_id_args(resolve_union_id_parser)
    add_feishu_args(resolve_union_id_parser)
    add_gateway_args(resolve_union_id_parser, include_base_url=False)
    add_pretty_arg(resolve_union_id_parser)
    resolve_union_id_parser.set_defaults(handler=handle_resolve_union_id)

    bootstrap_parser = subparsers.add_parser(
        "bootstrap",
        help="Resolve session, issue mcp token, and issue operator token.",
    )
    add_gateway_args(bootstrap_parser, include_base_url=True)
    add_open_id_args(bootstrap_parser)
    add_union_id_args(bootstrap_parser)
    add_feishu_args(bootstrap_parser)
    bootstrap_parser.add_argument("--conversation-id", required=True)
    bootstrap_parser.add_argument(
        "--conversation-type",
        required=True,
        choices=("single", "group", "topic"),
    )
    bootstrap_parser.add_argument("--task-key", required=True)
    bootstrap_parser.add_argument("--owner-user-id")
    bootstrap_parser.add_argument("--source", default=DEFAULT_SOURCE)
    bootstrap_parser.add_argument(
        "--mcp-ttl-seconds",
        type=int,
        default=DEFAULT_MCP_TTL_SECONDS,
    )
    bootstrap_parser.add_argument(
        "--operator-ttl-seconds",
        type=int,
        default=DEFAULT_OPERATOR_TTL_SECONDS,
    )
    bootstrap_parser.add_argument("--bundle-file", type=Path)
    add_pretty_arg(bootstrap_parser)
    bootstrap_parser.set_defaults(handler=handle_bootstrap)

    refresh_parser = subparsers.add_parser(
        "refresh-operator-token",
        help="Refresh the operator token inside an existing bundle file.",
    )
    refresh_parser.add_argument("--bundle-file", type=Path, required=True)
    refresh_parser.add_argument("--source")
    refresh_parser.add_argument(
        "--operator-ttl-seconds",
        type=int,
        default=DEFAULT_OPERATOR_TTL_SECONDS,
    )
    refresh_parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
    )
    add_pretty_arg(refresh_parser)
    refresh_parser.set_defaults(handler=handle_refresh_operator_token)

    list_parser = subparsers.add_parser(
        "list-tools",
        help="Call /mcp tools/list using a saved bundle file.",
    )
    list_parser.add_argument("--bundle-file", type=Path, required=True)
    list_parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
    )
    add_pretty_arg(list_parser)
    list_parser.set_defaults(handler=handle_list_tools)

    call_parser = subparsers.add_parser(
        "call-tool",
        help="Call /mcp tools/call using a saved bundle file.",
    )
    call_parser.add_argument("--bundle-file", type=Path, required=True)
    call_parser.add_argument("--tool-name", required=True)
    call_parser.add_argument(
        "--arguments",
        default="{}",
        help="JSON object string for tool arguments.",
    )
    call_parser.add_argument(
        "--arguments-file",
        type=Path,
        help="Path to a JSON object file for tool arguments.",
    )
    call_parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
    )
    add_pretty_arg(call_parser)
    call_parser.set_defaults(handler=handle_call_tool)

    export_catalog_parser = subparsers.add_parser(
        "export-catalog",
        help="Export tool definitions from tools/list to a catalog JSON file.",
    )
    export_catalog_parser.add_argument("--bundle-file", type=Path, required=True)
    export_catalog_parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Output path for the tools catalog JSON file.",
    )
    export_catalog_parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
    )
    export_catalog_parser.set_defaults(handler=handle_export_catalog)

    login_parser = subparsers.add_parser(
        "login",
        help="Feishu OAuth 2.0 personal login — saves credentials to ~/.mcp-gateway/credentials.json.",
    )
    login_parser.add_argument(
        "--base-url",
        help="Gateway base URL. Falls back to MCP_GATEWAY_BASE_URL.",
    )
    login_parser.add_argument(
        "--task-key",
        required=True,
        help="Stable task identifier (reused across sessions for the same task).",
    )
    login_parser.add_argument(
        "--conversation-id",
        help="Conversation ID. Defaults to oauth-{uuid}.",
    )
    login_parser.add_argument(
        "--conversation-type",
        choices=("single", "group", "topic"),
        default="single",
    )
    login_parser.add_argument(
        "--port",
        type=int,
        default=0,
        help="Local callback port (0 = random).",
    )
    login_parser.add_argument(
        "--credentials-file",
        type=Path,
        default=DEFAULT_CREDENTIALS_FILE,
        help=f"Output path for credentials JSON. Default: {DEFAULT_CREDENTIALS_FILE}",
    )
    login_parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=float(OAUTH_LOGIN_TIMEOUT_SECONDS),
    )
    login_parser.set_defaults(handler=handle_login)

    return parser


def add_gateway_args(parser: argparse.ArgumentParser, *, include_base_url: bool) -> None:
    if include_base_url:
        parser.add_argument(
            "--base-url",
            help="Gateway base URL. Falls back to MCP_GATEWAY_BASE_URL, then local debug default.",
        )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
    )


def add_open_id_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--open-id", dest="open_id")
    parser.add_argument("--feishu-id", dest="open_id", help=argparse.SUPPRESS)
    parser.add_argument(
        "--context-file",
        type=Path,
        help="Optional JSON file used to resolve open_id by common field paths.",
    )
    parser.add_argument(
        "--user-file",
        type=Path,
        help="Optional OpenClaw USER.md path. Defaults to ~/.openclaw/workspace/USER.md.",
    )


def add_union_id_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--union-id")


def add_feishu_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--feishu-api-base-url",
        help=(
            "Feishu OpenAPI base URL. Falls back to MCP_GATEWAY_FEISHU_API_BASE_URL, "
            "then local debug or official default."
        ),
    )
    parser.add_argument("--feishu-app-id")
    parser.add_argument("--feishu-app-secret")


def add_pretty_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--pretty", action="store_true")


def handle_resolve_open_id(args: argparse.Namespace) -> None:
    resolution = resolve_open_id(
        explicit_open_id=args.open_id,
        context_file=args.context_file,
        user_file=args.user_file,
    )
    emit_json(
        {
            "ok": True,
            "open_id": resolution["open_id"],
            "source": resolution["source"],
        },
        pretty=args.pretty,
    )


def handle_resolve_union_id(args: argparse.Namespace) -> None:
    resolution = resolve_union_identity(
        explicit_union_id=args.union_id,
        explicit_open_id=args.open_id,
        context_file=args.context_file,
        user_file=args.user_file,
        feishu_api_base_url=args.feishu_api_base_url,
        feishu_app_id=args.feishu_app_id,
        feishu_app_secret=args.feishu_app_secret,
        gateway_base_url=normalize_str(os.getenv("MCP_GATEWAY_BASE_URL")),
        timeout_seconds=args.timeout_seconds,
    )
    emit_json({"ok": True, **resolution}, pretty=args.pretty)


def handle_bootstrap(args: argparse.Namespace) -> None:
    base_url = resolve_gateway_base_url(args.base_url)
    identity = resolve_union_identity(
        explicit_union_id=args.union_id,
        explicit_open_id=args.open_id,
        context_file=args.context_file,
        user_file=args.user_file,
        feishu_api_base_url=args.feishu_api_base_url,
        feishu_app_id=args.feishu_app_id,
        feishu_app_secret=args.feishu_app_secret,
        gateway_base_url=base_url,
        timeout_seconds=args.timeout_seconds,
    )
    conversation = {
        "conversation_id": args.conversation_id,
        "conversation_type": args.conversation_type,
    }

    resolve_payload: dict[str, Any] = {
        "union_id": identity["union_id"],
        **conversation,
        "task_key": args.task_key,
    }
    if args.owner_user_id:
        resolve_payload["owner_user_id"] = args.owner_user_id

    resolved = post_json(
        base_url=base_url,
        path="/internal/agent-sessions/resolve",
        payload=resolve_payload,
        timeout_seconds=args.timeout_seconds,
    )
    resolve_body = expect_dict_body(resolved, "/internal/agent-sessions/resolve")
    operator = expect_dict(resolve_body, "operator")
    session = expect_dict(resolve_body, "session")
    owner_user_id = require_non_empty_str(session.get("owner_user_id"), "session.owner_user_id")

    mcp_response = post_json(
        base_url=base_url,
        path="/internal/agent-sessions/mcp/token",
        payload={
            "agent_session_id": require_non_empty_str(
                session.get("agent_session_id"),
                "session.agent_session_id",
            ),
            **conversation,
            "owner_user_id": owner_user_id,
            "ttl_seconds": args.mcp_ttl_seconds,
        },
        timeout_seconds=args.timeout_seconds,
    )
    mcp_body = expect_dict_body(mcp_response, "/internal/agent-sessions/mcp/token")

    operator_response = post_json(
        base_url=base_url,
        path="/internal/jwt/operator-token",
        payload={
            "source": args.source,
            "operator": operator,
            "conversation": conversation,
            "expires_in": args.operator_ttl_seconds,
        },
        timeout_seconds=args.timeout_seconds,
    )
    operator_body = expect_dict_body(operator_response, "/internal/jwt/operator-token")

    bundle: dict[str, Any] = {
        "gateway": {"base_url": base_url},
        "identity": {
            "union_id": identity["union_id"],
            "union_id_source": identity["union_id_source"],
            "operator": operator,
        },
        "conversation": {
            **conversation,
            "task_key": args.task_key,
            "owner_user_id": owner_user_id,
        },
        "session": session,
        "mcp_session": mcp_body,
        "operator_token": operator_body,
        "metadata": {
            "source": args.source,
            "bootstrapped_at": utc_now_iso(),
        },
    }
    if "open_id" in identity:
        bundle["identity"]["open_id"] = identity["open_id"]
        bundle["identity"]["open_id_source"] = identity["open_id_source"]
    if "feishu_api_base_url" in identity:
        bundle["metadata"]["feishu_api_base_url"] = identity["feishu_api_base_url"]
    if "feishu_credential_source" in identity:
        bundle["metadata"]["feishu_credential_source"] = identity["feishu_credential_source"]

    if args.bundle_file is not None:
        save_json(args.bundle_file, bundle)
    emit_json(bundle, pretty=args.pretty)


def handle_refresh_operator_token(args: argparse.Namespace) -> None:
    bundle = load_bundle(args.bundle_file)
    conversation = expect_dict(bundle, "conversation")
    operator = expect_dict(expect_dict(bundle, "identity"), "operator")
    metadata = expect_dict(bundle, "metadata")
    source = args.source or require_non_empty_str(metadata.get("source"), "metadata.source")
    base_url = require_non_empty_str(expect_dict(bundle, "gateway").get("base_url"), "gateway.base_url")

    operator_response = post_json(
        base_url=base_url,
        path="/internal/jwt/operator-token",
        payload={
            "source": source,
            "operator": operator,
            "conversation": {
                "conversation_id": require_non_empty_str(
                    conversation.get("conversation_id"),
                    "conversation.conversation_id",
                ),
                "conversation_type": require_non_empty_str(
                    conversation.get("conversation_type"),
                    "conversation.conversation_type",
                ),
            },
            "expires_in": args.operator_ttl_seconds,
        },
        timeout_seconds=args.timeout_seconds,
    )
    operator_body = expect_dict_body(operator_response, "/internal/jwt/operator-token")
    bundle["operator_token"] = operator_body
    metadata["operator_token_refreshed_at"] = utc_now_iso()
    save_json(args.bundle_file, bundle)
    emit_json(bundle, pretty=args.pretty)


def handle_list_tools(args: argparse.Namespace) -> None:
    bundle = load_bundle(args.bundle_file)
    response = post_json(
        base_url=require_non_empty_str(
            expect_dict(bundle, "gateway").get("base_url"),
            "gateway.base_url",
        ),
        path="/mcp",
        payload={"method": "tools/list", "params": {}},
        headers=build_gateway_auth_headers(bundle),
        timeout_seconds=args.timeout_seconds,
    )
    emit_json(format_gateway_response(response), pretty=args.pretty)


def handle_call_tool(args: argparse.Namespace) -> None:
    bundle = load_bundle(args.bundle_file)
    arguments = load_arguments(args.arguments, args.arguments_file)
    response = post_json(
        base_url=require_non_empty_str(
            expect_dict(bundle, "gateway").get("base_url"),
            "gateway.base_url",
        ),
        path="/mcp",
        payload={
            "method": "tools/call",
            "params": {"name": args.tool_name, "arguments": arguments},
        },
        headers=build_gateway_auth_headers(bundle),
        timeout_seconds=args.timeout_seconds,
    )
    emit_json(format_gateway_response(response), pretty=args.pretty)


def handle_export_catalog(args: argparse.Namespace) -> None:
    bundle = load_bundle(args.bundle_file)
    response = post_json(
        base_url=require_non_empty_str(
            expect_dict(bundle, "gateway").get("base_url"),
            "gateway.base_url",
        ),
        path="/mcp",
        payload={"method": "tools/list", "params": {}},
        headers=build_gateway_auth_headers(bundle),
        timeout_seconds=args.timeout_seconds,
    )
    body = response.get("body", {})
    raw_tools = body.get("tools", []) if isinstance(body, dict) else []
    catalog = [
        {
            "name": t["name"],
            "description": t.get("description"),
            "inputSchema": t.get("inputSchema", {}),
        }
        for t in raw_tools
        if isinstance(t, dict) and "name" in t
    ]
    save_json(args.output, catalog)
    emit_json(
        {"ok": True, "exported": len(catalog), "output": str(args.output)},
        pretty=True,
    )


def handle_login(args: argparse.Namespace) -> None:
    """Feishu OAuth 2.0 login flow.

    1. Request auth_url from Gateway /auth/oauth/feishu/start
    2. Open browser / print URL for user to visit
    3. Start local HTTP server to receive the callback
    4. Exchange code for bundle via /auth/oauth/feishu/exchange
    5. Write credentials.json (chmod 600)
    """
    import uuid

    base_url = resolve_gateway_base_url(args.base_url)
    conversation_id = args.conversation_id or f"oauth-{uuid.uuid4().hex[:12]}"
    credentials_file: Path = args.credentials_file
    timeout_seconds: float = args.timeout_seconds

    # Pick a local port
    port = args.port
    if port == 0:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind(("127.0.0.1", 0))
            port = s.getsockname()[1]

    redirect_uri = f"http://127.0.0.1:{port}/callback"

    # Step 1: get auth_url from Gateway (GET with query param)
    from urllib.parse import urlencode as _urlencode
    start_resp = request_json(
        method="GET",
        url=f"{base_url}/auth/oauth/feishu/start?{_urlencode({'redirect_uri': redirect_uri})}",
        timeout_seconds=timeout_seconds,
    )
    start_body = expect_dict_body(start_resp, "/auth/oauth/feishu/start")
    auth_url = require_non_empty_str(start_body.get("auth_url"), "auth_url")
    state = require_non_empty_str(start_body.get("state"), "state")

    # Step 2: open browser
    print(f"\n飞书 OAuth 授权链接：\n  {auth_url}\n")
    print("正在尝试打开浏览器，如未自动打开请手动访问上方链接...")
    try:
        webbrowser.open(auth_url)
    except Exception:
        pass

    # Step 3: local HTTP server to capture callback
    callback_result: dict[str, str] = {}
    callback_event = threading.Event()

    class _CallbackHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/callback":
                qs = parse_qs(parsed.query)
                callback_result["code"] = qs.get("code", [""])[0]
                callback_result["state"] = qs.get("state", [""])[0]
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(
                    b"<html><body><h2>\xe8\xae\xa4\xe8\xaf\x81\xe6\x88\x90\xe5\x8a\x9f"
                    b"\xef\xbc\x81\xe5\x8f\xaf\xe4\xbb\xa5\xe5\x85\xb3\xe9\x97\xad\xe6"
                    b"\xad\xa4\xe7\xaa\x97\xe5\x8f\xa3\xe4\xba\x86\xe3\x80\x82</h2></body></html>"
                )
                callback_event.set()
            else:
                self.send_response(404)
                self.end_headers()

        def log_message(self, fmt: str, *a: object) -> None:
            pass  # suppress request logs

    server = HTTPServer(("127.0.0.1", port), _CallbackHandler)
    server_thread = threading.Thread(target=server.serve_forever, daemon=True)
    server_thread.start()

    print(f"等待飞书回调（超时 {int(timeout_seconds)}s）...")
    if not callback_event.wait(timeout=timeout_seconds):
        server.shutdown()
        raise UsageError(f"OAuth 登录超时（{int(timeout_seconds)}s 内未收到回调）。")
    server.shutdown()

    code = callback_result.get("code", "")
    returned_state = callback_result.get("state", "")
    if not code:
        raise UsageError("回调中缺少 code 参数。")
    if returned_state != state:
        raise UsageError("回调中的 state 不匹配，可能存在 CSRF 攻击，已中止。")

    # Step 4: exchange code for bundle
    exchange_resp = post_json(
        base_url=base_url,
        path="/auth/oauth/feishu/exchange",
        payload={
            "code": code,
            "state": state,
            "redirect_uri": redirect_uri,
            "conversation_id": conversation_id,
            "conversation_type": args.conversation_type,
            "task_key": args.task_key,
        },
        timeout_seconds=timeout_seconds,
    )
    bundle_body = expect_dict_body(exchange_resp, "/auth/oauth/feishu/exchange")

    # Step 5: build credentials.json and write atomically
    now = datetime.now(tz=timezone.utc)
    mcp_token = bundle_body.get("mcp_session_token", {})
    op_token = bundle_body.get("operator_token", {})
    operator = bundle_body.get("operator", {})
    session = bundle_body.get("session", {})
    refresh_token = bundle_body.get("feishu_refresh_token", "")
    refresh_expires_in = int(bundle_body.get("feishu_refresh_token_expires_in", 2592000))
    mcp_expires_in = int(mcp_token.get("expires_in", 3600))

    def _iso(dt: datetime) -> str:
        return dt.replace(microsecond=0).isoformat().replace("+00:00", "Z")

    credentials: dict[str, Any] = {
        "auth_source": "feishu_oauth",
        "gateway_base_url": base_url,
        "saved_at": _iso(now),
        "user": {
            "open_id": bundle_body.get("feishu_open_id", ""),
            "union_id": operator.get("union_id", ""),
            "user_id": operator.get("user_id", ""),
            "user_name": operator.get("user_name", ""),
        },
        "operator": operator,
        "session": session,
        "mcp_session": {
            "token": mcp_token.get("token", ""),
            "expires_in": mcp_expires_in,
            "expires_at": _iso(now + timedelta(seconds=mcp_expires_in)),
        },
        "operator_token": op_token,
        "feishu_refresh_token": refresh_token,
        "feishu_refresh_token_expires_at": _iso(now + timedelta(seconds=refresh_expires_in)),
        "conversation": {
            "conversation_id": conversation_id,
            "conversation_type": args.conversation_type,
            "task_key": args.task_key,
            "owner_user_id": session.get("owner_user_id", operator.get("user_id", "")),
        },
        "metadata": {
            "source": "feishu_oauth_login",
            "bootstrapped_at": _iso(now),
        },
    }

    _write_credentials_atomic(credentials_file, credentials)

    user_name = operator.get("user_name", "未知")
    mcp_expires_at = _iso(now + timedelta(seconds=mcp_expires_in))
    print(f"\n✓ 登录成功！")
    print(f"  用户：{user_name}")
    print(f"  会话有效期至（mcp_session）：{mcp_expires_at}")
    print(f"  凭证已保存至：{credentials_file}")


def _write_credentials_atomic(path: Path, credentials: dict[str, Any]) -> None:
    """Write credentials JSON with atomic rename and chmod 600."""
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(credentials, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    fd, tmp_path = tempfile.mkstemp(dir=path.parent, prefix=".creds-")
    try:
        os.write(fd, content.encode("utf-8"))
        os.fchmod(fd, stat.S_IRUSR | stat.S_IWUSR)
        os.close(fd)
        os.replace(tmp_path, path)
    except Exception:
        os.close(fd)
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        raise


def resolve_open_id(
    *,
    explicit_open_id: str | None,
    context_file: Path | None,
    user_file: Path | None,
) -> dict[str, str]:
    explicit = normalize_str(explicit_open_id)
    if explicit is not None:
        return {"open_id": validate_open_id(explicit), "source": "flag"}

    if context_file is not None:
        payload = load_json(context_file)
        for path in COMMON_OPEN_ID_PATHS:
            value = get_dotted_path(payload, path)
            normalized = normalize_str(value)
            if normalized is not None:
                return {
                    "open_id": validate_open_id(normalized),
                    "source": f"context:{'.'.join(path)}",
                }

    openclaw_user_file = user_file or DEFAULT_OPENCLAW_USER_FILE
    openclaw_user_resolution = resolve_open_id_from_user_file(
        path=openclaw_user_file,
        explicit_path=user_file is not None,
    )
    if openclaw_user_resolution is not None:
        return openclaw_user_resolution

    env_open_id = normalize_str(os.getenv("MCP_GATEWAY_OPEN_ID"))
    if env_open_id is not None:
        return {
            "open_id": validate_open_id(env_open_id),
            "source": "env:MCP_GATEWAY_OPEN_ID",
        }

    deprecated_env_open_id = normalize_str(os.getenv("MCP_GATEWAY_FEISHU_ID"))
    if deprecated_env_open_id is not None:
        return {
            "open_id": validate_open_id(deprecated_env_open_id),
            "source": "env:MCP_GATEWAY_FEISHU_ID",
        }

    raise UsageError(
        "缺少可信 open_id。请传 --open-id，或提供 --context-file，或保证 "
        "~/.openclaw/workspace/USER.md 中存在 Feishu Open ID，或设置 MCP_GATEWAY_OPEN_ID。"
    )


def resolve_open_id_from_user_file(*, path: Path, explicit_path: bool) -> dict[str, str] | None:
    try:
        content = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        if explicit_path:
            raise UsageError(f"用户文件不存在: {path}") from None
        return None

    match = OPENCLAW_USER_OPEN_ID_PATTERN.search(content)
    if match is None:
        if explicit_path:
            raise UsageError(f"用户文件里没有找到 'Feishu Open ID: ou_...'：{path}")
        return None
    return {
        "open_id": validate_open_id(match.group("open_id")),
        "source": f"user_file:{path}",
    }


def resolve_union_identity(
    *,
    explicit_union_id: str | None,
    explicit_open_id: str | None,
    context_file: Path | None,
    user_file: Path | None,
    feishu_api_base_url: str | None,
    feishu_app_id: str | None,
    feishu_app_secret: str | None,
    gateway_base_url: str | None,
    timeout_seconds: float,
) -> dict[str, str]:
    open_id_resolution = try_resolve_open_id(
        explicit_open_id=explicit_open_id,
        context_file=context_file,
        user_file=user_file,
    )

    explicit = normalize_str(explicit_union_id)
    if explicit is not None:
        resolution: dict[str, str] = {
            "union_id": validate_union_id(explicit),
            "union_id_source": "flag",
        }
        if open_id_resolution is not None:
            resolution.update(
                {
                    "open_id": open_id_resolution["open_id"],
                    "open_id_source": open_id_resolution["source"],
                }
            )
        return resolution

    env_union_id = normalize_str(os.getenv("MCP_GATEWAY_UNION_ID"))
    if env_union_id is not None:
        resolution = {
            "union_id": validate_union_id(env_union_id),
            "union_id_source": "env:MCP_GATEWAY_UNION_ID",
        }
        if open_id_resolution is not None:
            resolution.update(
                {
                    "open_id": open_id_resolution["open_id"],
                    "open_id_source": open_id_resolution["source"],
                }
            )
        return resolution

    if open_id_resolution is None:
        open_id_resolution = resolve_open_id(
            explicit_open_id=explicit_open_id,
            context_file=context_file,
            user_file=user_file,
        )

    resolved_feishu_api_base_url = resolve_feishu_api_base_url(
        explicit_base_url=feishu_api_base_url,
        gateway_base_url=gateway_base_url,
    )
    feishu_credentials = resolve_feishu_credentials(
        explicit_app_id=feishu_app_id,
        explicit_app_secret=feishu_app_secret,
        feishu_api_base_url=resolved_feishu_api_base_url,
    )
    tenant_access_token = issue_feishu_tenant_access_token(
        feishu_api_base_url=resolved_feishu_api_base_url,
        app_id=feishu_credentials["app_id"],
        app_secret=feishu_credentials["app_secret"],
        timeout_seconds=timeout_seconds,
    )
    union_id = fetch_union_id_by_open_id(
        feishu_api_base_url=resolved_feishu_api_base_url,
        tenant_access_token=tenant_access_token,
        open_id=open_id_resolution["open_id"],
        timeout_seconds=timeout_seconds,
    )
    return {
        "open_id": open_id_resolution["open_id"],
        "open_id_source": open_id_resolution["source"],
        "union_id": union_id,
        "union_id_source": "feishu_open_api",
        "feishu_api_base_url": resolved_feishu_api_base_url,
        "feishu_credential_source": feishu_credentials["source"],
    }


def try_resolve_open_id(
    *,
    explicit_open_id: str | None,
    context_file: Path | None,
    user_file: Path | None,
) -> dict[str, str] | None:
    try:
        return resolve_open_id(
            explicit_open_id=explicit_open_id,
            context_file=context_file,
            user_file=user_file,
        )
    except UsageError:
        return None


def resolve_gateway_base_url(base_url: str | None) -> str:
    resolved = (
        normalize_str(base_url)
        or normalize_str(os.getenv("MCP_GATEWAY_BASE_URL"))
        or DEFAULT_GATEWAY_BASE_URL
    )
    return resolved.rstrip("/")


def resolve_feishu_api_base_url(*, explicit_base_url: str | None, gateway_base_url: str | None) -> str:
    configured = (
        normalize_str(explicit_base_url)
        or normalize_str(os.getenv("MCP_GATEWAY_FEISHU_API_BASE_URL"))
        or normalize_str(os.getenv("FEISHU_API_BASE_URL"))
    )
    if configured is not None:
        return configured.rstrip("/")

    effective_gateway_base_url = gateway_base_url or normalize_str(os.getenv("MCP_GATEWAY_BASE_URL"))
    if effective_gateway_base_url is not None and is_local_url(effective_gateway_base_url):
        return DEFAULT_LOCAL_FEISHU_API_BASE_URL
    return DEFAULT_FEISHU_API_BASE_URL


def resolve_feishu_credentials(
    *,
    explicit_app_id: str | None,
    explicit_app_secret: str | None,
    feishu_api_base_url: str,
) -> dict[str, str]:
    openclaw_config = load_openclaw_config()
    config_app_id = normalize_str(get_dotted_path(openclaw_config, ("channels", "feishu", "appId")))
    config_app_secret = normalize_str(
        get_dotted_path(openclaw_config, ("channels", "feishu", "appSecret"))
    )

    app_id, app_id_source = first_present_value(
        ("flag:--feishu-app-id", explicit_app_id),
        ("env:FEISHU_APP_ID", os.getenv("FEISHU_APP_ID")),
        ("env:MCP_GATEWAY_FEISHU_APP_ID", os.getenv("MCP_GATEWAY_FEISHU_APP_ID")),
        ("openclaw.json", config_app_id),
    )
    app_secret, app_secret_source = first_present_value(
        ("flag:--feishu-app-secret", explicit_app_secret),
        ("env:FEISHU_APP_SECRET", os.getenv("FEISHU_APP_SECRET")),
        ("env:MCP_GATEWAY_FEISHU_APP_SECRET", os.getenv("MCP_GATEWAY_FEISHU_APP_SECRET")),
        ("openclaw.json", config_app_secret),
    )

    if app_id is None or app_secret is None:
        if is_local_url(feishu_api_base_url):
            return {
                "app_id": app_id or "local-debug-app-id",
                "app_secret": app_secret or "local-debug-app-secret",
                "source": "local_debug_default",
            }
        raise UsageError(
            "缺少飞书应用凭证。请传 --feishu-app-id/--feishu-app-secret，或设置 "
            "FEISHU_APP_ID/FEISHU_APP_SECRET，或在 ~/.openclaw/openclaw.json 中配置 "
            "channels.feishu.appId/appSecret。"
        )

    source = (
        app_id_source if app_id_source == app_secret_source else f"{app_id_source}+{app_secret_source}"
    )
    return {"app_id": app_id, "app_secret": app_secret, "source": source}


def load_openclaw_config() -> Any:
    try:
        return json.loads(DEFAULT_OPENCLAW_CONFIG_FILE.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def issue_feishu_tenant_access_token(
    *,
    feishu_api_base_url: str,
    app_id: str,
    app_secret: str,
    timeout_seconds: float,
) -> str:
    response = post_json(
        base_url=feishu_api_base_url,
        path="/auth/v3/tenant_access_token/internal",
        payload={"app_id": app_id, "app_secret": app_secret},
        timeout_seconds=timeout_seconds,
    )
    body = expect_dict_body(response, "/auth/v3/tenant_access_token/internal")
    ensure_feishu_success(body, "/auth/v3/tenant_access_token/internal")
    return require_non_empty_str(body.get("tenant_access_token"), "tenant_access_token")


def fetch_union_id_by_open_id(
    *,
    feishu_api_base_url: str,
    tenant_access_token: str,
    open_id: str,
    timeout_seconds: float,
) -> str:
    response = get_json(
        base_url=feishu_api_base_url,
        path=f"/contact/v3/users/{quote(open_id, safe='')}",
        query={"user_id_type": "open_id"},
        headers={"Authorization": f"Bearer {tenant_access_token}"},
        timeout_seconds=timeout_seconds,
    )
    body = expect_dict_body(response, "/contact/v3/users/{open_id}")
    ensure_feishu_success(body, "/contact/v3/users/{open_id}")
    user = expect_dict(expect_dict(body, "data"), "user")
    union_id = normalize_str(user.get("union_id")) or normalize_str(user.get("unionId"))
    if union_id is None:
        raise UsageError("飞书用户详情里缺少 union_id。")
    return validate_union_id(union_id)


def ensure_feishu_success(body: dict[str, Any], path: str) -> None:
    code = body.get("code")
    if code in (None, 0):
        return
    raise ApiError(
        path=path,
        status=200,
        headers={},
        body=body,
        message=f"Feishu OpenAPI 返回错误 code={code}",
    )


def build_gateway_auth_headers(bundle: dict[str, Any]) -> dict[str, str]:
    mcp_session = expect_dict(bundle, "mcp_session")
    operator_token = expect_dict(bundle, "operator_token")
    return {
        "Authorization": "Bearer " + require_non_empty_str(mcp_session.get("token"), "mcp_session.token"),
        "X-Operator-Token": require_non_empty_str(
            operator_token.get("token"),
            "operator_token.token",
        ),
    }


def load_arguments(arguments: str, arguments_file: Path | None) -> dict[str, Any]:
    if arguments_file is not None:
        payload = load_json(arguments_file)
    else:
        try:
            payload = json.loads(arguments)
        except json.JSONDecodeError as exc:
            raise UsageError(f"--arguments 必须是合法 JSON: {exc}") from exc
    if not isinstance(payload, dict):
        raise UsageError("工具参数必须是 JSON 对象。")
    return payload


def format_gateway_response(response: dict[str, Any]) -> dict[str, Any]:
    return {
        "ok": True,
        "status": response["status"],
        "trace_id": response["headers"].get("X-Trace-Id"),
        "headers": response["headers"],
        "body": response["body"],
    }


def post_json(
    *,
    base_url: str,
    path: str,
    payload: dict[str, Any],
    timeout_seconds: float,
    headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    return request_json(
        method="POST",
        url=join_base_url(base_url, path),
        payload=payload,
        headers=headers,
        timeout_seconds=timeout_seconds,
    )


def get_json(
    *,
    base_url: str,
    path: str,
    query: dict[str, str],
    headers: dict[str, str] | None,
    timeout_seconds: float,
) -> dict[str, Any]:
    return request_json(
        method="GET",
        url=join_base_url(base_url, path),
        query=query,
        headers=headers,
        timeout_seconds=timeout_seconds,
    )


def request_json(
    *,
    method: str,
    url: str,
    timeout_seconds: float,
    payload: dict[str, Any] | None = None,
    query: dict[str, str] | None = None,
    headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    if query:
        url = f"{url}?{urlencode(query)}"
    request_headers = {"Accept": "application/json"}
    data = None
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    if headers:
        request_headers.update(headers)

    request = Request(url=url, method=method, data=data, headers=request_headers)
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            raw_body = response.read().decode("utf-8")
            return {
                "status": response.status,
                "headers": dict(response.headers.items()),
                "body": parse_jsonish(raw_body),
            }
    except HTTPError as exc:
        raw_body = exc.read().decode("utf-8")
        raise ApiError(
            path=url,
            status=exc.code,
            headers=dict(exc.headers.items()),
            body=parse_jsonish(raw_body),
            message=f"HTTP 请求返回 {exc.code}",
        ) from exc
    except URLError as exc:
        raise ApiError(
            path=url,
            status=None,
            headers={},
            body=None,
            message=f"HTTP 请求失败: {exc.reason}",
        ) from exc


def load_bundle(path: Path) -> dict[str, Any]:
    payload = load_json(path)
    if not isinstance(payload, dict):
        raise UsageError(f"bundle 文件必须是 JSON 对象: {path}")
    return payload


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise UsageError(f"文件不存在: {path}") from exc
    except json.JSONDecodeError as exc:
        raise UsageError(f"JSON 文件格式错误 {path}: {exc}") from exc


def save_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def parse_jsonish(raw_body: str) -> Any:
    stripped = raw_body.strip()
    if not stripped:
        return None
    try:
        return json.loads(stripped, parse_float=str)
    except json.JSONDecodeError:
        return stripped


def expect_dict_body(response: dict[str, Any], path: str) -> dict[str, Any]:
    body = response["body"]
    if not isinstance(body, dict):
        raise ApiError(
            path=path,
            status=response["status"],
            headers=response["headers"],
            body=body,
            message="HTTP 响应不是 JSON 对象。",
        )
    return body


def expect_dict(parent: dict[str, Any], key: str) -> dict[str, Any]:
    value = parent.get(key)
    if not isinstance(value, dict):
        raise UsageError(f"字段 {key} 必须是 JSON 对象。")
    return value


def require_non_empty_str(value: object, field_name: str) -> str:
    normalized = normalize_str(value)
    if normalized is None:
        raise UsageError(f"缺少必填字符串字段: {field_name}")
    return normalized


def validate_open_id(value: str) -> str:
    normalized = value.strip()
    if not normalized.startswith("ou_"):
        raise UsageError(f"open_id 必须以 'ou_' 开头: {value}")
    return normalized


def validate_union_id(value: str) -> str:
    normalized = value.strip()
    if not normalized.startswith("on_"):
        raise UsageError(f"union_id 必须以 'on_' 开头: {value}")
    return normalized


def get_dotted_path(payload: Any, path: tuple[str, ...]) -> Any:
    current = payload
    for segment in path:
        if not isinstance(current, dict):
            return None
        current = current.get(segment)
    return current


def normalize_str(value: object) -> str | None:
    if isinstance(value, str):
        stripped = value.strip()
        if stripped:
            return stripped
    return None


def first_present_value(*candidates: tuple[str, object]) -> tuple[str | None, str | None]:
    for source, value in candidates:
        normalized = normalize_str(value)
        if normalized is not None:
            return normalized, source
    return None, None


def join_base_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path


def is_local_url(url: str) -> bool:
    hostname = urlparse(url).hostname
    return hostname in {"127.0.0.1", "localhost"}


def utc_now_iso() -> str:
    return datetime.now(tz=timezone.utc).replace(microsecond=0).isoformat()


def emit_json(payload: Any, *, pretty: bool) -> None:
    if pretty:
        json.dump(payload, sys.stdout, ensure_ascii=False, indent=2, sort_keys=True)
    else:
        json.dump(payload, sys.stdout, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    sys.stdout.write("\n")


if __name__ == "__main__":
    raise SystemExit(main())
