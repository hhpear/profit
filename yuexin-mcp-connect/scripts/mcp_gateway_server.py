#!/usr/bin/env python3
"""MCP Server wrapper for yuexin-platform-mcp-gateway.

Speaks standard MCP protocol over stdio transport.
- tools/list: serves an in-memory catalog snapshot, refreshed at startup
              from the gateway's ``GET /gateway/registry/tools`` endpoint
              (unauthenticated, returns the full aggregated tool set);
              falls back to local tools-catalog.json if the gateway is
              unreachable. Per-user permission filtering is deferred to
              tools/call — agents may see tools they cannot ultimately
              invoke, and will get 403 at call time.
- tools/call: delegates to GatewaySession with full skill auth chain
"""

from __future__ import annotations

import sys

if sys.version_info < (3, 11):
    sys.stderr.write(
        f"mcp_gateway_server.py requires Python >= 3.11, got "
        f"{sys.version_info.major}.{sys.version_info.minor}. "
        f"Please rerun with python3.11 or newer.\n"
    )
    sys.exit(2)

import asyncio
import json
import logging
import os
import socket
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlencode, urlparse

# Ensure scripts/ directory is importable
sys.path.insert(0, str(Path(__file__).resolve().parent))

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import TextContent, Tool

from inject_operator_field import patch_tool
from mcp_gateway_client import (
    ApiError,
    expect_dict_body,
    post_json,
    require_non_empty_str,
    request_json,
    resolve_gateway_base_url,
)
from mcp_gateway_core import (
    BootstrapConfig,
    CredentialsExpiredError,
    GatewaySession,
    _try_load_credentials,
    build_config_from_env,
)

logger = logging.getLogger("mcp-gateway-server")

# ---------------------------------------------------------------------------
# Catalog
#
# Primary source: gateway ``GET /gateway/registry/tools`` pulled once at
# startup into memory.  That endpoint is unauthenticated and returns the
# full set of aggregated tools — per-user visibility filtering is enforced
# at tools/call time instead.  This replaces the old "admin manually runs
# export-catalog" workflow: whenever the gateway registers or renames a
# tool, an agent restart is enough to pick it up.
#
# Fallback: the local ``tools-catalog.json`` (same file used by previous
# export-catalog workflow), so the agent still boots when the gateway is
# briefly unreachable.
# ---------------------------------------------------------------------------

_DEFAULT_CATALOG_PATH = Path(__file__).resolve().parent.parent / "tools-catalog.json"

# In-memory catalog populated at startup (gateway refresh → file fallback).
_CATALOG_CACHE: list[dict[str, Any]] = []


def _resolve_catalog_path() -> Path:
    env = os.getenv("MCP_GATEWAY_CATALOG_FILE")
    if env:
        return Path(env)
    return _DEFAULT_CATALOG_PATH


def _load_catalog_from_file() -> list[dict[str, Any]]:
    path = _resolve_catalog_path()
    if not path.exists():
        logger.warning("工具目录文件不存在: %s，tools/list 将返回空列表", path)
        return []
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, json.JSONDecodeError):
        logger.warning("读取本地 catalog 文件失败: %s", path, exc_info=True)
        return []
    if not isinstance(data, list):
        logger.warning("工具目录文件格式异常（非数组）: %s", path)
        return []
    return data


