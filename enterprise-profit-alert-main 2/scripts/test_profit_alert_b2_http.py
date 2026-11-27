"""Invoked by Java contract test; only a numeric loopback port is accepted."""
import os
import json
import unittest
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from scripts.profit_alert_http import Client,ApiError,digest

@unittest.skipUnless(os.environ.get('PROFIT_B2_LOOPBACK_PORT'), 'run via Java PythonHttpContractTest')
class JavaHttpTest(unittest.TestCase):
    def setUp(self):
        port=int(os.environ['PROFIT_B2_LOOPBACK_PORT'])
        def transport(path,body,headers):
            try:
                with urlopen(Request(f'http://127.0.0.1:{port}'+path,body,headers),timeout=10) as response:
                    return response.status,json.load(response)
            except HTTPError as error:return error.code,json.load(error)
        self.client=Client('https://loopback.test','synthetic-worker','synthetic-secret',transport=transport)
    def test_signature_authorization_conflict_and_recovery(self):
        c=self.client
        request=dict(startDate='2026-11-26',endDate='2026-11-26',page={'number':1,'size':1},queryId=None)
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
