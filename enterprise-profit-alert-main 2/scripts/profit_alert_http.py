"""Company HTTP boundary; only read requests are automatically retried."""
import hashlib
import hmac
import json
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

BASE = '/internal/profit-alert/v1'


def canonical(value):
    def check(v):
        if isinstance(v, float):
            raise ValueError('floating point JSON is forbidden')
        if isinstance(v, dict):
            for x in v.values(): check(x)
        if isinstance(v, list):
            for x in v: check(x)
    check(value)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


class ApiError(RuntimeError):
    def __init__(self, code, status=0):
        super().__init__(code)
        self.code, self.status = code, status


class Client:
    def __init__(self, url, key, secret, timeout=120, transport=None):
        if not url.startswith('https://') and transport is None:
            raise ValueError('HTTPS required')
        self.url, self.key, self.secret = url.rstrip('/'), key, secret
        self.timeout, self.transport = timeout, transport

    def call(self, endpoint, payload):
        path, body = BASE + endpoint, canonical(payload)
        readonly = endpoint != '/deliveries'
        for attempt in range(3 if readonly else 1):
            stamp, nonce = str(int(time.time())), uuid.uuid4().hex
            signed = '\n'.join(('POST', path, stamp, nonce, hashlib.sha256(body).hexdigest()))
            headers = {'Content-Type': 'application/json', 'X-Request-Id': uuid.uuid4().hex,
                       'X-Agent-Key': self.key, 'X-Agent-Timestamp': stamp, 'X-Agent-Nonce': nonce,
                       'X-Agent-Signature': hmac.new(self.secret.encode(), signed.encode(), hashlib.sha256).hexdigest()}
            try:
                if self.transport:
                    status, envelope = self.transport(path, body, headers)
                else:
                    try:
                        with urlopen(Request(self.url + path, body, headers), timeout=30 if not readonly else self.timeout) as response:
                            status, envelope = response.status, json.load(response)
                    except HTTPError as error:
                        status, envelope = error.code, json.load(error)
                if status not in (200, 202) or envelope.get('code') != 'OK':
                    raise ApiError(envelope.get('code', 'INVALID_RESPONSE'), status)
                if not isinstance(envelope.get('data'), dict): raise ApiError('INVALID_RESPONSE')
                return envelope['data']
            except (URLError, TimeoutError):
                error = ApiError('TRANSPORT_UNKNOWN')
            except ApiError as exc:
                error = exc
                if exc.status not in (429, 503, 504): raise
            if not readonly or attempt == 2: raise error
            time.sleep(0.1 * 2 ** attempt)