def _fetch_catalog_from_gateway() -> list[dict[str, Any]] | None:
    """Pull the full aggregated tool list from the gateway.

    Hits ``GET {base_url}/gateway/registry/tools``, which is an internal
    control-plane endpoint that does not require operator identity.  We
    map the response fields (``full_name`` / ``input_schema``) to the
    MCP tool shape (``name`` / ``inputSchema``) used by the local catalog
    format, so gateway-sourced and file-sourced catalogs are interchangeable.

    Returns ``None`` on any error so the caller can fall back to the local
    file; intentionally non-throwing so a gateway hiccup never prevents the
    MCP server from booting.
    """
    # Import httpx lazily so environments that only use file-based catalog
    # (e.g. offline dev) don't need it installed.
    try:
        import httpx
    except ImportError:
        logger.warning("httpx 未安装，跳过从网关拉取 catalog，直接使用本地文件")
        return None

    try:
        base_url = resolve_gateway_base_url(os.getenv("MCP_GATEWAY_BASE_URL"))
    except Exception:
        logger.warning("无法解析 gateway base_url，将回退到本地 catalog", exc_info=True)
        return None

    url = f"{base_url.rstrip('/')}/gateway/registry/tools"
    timeout = float(os.getenv("MCP_GATEWAY_CATALOG_FETCH_TIMEOUT", "5.0"))
    try:
        with httpx.Client(timeout=timeout) as c:
            resp = c.get(url)
            resp.raise_for_status()
            payload = resp.json()
    except Exception as exc:
        logger.warning("从网关拉取 catalog 失败 (%s): %s；回退到本地文件", url, exc)
        return None

    tools = payload.get("tools") if isinstance(payload, dict) else None
    if not isinstance(tools, list):
        logger.warning("网关返回的 catalog 结构异常: %s", payload)
        return None

    normalized: list[dict[str, Any]] = []
    for t in tools:
        if not isinstance(t, dict) or not t.get("full_name"):
            continue
        normalized.append(
            {
                "name": t["full_name"],
                "description": t.get("description", ""),
                "inputSchema": t.get(
                    "input_schema", {"type": "object", "properties": {}}
                ),
            }
        )
    return normalized


def _persist_catalog_snapshot(catalog: list[dict[str, Any]]) -> None:
    """Write the freshly-fetched catalog to disk as a warm-cache snapshot.

    Next startup will still prefer the gateway, but this file keeps the
    agent bootable when the gateway is briefly unreachable.  Failures are
    logged but never raised — the live in-memory cache is authoritative.
    """
    path = _resolve_catalog_path()
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2)
    except OSError:
        logger.warning("写入本地 catalog 快照失败: %s", path, exc_info=True)


