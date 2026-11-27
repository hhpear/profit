"""Offline auth tests use synthetic credentials only; no live token is changed."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch
import mcp_gateway_core as core
import mcp_gateway_client as client
import query_daily as bridge

class LifecycleTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'credentials.json'
        self.base = 'http://synthetic.invalid'
        self.now = datetime.now(timezone.utc)
        self.creds = {
            'auth_source': 'feishu_oauth', 'gateway_base_url': self.base,
            'mcp_session': {'token': 'synthetic', 'expires_at': (self.now + timedelta(hours=1)).isoformat()},
            'feishu_refresh_token': 'synthetic-refresh',
            'feishu_refresh_token_expires_at': (self.now + timedelta(days=1)).isoformat(),
            'operator_token': {'token': 'synthetic-op'}, 'metadata': {'bootstrapped_at': self.now.isoformat()},
        }
        self.save()

    def save(self):
        self.path.write_text(json.dumps(self.creds))

    def test_valid_credentials_do_not_bootstrap_bot(self):
        with patch.object(core, 'post_json', side_effect=AssertionError('no network')):
            self.assertTrue(core.GatewaySession(core.BootstrapConfig(credentials_file=self.path)).bootstrap())

    def test_expired_session_refreshes_and_persists_rotated_credentials(self):
        self.creds['mcp_session']['expires_at'] = (self.now - timedelta(hours=1)).isoformat()
        self.save()
        reply = {'body': {'mcp_session_token': {'token': 'new-session', 'expires_in': 3600},
                         'feishu_refresh_token': 'rotated-refresh', 'operator_token': {'token': 'new-op'}}}
        with patch.object(core, 'post_json', return_value=reply) as post:
            session = core.GatewaySession(core.BootstrapConfig(base_url=self.base, credentials_file=self.path))
            session.bootstrap()
            self.assertEqual('/auth/oauth/feishu/refresh', post.call_args.kwargs['path'])
        saved = json.loads(self.path.read_text())
        self.assertEqual('rotated-refresh', saved['feishu_refresh_token'])
        self.assertEqual('new-session', saved['mcp_session']['token'])
        self.assertEqual(0o600, self.path.stat().st_mode & 0o777)
        self.assertFalse(list(self.path.parent.glob('.creds-*')))

    def test_expired_refresh_requires_login_without_browser(self):
        self.creds['mcp_session']['expires_at'] = (self.now - timedelta(hours=1)).isoformat()
        self.creds['feishu_refresh_token_expires_at'] = (self.now - timedelta(hours=1)).isoformat()
        self.save()
        with patch.object(core, 'post_json', side_effect=AssertionError('no network')):
            with self.assertRaises(core.CredentialsExpiredError):
                core.GatewaySession(core.BootstrapConfig(credentials_file=self.path)).bootstrap()

    def test_wrong_gateway_and_invalid_credentials_cannot_fall_back(self):
        for content in [self.creds, {'auth_source': 'other'}]:
            self.path.write_text(json.dumps(content))
            with patch.dict(os.environ, {'MCP_GATEWAY_BASE_URL': 'http://other.invalid',
                                         'MCP_GATEWAY_CREDENTIALS_FILE': str(self.path)}):
                with patch.object(bridge, 'GatewaySession', side_effect=AssertionError('no fallback')):
                    with self.assertRaises(core.CredentialsExpiredError):
                        bridge.query('2026-11-26', str(self.path))

    def test_decimals_preserved_before_float_conversion(self):
        parsed = client.parse_jsonish('{"profit_amount":0.1234567890123456789,"row_count":1}')
        self.assertEqual('0.1234567890123456789', parsed['profit_amount'])
        self.assertEqual(1, parsed['row_count'])

    def test_bridge_locks_credentials_and_omits_operator_identity(self):
        with patch.dict(os.environ, {'MCP_GATEWAY_BASE_URL': self.base,
                                     'MCP_GATEWAY_CREDENTIALS_FILE': str(self.path)}):
            with patch.object(bridge, 'GatewaySession') as session, patch.object(bridge.fcntl, 'flock') as flock:
                session.return_value.list_tools.return_value = [{'name': 'billing_mcp__query_daily_stats_summary'}]
                session.return_value.call_tool.return_value = {'body': {'content': [{'text': '{"profit_amount":0.1234567890123456789}'}]}}
                with patch('sys.argv', ['query_daily.py', '2026-11-26']), contextlib.redirect_stdout(io.StringIO()) as out:
                    bridge.main()
                flock.assert_called_once()
                args = session.return_value.call_tool.call_args.args[1]
                self.assertNotIn('_operator_open_id', args)
                self.assertEqual(['enterprise_id','account_id','send_channel_id'], args['group_by'])
                self.assertIn('0.1234567890123456789', out.getvalue())

if __name__ == '__main__':
    unittest.main()
