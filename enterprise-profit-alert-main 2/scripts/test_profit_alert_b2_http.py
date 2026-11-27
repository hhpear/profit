"""Invoked by Java contract test; only a numeric loopback port is accepted."""
import os
import json
import unittest
from scripts.profit_alert_http import Client,ApiError,digest

@unittest.skipUnless(os.environ.get('PROFIT_B2_LOOPBACK_PORT'), 'run via Java PythonHttpContractTest')
class JavaHttpTest(unittest.TestCase):
    def setUp(self):
        port=int(os.environ['PROFIT_B2_LOOPBACK_PORT'])
        self.client=Client(f'http://127.0.0.1:{port}','synthetic-worker','synthetic-secret',allow_http=True)
    def test_real_source_service_domestic_and_international(self):
        from scripts.profit_alert_b1 import domestic
        from scripts.fetch_enterprise_profit_alert_pigeon import validate
        from scripts.run_enterprise_profit_alert_daily import active_operating_rules
        from scripts.validate_enterprise_profit_alert_governance import read
        data=domestic(self.client,'2026-11-26','2026-11-26')
        self.assertEqual([row['profit'] for row in data['rows']],['20.00','20.00'])
        detail=domestic(self.client,'2026-11-26','2026-11-26',True)
        self.assertEqual(detail['pagination']['total'],2)
        for _ in range(2):
            result=self.client.call('/international/daily',{'dataDay':'2026-11-26'})
            validate(result['receipt'],'2026-11-26',read(active_operating_rules()))
        request=dict(startDate='2026-11-26',endDate='2026-11-26',customerTypes=['Customer','QuasiCustomer','Agent','CHANNEL'],taxBasis='INCLUDED',indicatorView='OPERATING',page={'number':1,'size':1})
        first=self.client.call('/domestic/query',request)
        with self.assertRaises(ApiError) as changed:
            self.client.call('/domestic/query',{**request,'queryId':first['queryId'],'endDate':'2026-11-25'})
        self.assertEqual(changed.exception.status,400)
        with self.assertRaises(ApiError) as missing:
            self.client.call('/domestic/query',{**request,'queryId':'expired-or-missing'})
        self.assertEqual(missing.exception.status,409)

    def test_readonly_source_probe(self):
        from tempfile import TemporaryDirectory
        from pathlib import Path
        from scripts.probe_profit_alert_sources import probe
        with TemporaryDirectory() as directory:
            result=probe(self.client,'2026-11-26',Path(directory)/'evidence')
            self.assertEqual(result['status'],'SOURCE_CHECKS_PASSED')
            self.assertFalse(result['delivery_attempted'])
            self.assertFalse(result['business_equivalence_confirmed'])

    def test_signature_authorization_conflict_and_recovery(self):
        c=self.client
        request=dict(startDate='2026-11-26',endDate='2026-11-26',page={'number':1,'size':1},queryId=None,customerTypes=['Customer','QuasiCustomer','Agent','CHANNEL'],taxBasis='INCLUDED',indicatorView='OPERATING')
        one=c.call('/domestic/query',request)
        two=c.call('/domestic/query',{**request,'queryId':one['queryId'],'page':{'number':2,'size':1}})
        self.assertEqual(one['queryId'],two['queryId'])
        self.assertEqual(two['data'][0]['entName'],'合成二')
        with self.assertRaises(ApiError) as missing:c.call('/deliveries/query',{'idempotencyKey':'absent'})
        self.assertEqual(missing.exception.status,404)
        payload=dict(application='enterprise-profit-alert',mode='test',dataDay='2026-11-26',recipientId='synthetic-user',idempotencyKey='b2-synthetic',card={'schema':'2.0','中':{'金额':'1.00'}},cardSha256=digest({'schema':'2.0','中':{'金额':'1.00'}}))
        first=c.call('/deliveries',payload)
        self.assertEqual(c.call('/deliveries',payload)['deliveryId'],first['deliveryId'])
        for _ in range(2):
            verified=c.call('/deliveries/readback',{'deliveryId':first['deliveryId']})
            self.assertEqual(verified['senderAppId'],'synthetic-app')
            self.assertEqual(verified['contentMatch'],'EXACT')
        with self.assertRaises(ApiError) as conflict:c.call('/deliveries',{**payload,'cardSha256':'changed'})
        self.assertEqual(conflict.exception.status,409)
        with self.assertRaises(ApiError) as forbidden:c.call('/deliveries',{**payload,'mode':'production'})
        self.assertEqual(forbidden.exception.status,403)
        c.secret='wrong'
        with self.assertRaises(ApiError) as unauthorized:c.call('/deliveries/query',{'idempotencyKey':'b2-synthetic'})
        self.assertEqual(unauthorized.exception.status,401)

if __name__=='__main__':unittest.main()
