"""Direct HTTP boundary and explicit internal-network configuration."""
import hashlib
import hmac
import json
import os
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from unittest.mock import patch
from scripts.profit_alert_http import BASE, Client


class ClientTest(unittest.TestCase):
    def test_environment_and_url_policy(self):
        env = dict(PROFIT_API_URL='http://yuexin-bmp-agent-task:8080',
                   PROFIT_API_KEY='profit-alert-test', PROFIT_API_SECRET='synthetic-secret')
        with patch.dict(os.environ, env, clear=True):
            with self.assertRaises(ValueError): Client.from_env()
            os.environ['PROFIT_API_ALLOW_HTTP'] = 'true'
            self.assertEqual(Client.from_env().url, env['PROFIT_API_URL'])
        for url in ('https://example.test/prefix', 'https://user:pass@example.test',
                    'https://example.test?x=1', 'https://example.test/#fragment'):
            with self.assertRaises(ValueError): Client(url, 'worker', 'secret')

    def test_direct_request_path_and_signed_bytes(self):
        observed = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_POST(self):
                body = self.rfile.read(int(self.headers['Content-Length']))
                canonical = '\n'.join(('POST', self.path, self.headers['X-Agent-Timestamp'],
                                      self.headers['X-Agent-Nonce'], hashlib.sha256(body).hexdigest()))
                observed.append((self.path, json.loads(body), self.headers['X-Agent-Key'],
                    bool(self.headers['X-Request-Id']), hmac.compare_digest(
                        self.headers['X-Agent-Signature'],
                        hmac.new(b'synthetic-secret', canonical.encode(), hashlib.sha256).hexdigest())))
                response = b'{"code":"OK","data":{"accepted":true}}'
                self.send_response(200)
                self.end_headers()
                self.wfile.write(response)
        server = HTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            c = Client(f'http://127.0.0.1:{server.server_port}/', 'profit-alert-test',
                       'synthetic-secret', allow_http=True)
            for endpoint in ('/domestic/query', '/international/daily', '/deliveries',
                             '/deliveries/query', '/deliveries/readback'):
                self.assertEqual(c.call(endpoint, {'金额': '1.00'})['accepted'], True)
                self.assertEqual(observed[-1], (BASE + endpoint, {'金额': '1.00'},
                                               'profit-alert-test', True, True))
        finally:
            server.shutdown()
            thread.join()
            server.server_close()


if __name__ == '__main__': unittest.main()
