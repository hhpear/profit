#!/usr/bin/env python3
"""Non-interactive OAuth gateway bridge. Never starts a browser or logs tokens."""
import json, os, sys
import fcntl
from urllib.error import URLError
from mcp_gateway_core import CredentialsExpiredError
from pathlib import Path
from mcp_gateway_core import GatewaySession, build_config_from_env
from mcp_gateway_client import ApiError

def main():
    day = sys.argv[1]
    from datetime import date
    date.fromisoformat(day)
    path = os.environ.get("MCP_GATEWAY_CREDENTIALS_FILE", "")
    if not path or not Path(path).is_file():
        raise CredentialsExpiredError("credentials_not_configured")
    # Lock a stable sibling file: credentials themselves are atomically replaced on refresh.
    with open(path + ".lock", "a") as lock:
        os.chmod(path + ".lock", 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX)
        query(day, path)


def query(day, path):
    config = build_config_from_env()
    # Explicit OAuth credentials only; never fall back to Bot/env identity.
    from mcp_gateway_core import _try_load_credentials
    creds = _try_load_credentials(Path(path))
    if creds is None:
        raise CredentialsExpiredError("credentials_invalid")
    if creds.get("gateway", {}).get("base_url", "").rstrip("/") != (config.base_url or "").rstrip("/"):
        raise CredentialsExpiredError("gateway_mismatch")
    session = GatewaySession(config)
    session.bootstrap()
    tool = "billing_mcp__query_daily_stats_summary"
    if tool not in {t["name"] for t in session.list_tools()}:
        raise RuntimeError("tool_unavailable")
    response = session.call_tool(tool, {
        "start_time": day + " 00:00:00", "end_time": day + " 23:59:59",
        "source_platform": "Pigeon",
        "group_by": ["enterprise_id", "account_id", "send_channel_id"],
    })
    # Parse decimals before Java receives them; amounts never pass through binary floats.
    def normalize(value):
        if isinstance(value, str) and value.lstrip().startswith(("{", "[")):
            try:
                return normalize(json.loads(value, parse_float=str))
            except (ValueError, TypeError):
                return value
        if isinstance(value, dict):
            return {k: normalize(v) for k, v in value.items()}
        if isinstance(value, list):
            return [normalize(v) for v in value]
        return value
    print(json.dumps(normalize(response), ensure_ascii=False))

if __name__ == "__main__":
    try:
        main()
    except CredentialsExpiredError:
        print(json.dumps({"bridgeError": "AUTH_REQUIRED", "status": 401}))
        sys.exit(1)
    except (URLError, TimeoutError, ConnectionError):
        print(json.dumps({"bridgeError": "TRANSPORT_FAILED", "retryable": True}))
        sys.exit(1)
    except ApiError as exc:
        print(json.dumps({"bridgeError": "GATEWAY_REJECTED", "status": exc.status, "retryable": exc.status in (None, 429, 502, 503, 504)}))
        sys.exit(1)
    except Exception:
        print(json.dumps({"bridgeError": "AUTH_OR_TRANSPORT_FAILED"}))
        sys.exit(1)
