"""Programmatic API layer for MCP Gateway auth and tool calls.

Wraps the low-level functions from ``mcp_gateway_client`` in a stateful
``GatewaySession`` that manages the bundle lifecycle: bootstrap, token
refresh, tools/list, and tools/call.
"""

from __future__ import annotations

import json
import logging
import os
import stat
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from mcp_gateway_client import (
    ApiError,
    UsageError,
    build_gateway_auth_headers,
    expect_dict,
    expect_dict_body,
    format_gateway_response,
    post_json,
    require_non_empty_str,
    resolve_gateway_base_url,
    resolve_union_identity,
    utc_now_iso,
)

logger = logging.getLogger(__name__)


@dataclass
class BootstrapConfig:
    """All parameters needed to bootstrap a gateway session."""

    base_url: str | None = None
    open_id: str | None = None
    union_id: str | None = None
    context_file: Path | None = None
    user_file: Path | None = None
    feishu_api_base_url: str | None = None
    feishu_app_id: str | None = None
    feishu_app_secret: str | None = None
    conversation_id: str = ""
    conversation_type: str = "single"
    task_key: str = ""
    owner_user_id: str | None = None
    source: str = "mcp-stdio-server"
    mcp_ttl_seconds: int = 3600
    operator_ttl_seconds: int = 60
    timeout_seconds: float = 15.0
    credentials_file: Path | None = None  # OAuth credentials path


def build_config_from_env() -> BootstrapConfig:
    """Build a ``BootstrapConfig`` from environment variables."""
    context_file_str = os.getenv("MCP_GATEWAY_CONTEXT_FILE")
    user_file_str = os.getenv("MCP_GATEWAY_USER_FILE")

    credentials_file: Path | None = None
    creds_env = os.getenv("MCP_GATEWAY_CREDENTIALS_FILE")
    if creds_env is not None:
        # Empty string = explicitly disabled
        if creds_env.strip():
            credentials_file = Path(creds_env.strip())
    else:
        default = Path.home() / ".mcp-gateway" / "credentials.json"
        if default.exists():
            credentials_file = default

    return BootstrapConfig(
        base_url=os.getenv("MCP_GATEWAY_BASE_URL"),
        open_id=os.getenv("MCP_GATEWAY_OPEN_ID"),
        union_id=os.getenv("MCP_GATEWAY_UNION_ID"),
        context_file=Path(context_file_str) if context_file_str else None,
        user_file=Path(user_file_str) if user_file_str else None,
        feishu_api_base_url=os.getenv("MCP_GATEWAY_FEISHU_API_BASE_URL"),
        feishu_app_id=os.getenv("FEISHU_APP_ID") or os.getenv("MCP_GATEWAY_FEISHU_APP_ID"),
        feishu_app_secret=os.getenv("FEISHU_APP_SECRET") or os.getenv("MCP_GATEWAY_FEISHU_APP_SECRET"),
        conversation_id=os.getenv("MCP_GATEWAY_CONVERSATION_ID", f"mcp-stdio-{os.getpid()}"),
        conversation_type=os.getenv("MCP_GATEWAY_CONVERSATION_TYPE", "single"),
        task_key=os.getenv("MCP_GATEWAY_TASK_KEY", "mcp-stdio-session"),
        owner_user_id=os.getenv("MCP_GATEWAY_OWNER_USER_ID"),
        source=os.getenv("MCP_GATEWAY_SOURCE", "mcp-stdio-server"),
        mcp_ttl_seconds=int(os.getenv("MCP_GATEWAY_MCP_TTL", "3600")),
        operator_ttl_seconds=int(os.getenv("MCP_GATEWAY_OPERATOR_TTL", "60")),
        timeout_seconds=float(os.getenv("MCP_GATEWAY_TIMEOUT", "15")),
        credentials_file=credentials_file,
    )


