"""Synthetic v1 service for B1 contract and recovery verification. No network or real delivery."""
import copy
import hashlib
import hmac
import json
from datetime import date,timedelta
from scripts.profit_alert_http import BASE

class MockService:
    """Synthetic service with durable-in-test delivery authority and injectable failures."""
    def __init__(self,alert=True):
        self.alert=alert; self.records={}; self.sent=[]; self.calls=[]; self.fault=None
    def transport(self,path,body,headers):
        canonical='\n'.join(('POST',path,headers['X-Agent-Timestamp'],headers['X-Agent-Nonce'],hashlib.sha256(body).hexdigest()))
        assert hmac.compare_digest(headers['X-Agent-Signature'],hmac.new(b'synthetic-secret',canonical.encode(),hashlib.sha256).hexdigest())
        payload=json.loads(body); endpoint=path.removeprefix(BASE); self.calls.append(endpoint)
        if endpoint=='/domestic/query':
            rows=[]; day=date.fromisoformat(payload['startDate'])
            while day<=date.fromisoformat(payload['endDate']):
                profit='-500' if self.alert and day.isoformat()>='2026-11-24' else '1000'
                rows.append(dict(timeStr=day.isoformat(),entCode='SYNTHETIC-001',entName='合成企业',smsIncome='2000',smsProfit=profit)); day+=timedelta(days=1)
            page=payload['page']; size=page['size']; n=page['number']; q='q-'+payload['startDate']
            data=dict(queryId=q,success=True,code='GB:0000',data=rows[(n-1)*size:n*size],totalCount=len(rows),pageNumber=n,pageSize=size,meta={'consistency':'FROZEN_RECEIPT'})
            if self.fault=='source_changed': data['queryId']='changed' if n>1 else q
            if self.fault=='missing_page': data['totalCount']=len(rows)+1
            if self.fault=='amount': data['data'][0]['smsProfit']=None
        elif endpoint=='/international/daily':
            d=payload['dataDay']; data={'receipt':dict(source_platform='Pigeon',start_time=d+'T00:00:00+08:00',end_time=d+'T23:59:59+08:00',group_by=['enterprise_id','account_id','send_channel_id'],truncated=False,row_count=0,rows=[]),'meta':{}}
        elif endpoint=='/deliveries/query':
            if payload['idempotencyKey'] not in self.records:return 404,{'code':'NOT_FOUND'}
            data=self.records[payload['idempotencyKey']]
        elif endpoint=='/deliveries':
            key=payload['idempotencyKey']
            if key in self.records and any(self.records[key].get(k)!=payload.get(k) for k in ('cardSha256','inputSha256','rulesetSha256')):
                return 409,{'code':'IDEMPOTENCY_CONFLICT'}
            if key not in self.records:
                data={**payload,'deliveryId':key,'state':'SENT','messageId':'msg-'+key}; self.records[key]=data; self.sent.append(payload['recipientId'])
                if self.fault=='disconnect': raise TimeoutError()
            data=self.records[key]
        elif endpoint=='/deliveries/readback':
            record=self.records[payload['deliveryId']]
            data=dict(deliveryId=record['deliveryId'],state='VERIFIED',readbackConfirmed=True,recipientId=record['recipientId'],senderAppId='synthetic-app',contentMatch='EXACT',messageId=record['messageId'])
            if self.fault=='third' and record['recipientId']=='c':data['contentMatch']='MISMATCH'
        else: raise AssertionError(endpoint)
        return 200,{'code':'OK','data':copy.deepcopy(data)}
