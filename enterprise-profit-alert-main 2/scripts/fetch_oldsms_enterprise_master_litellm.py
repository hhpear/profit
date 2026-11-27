#!/usr/bin/env python3
"""Compatibility entrypoint: enterprise data is queried only via canonical MCP client."""
import sys
from pathlib import Path
CANONICAL = Path(__file__).resolve().parents[1] / "data_monitor_platform_server_latest/pigeon/scheduled/intl-daily-report/scripts"
sys.path.insert(0, str(CANONICAL))
from query_oldsms_enterprise_mcp import post, decode_response, extract_payload, main
if __name__ == "__main__":
    main()