class GatewaySession:
    """Manages the full bundle lifecycle for MCP Gateway interactions.

    Handles bootstrap, token refresh, tools/list, and tools/call.
    All methods are synchronous (use ``asyncio.to_thread`` from async callers).
    """

    def __init__(self, config: BootstrapConfig) -> None:
        self._config = config
        self._bundle: dict[str, Any] | None = None
        self._operator_issued_at: datetime | None = None

    @property
    def is_bootstrapped(self) -> bool:
        return self._bundle is not None

    def bootstrap(self) -> dict[str, Any]:
        """Execute the full 3-step auth chain and cache the bundle.

        When credentials_file is configured, tries the OAuth path first:
        1. Valid mcp_session_token  → use directly, no network request.
        2. Expired mcp_session + valid feishu_refresh_token → silent refresh.
        3. Both expired (~every 30d) → raise CredentialsExpiredError.
        Otherwise falls through to the existing Bot/env identity flow.
        """
        cfg = self._config

        # --- OAuth credentials path (highest priority) ---
        if cfg.credentials_file:
            creds = _try_load_credentials(cfg.credentials_file)
            if creds is not None:
                if not _is_mcp_token_expired(creds):
                    self._bundle = creds
                    self._operator_issued_at = _parse_operator_issued_at(creds)
                    logger.info("从 credentials 文件加载会话（OAuth 路径）")
                    return self._bundle

                if not _is_refresh_token_expired(creds):
                    logger.info("mcp_session_token 已过期，尝试静默 refresh")
                    new_bundle = _silent_refresh(creds, cfg)
                    _save_credentials(cfg.credentials_file, new_bundle)
                    self._bundle = new_bundle
                    self._operator_issued_at = _parse_operator_issued_at(new_bundle)
                    return self._bundle

                raise CredentialsExpiredError(
                    "OAuth 会话已过期（refresh token 已失效），请重新运行：\n"
                    f"  python3 mcp_gateway_client.py login --base-url <gateway_url>"
                )

        # --- Existing Bot / env identity flow (unchanged) ---
        base_url = resolve_gateway_base_url(cfg.base_url)

        identity = resolve_union_identity(
            explicit_union_id=cfg.union_id,
            explicit_open_id=cfg.open_id,
            context_file=cfg.context_file,
            user_file=cfg.user_file,
            feishu_api_base_url=cfg.feishu_api_base_url,
            feishu_app_id=cfg.feishu_app_id,
            feishu_app_secret=cfg.feishu_app_secret,
            gateway_base_url=base_url,
            timeout_seconds=cfg.timeout_seconds,
        )

        conversation = {
            "conversation_id": cfg.conversation_id,
            "conversation_type": cfg.conversation_type,
        }

        resolve_payload: dict[str, Any] = {
            "union_id": identity["union_id"],
            **conversation,
            "task_key": cfg.task_key,
        }
        if cfg.owner_user_id:
            resolve_payload["owner_user_id"] = cfg.owner_user_id

        resolved = post_json(
            base_url=base_url,
            path="/internal/agent-sessions/resolve",
            payload=resolve_payload,
            timeout_seconds=cfg.timeout_seconds,
        )
        resolve_body = expect_dict_body(resolved, "/internal/agent-sessions/resolve")
        operator = expect_dict(resolve_body, "operator")
        session = expect_dict(resolve_body, "session")
        owner_user_id = require_non_empty_str(
            session.get("owner_user_id"), "session.owner_user_id"
        )

        mcp_response = post_json(
            base_url=base_url,
            path="/internal/agent-sessions/mcp/token",
            payload={
                "agent_session_id": require_non_empty_str(
                    session.get("agent_session_id"), "session.agent_session_id"
                ),
                **conversation,
                "owner_user_id": owner_user_id,
                "ttl_seconds": cfg.mcp_ttl_seconds,
            },
            timeout_seconds=cfg.timeout_seconds,
        )
        mcp_body = expect_dict_body(mcp_response, "/internal/agent-sessions/mcp/token")

        operator_response = post_json(
            base_url=base_url,
            path="/internal/jwt/operator-token",
            payload={
                "source": cfg.source,
                "operator": operator,
                "conversation": conversation,
                "expires_in": cfg.operator_ttl_seconds,
            },
            timeout_seconds=cfg.timeout_seconds,
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
                "task_key": cfg.task_key,
                "owner_user_id": owner_user_id,
            },
            "session": session,
            "mcp_session": mcp_body,
            "operator_token": operator_body,
            "metadata": {
                "source": cfg.source,
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

        self._bundle = bundle
        self._operator_issued_at = datetime.now(tz=timezone.utc)
        logger.info("bootstrap 完成: base_url=%s", base_url)
        return bundle

    def ensure_valid_operator_token(self) -> None:
        """Refresh operator_token if it is expiring within 10 seconds."""
        if self._bundle is None:
            raise RuntimeError("尚未执行 bootstrap")
        if self._is_operator_token_expiring_soon():
            self._refresh_operator_token()

    def list_tools(self) -> list[dict[str, Any]]:
        """Call gateway tools/list and return raw tool definitions."""
        if self._bundle is None:
            raise RuntimeError("尚未执行 bootstrap")
        self.ensure_valid_operator_token()
        base_url = require_non_empty_str(
            expect_dict(self._bundle, "gateway").get("base_url"), "gateway.base_url"
        )
        response = post_json(
            base_url=base_url,
            path="/mcp",
            payload={"method": "tools/list", "params": {}},
            headers=build_gateway_auth_headers(self._bundle),
            timeout_seconds=self._config.timeout_seconds,
        )
        body = response.get("body", {})
        return body.get("tools", []) if isinstance(body, dict) else []

    def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        """Call a gateway tool with automatic auth self-healing on 401.

        Recovery ladder on 401:
        1. Refresh operator_token and retry (cheap, handles operator_jwt_expired).
        2. Still 401 → full re-bootstrap and retry (handles
           mcp_session_token_invalid/expired, which operator refresh cannot fix).
        3. Still 401 → raise (真实的权限/身份问题，不应无限重试).
        """
        if self._bundle is None:
            raise RuntimeError("尚未执行 bootstrap")
        self.ensure_valid_operator_token()
        try:
            return self._do_call_tool(name, arguments)
        except ApiError as exc:
            if exc.status != 401:
                raise
            logger.info("tools/call 返回 401,尝试刷新 operator_token 后重试")
            try:
                self._refresh_operator_token()
                return self._do_call_tool(name, arguments)
            except ApiError as exc2:
                if exc2.status != 401:
                    raise
                logger.warning(
                    "刷新 operator_token 后仍 401,触发完整 bootstrap 重建会话"
                )
                self.bootstrap()
                return self._do_call_tool(name, arguments)

    def _do_call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        base_url = require_non_empty_str(
            expect_dict(self._bundle, "gateway").get("base_url"),  # type: ignore[arg-type]
            "gateway.base_url",
        )
        response = post_json(
            base_url=base_url,
            path="/mcp",
            payload={
                "method": "tools/call",
                "params": {"name": name, "arguments": arguments},
            },
            headers=build_gateway_auth_headers(self._bundle),  # type: ignore[arg-type]
            timeout_seconds=self._config.timeout_seconds,
        )
        return format_gateway_response(response)

    def _refresh_operator_token(self) -> None:
        if self._bundle is None:
            raise RuntimeError("尚未执行 bootstrap")
        conversation = expect_dict(self._bundle, "conversation")
        operator = expect_dict(expect_dict(self._bundle, "identity"), "operator")
        metadata = expect_dict(self._bundle, "metadata")
        source = require_non_empty_str(metadata.get("source"), "metadata.source")
        base_url = require_non_empty_str(
            expect_dict(self._bundle, "gateway").get("base_url"), "gateway.base_url"
        )
        operator_response = post_json(
            base_url=base_url,
            path="/internal/jwt/operator-token",
            payload={
                "source": source,
                "operator": operator,
                "conversation": {
                    "conversation_id": require_non_empty_str(
                        conversation.get("conversation_id"), "conversation.conversation_id"
                    ),
                    "conversation_type": require_non_empty_str(
                        conversation.get("conversation_type"), "conversation.conversation_type"
                    ),
                },
                "expires_in": self._config.operator_ttl_seconds,
            },
            timeout_seconds=self._config.timeout_seconds,
        )
        operator_body = expect_dict_body(operator_response, "/internal/jwt/operator-token")
        self._bundle["operator_token"] = operator_body
        metadata["operator_token_refreshed_at"] = utc_now_iso()
        self._operator_issued_at = datetime.now(tz=timezone.utc)
        logger.info("operator_token 已刷新")

    def _is_operator_token_expiring_soon(self, buffer_seconds: int = 10) -> bool:
        if self._operator_issued_at is None:
            return True
        elapsed = (datetime.now(tz=timezone.utc) - self._operator_issued_at).total_seconds()
        return elapsed >= (self._config.operator_ttl_seconds - buffer_seconds)


# ---------------------------------------------------------------------------
# OAuth credentials helpers
# ---------------------------------------------------------------------------

class CredentialsExpiredError(RuntimeError):
    """OAuth credentials exist but both mcp_session_token and refresh_token are expired."""


def _try_load_credentials(path: Path) -> dict[str, Any] | None:
    """Load credentials.json and convert to bundle-compatible dict.

    Returns None if the file doesn't exist or can't be parsed (silent fallback
    to the Bot flow). Never raises.
    """
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None
    if not isinstance(raw, dict) or raw.get("auth_source") != "feishu_oauth":
        return None

    # Normalise to bundle format so GatewaySession internals don't need changes.
    try:
        gateway_base_url = raw.get("gateway_base_url") or ""
        return {
            "auth_source": "feishu_oauth",
            "gateway": {"base_url": gateway_base_url},
            "identity": {
                "union_id": raw.get("user", {}).get("union_id", ""),
                "operator": raw.get("operator", {}),
            },
            "conversation": raw.get("conversation", {}),
            "session": raw.get("session", {}),
            "mcp_session": raw.get("mcp_session", {}),
            "operator_token": raw.get("operator_token", {}),
            "feishu_refresh_token": raw.get("feishu_refresh_token", ""),
            "feishu_refresh_token_expires_at": raw.get("feishu_refresh_token_expires_at", ""),
            "metadata": raw.get("metadata", {"source": "feishu_oauth_login"}),
        }
    except Exception:
        return None


def _is_mcp_token_expired(creds: dict[str, Any], buffer_seconds: int = 60) -> bool:
    """True if mcp_session_token is expired or expiring within buffer_seconds."""
    try:
        expires_at_str = creds["mcp_session"]["expires_at"]
        expires_at = datetime.fromisoformat(expires_at_str.replace("Z", "+00:00"))
        return datetime.now(tz=timezone.utc) >= expires_at - _timedelta(seconds=buffer_seconds)
    except Exception:
        return True  # conservative: treat as expired


def _is_refresh_token_expired(creds: dict[str, Any], buffer_seconds: int = 300) -> bool:
    """True if feishu_refresh_token is expired or expiring within buffer_seconds."""
    try:
        expires_at_str = creds["feishu_refresh_token_expires_at"]
        if not expires_at_str:
            return True
        expires_at = datetime.fromisoformat(expires_at_str.replace("Z", "+00:00"))
        return datetime.now(tz=timezone.utc) >= expires_at - _timedelta(seconds=buffer_seconds)
    except Exception:
        return True  # conservative: treat as expired


def _timedelta(*, seconds: int) -> Any:
    from datetime import timedelta
    return timedelta(seconds=seconds)


def _silent_refresh(creds: dict[str, Any], config: BootstrapConfig) -> dict[str, Any]:
    """Call /auth/oauth/feishu/refresh and return a new bundle-compatible dict."""
    base_url = creds.get("gateway", {}).get("base_url") or resolve_gateway_base_url(config.base_url)
    conversation = creds.get("conversation", {})

    response = post_json(
        base_url=base_url,
        path="/auth/oauth/feishu/refresh",
        payload={
            "refresh_token": creds.get("feishu_refresh_token", ""),
            "conversation_id": conversation.get("conversation_id", ""),
            "conversation_type": conversation.get("conversation_type", "single"),
            "task_key": conversation.get("task_key", ""),
        },
        timeout_seconds=config.timeout_seconds,
    )
    body = expect_dict_body(response, "/auth/oauth/feishu/refresh")

    now = datetime.now(tz=timezone.utc)
    mcp_token = body.get("mcp_session_token", {})
    refresh_expires_in = int(body.get("feishu_refresh_token_expires_in", 2592000))
    refresh_expires_at = (
        now.replace(microsecond=0).isoformat().replace("+00:00", "Z")
    )
    # Compute actual expiry timestamps
    mcp_expires_in = int(mcp_token.get("expires_in", 3600))
    from datetime import timedelta
    mcp_expires_at = (now + timedelta(seconds=mcp_expires_in)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    refresh_expires_at = (now + timedelta(seconds=refresh_expires_in)).replace(microsecond=0).isoformat().replace("+00:00", "Z")

    operator = body.get("operator", creds.get("identity", {}).get("operator", {}))
    session = body.get("session", creds.get("session", {}))

    return {
        "auth_source": "feishu_oauth",
        "gateway": {"base_url": base_url},
        "identity": {
            "union_id": operator.get("union_id", ""),
            "operator": operator,
        },
        "conversation": conversation,
        "session": session,
        "mcp_session": {
            "token": mcp_token.get("token", ""),
            "expires_in": mcp_expires_in,
            "expires_at": mcp_expires_at,
        },
        "operator_token": body.get("operator_token", {}),
        "feishu_refresh_token": body.get("feishu_refresh_token", ""),
        "feishu_refresh_token_expires_at": refresh_expires_at,
        "metadata": {
            "source": "feishu_oauth_login",
            "bootstrapped_at": utc_now_iso(),
        },
    }


def _save_credentials(path: Path, bundle: dict[str, Any]) -> None:
    """Atomically write credentials.json as bundle-compatible dict, chmod 600."""
    # Reconstruct credentials.json format from bundle
    operator = bundle.get("identity", {}).get("operator", {})
    user = {
        "open_id": operator.get("feishu_id", ""),
        "union_id": operator.get("union_id", ""),
        "user_id": operator.get("user_id", ""),
        "user_name": operator.get("user_name", ""),
    }
    mcp_session = bundle.get("mcp_session", {})
    credentials = {
        "auth_source": "feishu_oauth",
        "gateway_base_url": bundle.get("gateway", {}).get("base_url", ""),
        "saved_at": utc_now_iso(),
        "user": user,
        "operator": operator,
        "session": bundle.get("session", {}),
        "mcp_session": mcp_session,
        "operator_token": bundle.get("operator_token", {}),
        "feishu_refresh_token": bundle.get("feishu_refresh_token", ""),
        "feishu_refresh_token_expires_at": bundle.get("feishu_refresh_token_expires_at", ""),
        "conversation": bundle.get("conversation", {}),
        "metadata": bundle.get("metadata", {}),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(credentials, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    # Atomic write via temp file + rename
    fd, tmp_path = tempfile.mkstemp(dir=path.parent, prefix=".creds-")
    try:
        os.write(fd, content.encode("utf-8"))
        os.fchmod(fd, stat.S_IRUSR | stat.S_IWUSR)  # chmod 600
        os.close(fd)
        os.replace(tmp_path, path)
    except Exception:
        os.close(fd)
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        raise


def _parse_operator_issued_at(creds: dict[str, Any]) -> datetime:
    """Parse the operator_token issuance time from metadata.bootstrapped_at."""
    try:
        ts = creds.get("metadata", {}).get("bootstrapped_at", "")
        return datetime.fromisoformat(ts.replace("Z", "+00:00"))
    except Exception:
        return datetime.fromtimestamp(0, tz=timezone.utc)  # trigger immediate refresh