def _inject_operator_field(catalog: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Patch every tool schema so ``_operator_open_id`` is a visible required field.

    Without this the LLM never sees the field, omits it on tools/call, and
    the server rejects the request with ``missing_operator_open_id`` — which
    surfaces to the agent as "this tool is asking for open_id" confusion.
    The patching logic is identical to the legacy ``inject_operator_field.py``
    CLI, just applied inline on every catalog load path.
    """
    for tool in catalog:
        if isinstance(tool, dict):
            patch_tool(tool)
    return catalog


def _initialize_catalog() -> None:
    """Populate the in-memory catalog at process startup.

    Strategy: try gateway first (authoritative source); on any failure fall
    back to the on-disk snapshot so the agent still boots.  On success the
    fresh catalog is also written back to disk as the next-boot fallback.
    In OAuth mode, _operator_open_id is NOT injected into tool schemas.
    """
    global _CATALOG_CACHE
    fresh = _fetch_catalog_from_gateway()
    source = fresh if fresh is not None else _load_catalog_from_file()
    if not _OAUTH_MODE:
        source = _inject_operator_field(source)
    _CATALOG_CACHE = source
    if fresh is not None:
        logger.info("从网关加载工具目录: %d 个工具 (oauth_mode=%s)", len(_CATALOG_CACHE), _OAUTH_MODE)
        _persist_catalog_snapshot(_CATALOG_CACHE)
    else:
        logger.info("回退使用本地 catalog 文件: %d 个工具 (oauth_mode=%s)", len(_CATALOG_CACHE), _OAUTH_MODE)


# ---------------------------------------------------------------------------
# OAuth mode detection
#
# When MCP_GATEWAY_CREDENTIALS_FILE points to a valid credentials.json (or the
# default ~/.mcp-gateway/credentials.json exists), the server runs in OAuth
# mode:
#   • A single GatewaySession is bootstrapped from the file at startup.
#   • _operator_open_id is NOT injected into tool schemas — callers don't need
#     to supply it.
#   • If the credentials are expired, the server boots but every tools/call
#     returns an actionable error asking the user to re-run `login`.
#
# When no credentials file is found, the server runs in Bot mode (original
# behaviour): _operator_open_id is required on every tools/call.
# ---------------------------------------------------------------------------

_OAUTH_MODE: bool = False
_oauth_session: GatewaySession | None = None
_oauth_session_lock = asyncio.Lock()


def _is_oauth_mode_requested() -> bool:
    """True if OAuth mode is desired (regardless of whether credentials exist yet).

    Set MCP_GATEWAY_CREDENTIALS_FILE="" to explicitly disable OAuth and fall
    back to Bot mode (per-call _operator_open_id required).
    """
    env = os.getenv("MCP_GATEWAY_CREDENTIALS_FILE")
    if env is not None:
        return bool(env.strip())  # empty string = explicitly disable OAuth
    return True  # default: OAuth mode


def _get_credentials_path() -> Path | None:
    """Return the intended credentials file path, or None if OAuth is disabled."""
    env = os.getenv("MCP_GATEWAY_CREDENTIALS_FILE")
    if env is not None:
        return Path(env.strip()) if env.strip() else None
    return Path.home() / ".mcp-gateway" / "credentials.json"


def _get_oauth_callback_port() -> int:
    """Return the fixed local port for OAuth callback. Default: 18765."""
    return int(os.getenv("MCP_GATEWAY_OAUTH_CALLBACK_PORT", "18765"))


def _run_login_flow(creds_path: Path, config: BootstrapConfig) -> bool:
    """Run OAuth login interactively (open browser + wait for callback).

    Uses the fixed port from MCP_GATEWAY_OAUTH_CALLBACK_PORT (default 18765)
    so the redirect URI can be pre-registered in Feishu's developer console.
    Returns True on success, False on timeout or error.
    """
    import uuid

    base_url = resolve_gateway_base_url(config.base_url)
    task_key = config.task_key or "mcp-stdio-session"
    conversation_id = f"oauth-{uuid.uuid4().hex[:12]}"
    timeout_seconds = 300.0  # 5 minutes for user to authorize
    port = _get_oauth_callback_port()

    redirect_uri = f"http://127.0.0.1:{port}/callback"

    try:
        start_resp = request_json(
            method="GET",
            url=f"{base_url}/auth/oauth/feishu/start?{urlencode({'redirect_uri': redirect_uri})}",
            timeout_seconds=config.timeout_seconds,
        )
        start_body = expect_dict_body(start_resp, "/auth/oauth/feishu/start")
        auth_url = require_non_empty_str(start_body.get("auth_url"), "auth_url")
        state = require_non_empty_str(start_body.get("state"), "state")
    except Exception as exc:
        print(f"\n[yuexin-gateway] 获取飞书授权链接失败: {exc}", file=sys.stderr)
        return False

    print(f"\n[yuexin-gateway] 首次使用需要飞书身份授权", file=sys.stderr)
    print(f"[yuexin-gateway] 飞书授权链接:\n  {auth_url}", file=sys.stderr)
    print(f"[yuexin-gateway] 正在尝试打开浏览器...", file=sys.stderr)
    try:
        webbrowser.open(auth_url)
    except Exception:
        pass

    callback_result: dict[str, str] = {}
    callback_event = threading.Event()

    class _Handler(BaseHTTPRequestHandler):
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
                    "<html><body><h2>认证成功！可以关闭此窗口了。</h2></body></html>".encode("utf-8")
                )
                callback_event.set()
            else:
                self.send_response(404)
                self.end_headers()

        def log_message(self, fmt: str, *a: object) -> None:
            pass

    http_server = HTTPServer(("127.0.0.1", port), _Handler)
    t = threading.Thread(target=http_server.serve_forever, daemon=True)
    t.start()

    print(f"[yuexin-gateway] 等待授权回调（超时 {int(timeout_seconds)}s）...", file=sys.stderr)
    if not callback_event.wait(timeout=timeout_seconds):
        http_server.shutdown()
        print("[yuexin-gateway] 授权超时，请重启 Claude Code 重试", file=sys.stderr)
        return False
    http_server.shutdown()

    code = callback_result.get("code", "")
    returned_state = callback_result.get("state", "")
    if not code or returned_state != state:
        print("[yuexin-gateway] 回调参数异常，请重试", file=sys.stderr)
        return False

    # Use a longer timeout for the exchange — it involves Feishu API +
    # user-system lookup and can take several seconds on a cold path.
    exchange_timeout = max(config.timeout_seconds, 60.0)
    try:
        exchange_resp = post_json(
            base_url=base_url,
            path="/auth/oauth/feishu/exchange",
            payload={
                "code": code,
                "state": state,
                "redirect_uri": redirect_uri,
                "conversation_id": conversation_id,
                "conversation_type": "single",
                "task_key": task_key,
            },
            timeout_seconds=exchange_timeout,
        )
        bundle_body = expect_dict_body(exchange_resp, "/auth/oauth/feishu/exchange")
    except Exception as exc:
        print(f"[yuexin-gateway] code 换取 bundle 失败（超时={exchange_timeout}s）: {exc}", file=sys.stderr)
        return False

    from datetime import datetime, timedelta, timezone
    now = datetime.now(tz=timezone.utc)

    def _iso(dt: datetime) -> str:
        return dt.replace(microsecond=0).isoformat().replace("+00:00", "Z")

    mcp_token = bundle_body.get("mcp_session_token", {})
    op_token = bundle_body.get("operator_token", {})
    operator = bundle_body.get("operator", {})
    session = bundle_body.get("session", {})
    refresh_token = bundle_body.get("feishu_refresh_token", "")
    refresh_expires_in = int(bundle_body.get("feishu_refresh_token_expires_in", 2592000))
    mcp_expires_in = int(mcp_token.get("expires_in", 3600))

    credentials = {
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
            "conversation_type": "single",
            "task_key": task_key,
            "owner_user_id": session.get("owner_user_id", operator.get("user_id", "")),
        },
        "metadata": {"source": "feishu_oauth_login", "bootstrapped_at": _iso(now)},
    }

    try:
        creds_path.parent.mkdir(parents=True, exist_ok=True)
        import tempfile, stat
        with tempfile.NamedTemporaryFile(
            mode="w", dir=creds_path.parent, delete=False, suffix=".tmp", encoding="utf-8"
        ) as f:
            json.dump(credentials, f, ensure_ascii=False, indent=2)
            tmp = Path(f.name)
        os.chmod(tmp, stat.S_IRUSR | stat.S_IWUSR)
        tmp.replace(creds_path)
    except Exception as exc:
        print(f"[yuexin-gateway] 保存 credentials 失败: {exc}", file=sys.stderr)
        return False

    user_name = operator.get("user_name", "未知")
    print(f"[yuexin-gateway] ✓ 授权成功！用户：{user_name}", file=sys.stderr)
    return True


# ---------------------------------------------------------------------------
# Session management (lazy bootstrap, per-operator-open_id pool)
# ---------------------------------------------------------------------------

# Reserved argument key that callers must set on every tools/call so the
# server knows which operator (飞书 open_id) is making the request.
# We pop it before forwarding arguments to the gateway.
OPERATOR_OPEN_ID_KEY = "_operator_open_id"

# pool: open_id -> GatewaySession (bootstrapped once, reused)
_sessions: dict[str, GatewaySession] = {}
_sessions_lock = asyncio.Lock()

# Error codes / message substrings that mean the cached CallerContext is stale.
# Triggers automatic session eviction + re-bootstrap (once per call, no loop).
_SESSION_STALE_ERRORS = frozenset({
    "no_service_access",
    "mcp_session_token_invalid",
    "mcp_session_token_expired",
})


def _is_session_stale_result(result: dict[str, Any]) -> bool:
    """Return True when the call result signals a stale/revoked CallerContext.

    Covers roles-changed-after-bootstrap (no_service_access) and expired/
    invalidated MCP Session Tokens.  Checks both the outer result dict and
    the inner JSON payload (nested inside body.content[0].text).
    """
    def _check(d: dict[str, Any] | None) -> bool:
        if not isinstance(d, dict):
            return False
        for key in ("error", "code", "category", "message"):
            val = d.get(key)
            if isinstance(val, str) and (
                val in _SESSION_STALE_ERRORS or "no user authority" in val
            ):
                return True
        return False

    return _check(result) or _check(_extract_inner_json(result))


class MissingOperatorOpenIdError(ValueError):
    """Raised when a tools/call arrives without _operator_open_id."""


def _build_config_for_open_id(operator_open_id: str) -> BootstrapConfig:
    """Build a BootstrapConfig bound to the given operator open_id."""
    config = build_config_from_env()
    config.open_id = operator_open_id
    # Reset union_id so bootstrap resolves it from open_id via Feishu OpenAPI.
    config.union_id = None
    # Namespace the conversation id per operator so sessions don't collide
    # on the gateway side.
    config.conversation_id = (
        f"mcp-stdio-{os.getpid()}-{operator_open_id[:16]}"
    )
    return config


async def _ensure_session(operator_open_id: str) -> GatewaySession:
    """Get (or lazily bootstrap) the session bound to the given operator open_id.

    Maintains a per-open_id session pool so concurrent calls for the same
    operator share one bundle.  No fallback to environment variables —
    callers must always provide _operator_open_id.
    """
    if not operator_open_id:
        raise MissingOperatorOpenIdError(
            f"缺少必填字段 {OPERATOR_OPEN_ID_KEY!r}（当前发送者的飞书 open_id, 形如 ou_xxx）"
        )

    existing = _sessions.get(operator_open_id)
    if existing is not None and existing.is_bootstrapped:
        return existing
    async with _sessions_lock:
        existing = _sessions.get(operator_open_id)
        if existing is not None and existing.is_bootstrapped:
            return existing
        config = _build_config_for_open_id(operator_open_id)
        session = GatewaySession(config)
        logger.info("正在执行 bootstrap (operator=%s) ...", operator_open_id)
        await asyncio.to_thread(session.bootstrap)
        _sessions[operator_open_id] = session
        return session


async def _drop_session(operator_open_id: str) -> None:
    """Evict a cached session so the next call triggers a clean re-bootstrap."""
    async with _sessions_lock:
        _sessions.pop(operator_open_id, None)


async def _ensure_oauth_session() -> GatewaySession:
    """Get (or lazily bootstrap) the single OAuth session.

    If no valid credentials exist, triggers the login flow inline (first call
    blocks until the user authorizes in the browser).  If bootstrap fails with
    CredentialsExpiredError (e.g. gateway restarted and invalidated the in-memory
    token store, or the saved credentials are otherwise stale), the credentials
    file is deleted and the login flow is re-triggered automatically.
    """
    global _oauth_session
    if _oauth_session is not None and _oauth_session.is_bootstrapped:
        return _oauth_session
    async with _oauth_session_lock:
        if _oauth_session is not None and _oauth_session.is_bootstrapped:
            return _oauth_session

        if _get_credentials_path() is None:
            raise CredentialsExpiredError("OAuth 已禁用（MCP_GATEWAY_CREDENTIALS_FILE 为空）")

        for attempt in range(1, 3):  # at most 2 login attempts
            config = build_config_from_env()
            creds_path = _get_credentials_path()
            assert creds_path is not None  # checked above

            # Trigger login if credentials are missing or unreadable
            if _try_load_credentials(creds_path) is None:
                logger.info("未找到有效 credentials（第 %d 次），触发 OAuth 登录流程", attempt)
                success = await asyncio.to_thread(_run_login_flow, creds_path, config)
                if not success:
                    raise CredentialsExpiredError(
                        "OAuth 登录未完成，请重新调用此工具重试"
                    )
                config.credentials_file = creds_path

            session = GatewaySession(config)
            logger.info(
                "正在执行 OAuth bootstrap (attempt=%d, credentials_file=%s) ...",
                attempt,
                config.credentials_file,
            )
            try:
                await asyncio.to_thread(session.bootstrap)
            except CredentialsExpiredError:
                if attempt >= 2:
                    raise
                # Saved credentials are stale (e.g. gateway restarted, token store cleared,
                # or refresh token already rotated).  Delete the file so the next iteration
                # triggers a fresh interactive login instead of silently re-reading the
                # same invalid credentials.
                logger.warning(
                    "OAuth bootstrap 失败（credentials 已失效），删除 credentials 文件后重新登录"
                )
                try:
                    creds_path.unlink(missing_ok=True)
                except OSError:
                    pass
                continue

            _oauth_session = session
            return session

        raise CredentialsExpiredError("OAuth 登录多次失败，请检查网络或飞书授权配置")


async def _drop_oauth_session() -> None:
    """Evict the OAuth session so the next call re-bootstraps from credentials."""
    global _oauth_session
    async with _oauth_session_lock:
        _oauth_session = None


# ---------------------------------------------------------------------------
# tools/list serves the in-memory catalog populated at startup.
#
# Trade-off: the gateway also supports per-operator filtering via the /mcp
# tools/list method, but that path requires a CallerContext — which we do
# not have at MCP stdio-server startup.  Instead we serve the full
# aggregated tool set and let the gateway enforce visibility/permission
# at tools/call time (returning 403 for tools the operator cannot use).
# This matches how Claude Desktop and most MCP clients work today and
# means an agent restart is sufficient to pick up new/renamed tools.
# ---------------------------------------------------------------------------


# ---------------------------------------------------------------------------
# Async job polling — transparent wait for executing tools
# ---------------------------------------------------------------------------

_POLL_INTERVAL = float(os.getenv("MCP_GATEWAY_POLL_INTERVAL", "3"))  # seconds
_POLL_MAX_ATTEMPTS = int(os.getenv("MCP_GATEWAY_POLL_MAX_ATTEMPTS", "40"))  # 3s * 40 = 2min

# Map: tool_name → result_tool_name
_ASYNC_RESULT_TOOL_MAP: dict[str, str] = {
    "datacenter__query_sms_analytics": "datacenter__query_sms_analytics_result",
}


def _extract_inner_json(result: dict[str, Any]) -> dict[str, Any] | None:
    """Extract the inner JSON payload from a gateway tools/call response.

    The gateway wraps tool output in two possible shapes::

        Shape A (CallToolResult wrapper):
        {"body": {"content": {"content": [{"type": "text", "text": "<json>"}], ...}}}

        Shape B (flat):
        {"body": {"content": [{"type": "text", "text": "<json>"}]}}

    This helper handles both, extracts the first TextContent, and JSON-parses it.
    Returns None if the structure doesn't match or parsing fails.
    """
    body = result.get("body")
    if not isinstance(body, dict):
        return None
    content = body.get("content")
    # Shape A: content is a dict (CallToolResult) containing a nested "content" list
    if isinstance(content, dict):
        content = content.get("content")
    # Now content should be a list of TextContent items
    if not isinstance(content, list) or not content:
        return None
    first = content[0]
    if not isinstance(first, dict) or first.get("type") != "text":
        return None
    text = first.get("text")
    if not isinstance(text, str):
        return None
    try:
        parsed = json.loads(text)
        return parsed if isinstance(parsed, dict) else None
    except (json.JSONDecodeError, ValueError):
        return None


async def _await_async_job_if_needed(
    session: GatewaySession,
    tool_name: str,
    result: dict[str, Any],
) -> dict[str, Any]:
    """If the result indicates an async job, poll until done/error.

    The gateway response wraps the downstream tool output inside
    ``body.content[0].text`` (JSON string).  We extract the inner payload
    to inspect ``execution_mode`` / ``job_id``, then auto-poll the
    corresponding *_result tool and return the final inner result directly
    so the caller (MCP Agent) gets the answer in one round-trip.
    """
    if not isinstance(result, dict):
        return result

    inner = _extract_inner_json(result)
    if inner is None:
        return result

    execution_mode = inner.get("execution_mode")
    job_id = inner.get("job_id")
    result_tool = _ASYNC_RESULT_TOOL_MAP.get(tool_name)

    if execution_mode != "executing" or not job_id or not result_tool:
        return result

    logger.info("异步任务 %s 已提交，开始自动轮询 %s ...", job_id, result_tool)

    for attempt in range(1, _POLL_MAX_ATTEMPTS + 1):
        await asyncio.sleep(_POLL_INTERVAL)
        try:
            poll_result = await asyncio.to_thread(
                session.call_tool, result_tool, {"job_id": job_id}
            )
        except Exception:
            logger.warning("轮询 %s 第 %d 次失败", result_tool, attempt, exc_info=True)
            continue

        if not isinstance(poll_result, dict):
            continue

        poll_inner = _extract_inner_json(poll_result)
        if poll_inner is None:
            continue

        status = poll_inner.get("status")
        if status == "done":
            logger.info("异步任务 %s 完成（第 %d 次轮询）", job_id, attempt)
            # Return the inner result directly so the Agent gets clean data
            return poll_inner.get("result", poll_inner)
        elif status == "error":
            logger.warning("异步任务 %s 失败: %s", job_id, poll_inner.get("error"))
            return poll_inner
        # status == "pending": continue polling

    logger.warning("异步任务 %s 超时（%d 次轮询后放弃）", job_id, _POLL_MAX_ATTEMPTS)
    return {
        "ok": False,
        "error": "poll_timeout",
        "message": f"异步任务 {job_id} 在 {_POLL_MAX_ATTEMPTS * _POLL_INTERVAL:.0f} 秒后仍未完成",
        "job_id": job_id,
    }


# ---------------------------------------------------------------------------
# MCP Server
# ---------------------------------------------------------------------------


def create_server() -> Server:
    server = Server("yuexin-mcp-gateway")

    @server.list_tools()
    async def handle_list_tools() -> list[Tool]:
        """Serve tools from the in-memory catalog (loaded at startup)."""
        return [
            Tool(
                name=t["name"],
                description=t.get("description", ""),
                inputSchema=t.get("inputSchema", {"type": "object", "properties": {}}),
            )
            for t in _CATALOG_CACHE
            if isinstance(t, dict) and "name" in t
        ]

    @server.call_tool()
    async def handle_call_tool(name: str, arguments: dict[str, Any]) -> list[TextContent]:
        """Lazy-bootstrap and call the tool via the gateway auth chain.

        OAuth mode: single session from credentials.json, no _operator_open_id needed.
        Bot mode: per-operator session pool, _operator_open_id required.

        For async tools (execution_mode=executing + job_id), automatically
        polls the *_result tool until the job completes — the caller gets
        the final result in a single round-trip.
        """
        # --- OAuth mode: single session, no _operator_open_id required ---
        if _OAUTH_MODE:
            if isinstance(arguments, dict):
                arguments.pop(OPERATOR_OPEN_ID_KEY, None)
            try:
                session = await _ensure_oauth_session()
                await asyncio.to_thread(session.ensure_valid_operator_token)
                try:
                    result = await asyncio.to_thread(session.call_tool, name, arguments)
                except ApiError as exc:
                    if exc.status != 401:
                        raise
                    logger.info("OAuth session 401，重新 bootstrap 后重试")
                    await _drop_oauth_session()
                    session = await _ensure_oauth_session()
                    await asyncio.to_thread(session.ensure_valid_operator_token)
                    result = await asyncio.to_thread(session.call_tool, name, arguments)

                if _is_session_stale_result(result):
                    logger.info("OAuth session 失效，重新 bootstrap 后重试")
                    await _drop_oauth_session()
                    session = await _ensure_oauth_session()
                    await asyncio.to_thread(session.ensure_valid_operator_token)
                    result = await asyncio.to_thread(session.call_tool, name, arguments)

                result = await _await_async_job_if_needed(session, name, result)
                return [TextContent(type="text", text=json.dumps(result, ensure_ascii=False, indent=2))]
            except CredentialsExpiredError as exc:
                return [TextContent(type="text", text=json.dumps(
                    {"ok": False, "error": "credentials_expired", "message": str(exc)},
                    ensure_ascii=False, indent=2,
                ))]
            except ApiError as exc:
                return [TextContent(type="text", text=json.dumps(
                    {"ok": False, "error": "gateway_api_error", "status": exc.status,
                     "path": exc.path, "body": exc.body, "message": exc.message or f"HTTP {exc.status}"},
                    ensure_ascii=False, indent=2,
                ))]
            except Exception as exc:
                return [TextContent(type="text", text=json.dumps(
                    {"ok": False, "error": type(exc).__name__, "message": str(exc)},
                    ensure_ascii=False, indent=2,
                ))]

        # --- Bot mode: per-operator session pool, _operator_open_id required ---
        # Extract and strip the reserved operator identity field so it never
        # leaks into the downstream tool's argument schema.
        operator_open_id: str | None = None
        if isinstance(arguments, dict):
            raw = arguments.pop(OPERATOR_OPEN_ID_KEY, None)
            if isinstance(raw, str) and raw.strip():
                operator_open_id = raw.strip()

        if not operator_open_id:
            return [
                TextContent(
                    type="text",
                    text=json.dumps(
                        {
                            "ok": False,
                            "error": "missing_operator_open_id",
                            "message": (
                                f"arguments 缺少必填字段 {OPERATOR_OPEN_ID_KEY!r}。"
                                "请传入当前会话发送者的飞书 open_id（形如 ou_xxx），"
                                "通常来自飞书群消息的 sender.sender_id.open_id。"
                            ),
                        },
                        ensure_ascii=False,
                        indent=2,
                    ),
                )
            ]

        try:
            session = await _ensure_session(operator_open_id)
            await asyncio.to_thread(session.ensure_valid_operator_token)
            try:
                result = await asyncio.to_thread(session.call_tool, name, arguments)
            except ApiError as exc:
                if exc.status != 401:
                    raise
                # MCP Session Token rejected by gateway — re-bootstrap once and retry.
                logger.info(
                    "MCP Session Token 被拒绝 (operator=%s, 401)，重新 bootstrap 后重试",
                    operator_open_id,
                )
                await _drop_session(operator_open_id)
                session = await _ensure_session(operator_open_id)
                await asyncio.to_thread(session.ensure_valid_operator_token)
                result = await asyncio.to_thread(session.call_tool, name, arguments)

            # Stale CallerContext: roles changed after bootstrap (e.g. no_service_access).
            # Drop the old session and retry once with a freshly-bootstrapped context.
            if _is_session_stale_result(result):
                logger.info(
                    "检测到 session 失效或权限变更 (operator=%s)，重新 bootstrap 后重试",
                    operator_open_id,
                )
                await _drop_session(operator_open_id)
                session = await _ensure_session(operator_open_id)
                await asyncio.to_thread(session.ensure_valid_operator_token)
                result = await asyncio.to_thread(session.call_tool, name, arguments)

            # Auto-poll async jobs so the Agent gets the final result
            result = await _await_async_job_if_needed(session, name, result)

            return [
                TextContent(
                    type="text",
                    text=json.dumps(result, ensure_ascii=False, indent=2),
                )
            ]
        except ApiError as exc:
            return [
                TextContent(
                    type="text",
                    text=json.dumps(
                        {
                            "ok": False,
                            "error": "gateway_api_error",
                            "status": exc.status,
                            "path": exc.path,
                            "body": exc.body,
                            "message": exc.message or f"HTTP {exc.status}",
                            "operator_open_id": operator_open_id,
                        },
                        ensure_ascii=False,
                        indent=2,
                    ),
                )
            ]
        except Exception as exc:
            return [
                TextContent(
                    type="text",
                    text=json.dumps(
                        {
                            "ok": False,
                            "error": type(exc).__name__,
                            "message": str(exc),
                            "operator_open_id": operator_open_id,
                        },
                        ensure_ascii=False,
                        indent=2,
                    ),
                )
            ]

    return server


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


async def _main() -> None:
    logging.basicConfig(
        level=os.getenv("MCP_GATEWAY_LOG_LEVEL", "WARNING").upper(),
        format="%(asctime)s %(name)s %(levelname)s %(message)s",
        stream=sys.stderr,
    )
    global _OAUTH_MODE
    _OAUTH_MODE = _is_oauth_mode_requested()
    logger.info(
        "启动模式: %s (callback_port=%s)",
        "OAuth" if _OAUTH_MODE else "Bot",
        _get_oauth_callback_port() if _OAUTH_MODE else "N/A",
    )
    # Populate the in-memory catalog from the gateway before the stdio loop
    # starts answering tools/list.  Runs in a worker thread because the
    # fetch uses the sync httpx client — keeps the event loop unblocked.
    await asyncio.to_thread(_initialize_catalog)
    server = create_server()
    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


def main() -> None:
    asyncio.run(_main())


if __name__ == "__main__":
    main()
