"""Offline-ready daily workflow with durable inputs and authoritative delivery recovery."""
import argparse
import fcntl
import json
import os
import sqlite3
import time
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
from scripts.profit_alert_http import Client, ApiError, digest
from scripts.normalize_pigeon_artifact_receipts import atomic_write
from scripts.validate_enterprise_profit_alert_governance import validate_active, validate_output, read, sha
from scripts.run_enterprise_profit_alert_daily import active_operating_rules, international_seed_from_history, validate_recovery_receipt_days
from scripts.query_enterprise_profit_alert_domestic import query
from scripts.fetch_enterprise_profit_alert_pigeon import validate
from scripts.build_enterprise_profit_alert import build_domestic_calendar_history, merge_calendar_history, build_calendar_alert
from scripts.build_enterprise_profit_alert_card import build


class Pending(RuntimeError): pass


def domestic(client, start, end, detail=False):
    query_id = None
    def request(_, old):
        nonlocal query_id
        page = old['page']
        result = client.call('/domestic/query', {'startDate': start, 'endDate': end,
            'customerTypes': old['customerTypes'], 'taxBasis': 'INCLUDED', 'indicatorView': 'OPERATING',
            'page': {'number': page['pageNumber'], 'size': page['pageSize']}, 'queryId': query_id})
        if not result.get('queryId') or (query_id and result['queryId'] != query_id):
            raise RuntimeError('frozen query changed')
        if result.get('pageNumber') != page['pageNumber'] or result.get('pageSize') != page['pageSize']:
            raise RuntimeError('page mismatch')
        if (result.get('meta') or {}).get('consistency') != 'FROZEN_RECEIPT':
            raise RuntimeError('frozen source evidence missing')
        query_id = result['queryId']
        for row in result.get('data', []):
            for field in ('smsIncome', 'smsProfit'):
                if not isinstance(row.get(field), str): raise RuntimeError('amount must be string')
        evidence.append(result)
        return result
    evidence = []
    output = query(start, end, request, current_detail=detail)
    output['gateway_evidence'] = evidence
    return output


def select_history(root, day, mode, version):
    candidates = []
    for frozen in (root / mode).glob('*/frozen.json'):
        package = read(frozen)
        history = package.get('history') or {}
        end = (history.get('coverage') or {}).get('end', '')
        if end < day and history.get('ruleset_version') == version:
            if digest(history) != package.get('sha256'):
                raise RuntimeError('history candidate digest mismatch')
            for source in history.get('input_sources') or []:
                if sha(Path(source['path'])) != source['sha256']:
                    raise RuntimeError('history candidate source mismatch')
            candidate = frozen.parent / 'enterprise-profit-calendar-history.json'
            if candidate.exists() and read(candidate) == history:
                candidates.append((end, candidate))
    if not candidates:
        raise RuntimeError('no compatible local history; provide authorized initial history')
    return max(candidates)[1]


def execute(client, root, day, mode, previous, recipients, release='b1', sender_app=None, assume_notice_success=False, accept_notice_queue=False):
    root = Path(root).resolve()
    if (root / 'RESTORE_REQUIRES_RECONCILIATION.json').exists():
        raise Pending('restored state requires reconciliation before execution')
    root.mkdir(parents=True, exist_ok=True)
    with (root / 'workflow.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        db = sqlite3.connect(root / 'runs.sqlite')
        try:
            db.execute('CREATE TABLE IF NOT EXISTS runs (day TEXT, mode TEXT, stage TEXT, binding TEXT, error TEXT, PRIMARY KEY(day,mode))')
            if 'metadata' not in [row[1] for row in db.execute('PRAGMA table_info(runs)')]:
                db.execute('ALTER TABLE runs ADD COLUMN metadata TEXT')
            db.execute('CREATE TABLE IF NOT EXISTS deliveries (day TEXT, mode TEXT, recipient TEXT, evidence TEXT, PRIMARY KEY(day,mode,recipient))')
            out = root / mode / day
            out.mkdir(parents=True, exist_ok=True)
            def stage(value, error=None):
                db.execute('UPDATE runs SET stage=?,error=? WHERE day=? AND mode=?', (value,error,day,mode)); db.commit()
            pointer, rules_path, rules = validate_active()
            if mode not in ('dry-run', 'test', 'production'): raise ValueError('invalid mode')
            if assume_notice_success and accept_notice_queue:
                raise ValueError('placeholder and real Notice modes cannot be combined')
            if assume_notice_success and mode != 'test':
                raise ValueError('temporary assumed notice is supported in test mode only')
            if mode != 'dry-run' and client.transport is None and not (assume_notice_success or accept_notice_queue):
                raise ValueError('B1 enables simulated delivery only; company rules and recipient authorization require B2')
            if date.fromisoformat(day) >= datetime.now(ZoneInfo('Asia/Shanghai')).date(): raise ValueError('incomplete day')
            if mode != 'dry-run' and (not recipients or len(set(recipients)) != len(recipients) or not sender_app):
                raise ValueError('explicit unique recipients and sender app required')
            binding_fields = {'assume_notice_success': assume_notice_success, 'rules': pointer['ruleset_sha256'], 'recipients': recipients, 'release': release, 'sender': sender_app}
            if accept_notice_queue: binding_fields['accept_notice_queue'] = True
            binding = digest(binding_fields)
            existing = db.execute('SELECT stage,binding FROM runs WHERE day=? AND mode=?', (day,mode)).fetchone()
            if existing and existing[1] != binding: raise RuntimeError('run binding conflict')
            db.execute('INSERT OR IGNORE INTO runs (day,mode,stage,binding,error) VALUES (?,?,?,?,NULL)', (day,mode,'VALIDATING',binding)); db.commit()
            try:
                frozen = out / 'frozen.json'
                if frozen.exists():
                    package = read(frozen)
                    if digest(package['history']) != package['sha256']: raise RuntimeError('frozen input corrupted')
                    history = package['history']
                else:
                    stage('FETCHING')
                    previous_path = Path(previous) if previous else select_history(root, day, mode, rules['version'])
                    prior = read(previous_path)
                    if prior.get('status') != 'SUCCESS' or not prior.get('rows'):
                        raise RuntimeError('validated nonempty initial history required')
                    atomic_write(out/'sources/previous-history.json', prior)
                    atomic_write(out/'sources/operating-rules.json', read(active_operating_rules()))
                    if prior.get('ruleset_version') != rules['version']: raise RuntimeError('history rules incompatible')
                    operating = read(active_operating_rules())
                    pairs = []
                    current = date.fromisoformat(prior['coverage']['end']) + timedelta(days=1)
                    while current <= date.fromisoformat(day):
                        text = current.isoformat()
                        response = client.call('/international/daily', {'dataDay': text})
                        validate(response['receipt'], text, operating)
                        path = out / 'sources' / f'pigeon-{text}.json'
                        atomic_write(path, response['receipt']); atomic_write(path.with_suffix('.envelope.json'), response)
                        pairs.append((path,response['receipt'])); current += timedelta(days=1)
                    validate_recovery_receipt_days(prior, day, pairs)
                    start = (date.fromisoformat(day)-timedelta(days=60)).isoformat()
                    hist = domestic(client,start,day)
                    detail = domestic(client,day,day,True)
                    hp, dp = out/'sources/domestic-history.json', out/'sources/domestic-current.json'
                    atomic_write(hp,hist); atomic_write(dp,detail)
                    history = merge_calendar_history(build_domestic_calendar_history(hist,day),detail,
                        international_seed_from_history(prior),pairs,operating,day)
                    history['ruleset_version'] = rules['version']
                    history['input_sources'] = [{'role': role,'path': str(path),'sha256': sha(path)} for role,path in
                        [('previous_history',out/'sources/previous-history.json'),('operating_rules',out/'sources/operating-rules.json'),('domestic_history',hp),('domestic_current',dp), *[('international_receipt',p) for p,_ in pairs]]]
                    atomic_write(frozen, {'history':history,'sha256':digest(history)})
                db.execute('UPDATE runs SET metadata=? WHERE day=? AND mode=?',(json.dumps(dict(release=release,ruleset_version=rules['version'],ruleset_sha256=pointer['ruleset_sha256'],input_sha256=digest(history),output_dir=str(out))),day,mode)); db.commit()
                stage('CALCULATING')
                alert = build_calendar_alert(history,day,rules)
                card = build(alert,rules)
                for name,value in [('calendar-history',history),('alert',alert),('alert-card',card)]:
                    filename = 'enterprise-profit-calendar-history.json' if name=='calendar-history' else f'enterprise-profit-{name}.json'
                    atomic_write(out/filename,value)
                # Validate inputs and artifacts before any external side effect.
                atomic_write(out/'enterprise-profit-alert-delivery-dry-run.json', dict(status='SUCCESS',delivery_status='DRY_RUN_NOT_SENT',send_attempted=False,message_count=0,alert_count=alert['alert_count'],would_send=alert['alert_count']>0))
                validate_output(out,rules_path,rules,delivery_mode='dry-run')
                receipt = {'status':'SUCCESS','execution_mode':mode,'alert_count':alert['alert_count'], 'message_count':0}
                evidence = []
                if mode=='dry-run': receipt.update(delivery_status='DRY_RUN_NOT_SENT',send_attempted=False,would_send=alert['alert_count']>0)
                elif not alert['alert_count']: receipt['delivery_status']='NO_SEND_NO_BUSINESS_ALERT'
                else:
                    stage('DELIVERING')
                    for recipient in recipients:
                        key = 'epa-' + digest(['enterprise-profit-alert',day,mode,recipient])
                        payload = dict(application='enterprise-profit-alert',dataDay=day,mode=mode,recipientId=recipient,
                            idempotencyKey=key,rulesetVersion=rules['version'],rulesetSha256=pointer['ruleset_sha256'],inputSha256=digest(history),cardSha256=digest(card),card=card)
                        observed = db.execute('SELECT evidence FROM deliveries WHERE day=? AND mode=? AND recipient=?',(day,mode,recipient)).fetchone()
                        try: result = client.call('/deliveries/query',{'idempotencyKey':key})
                        except ApiError as error:
                            if error.status!=404 or observed: raise Pending('delivery cannot be proven absent') from error
                            # Persist intent BEFORE crossing the write boundary.
                            db.execute('INSERT INTO deliveries VALUES (?,?,?,?)',(day,mode,recipient,json.dumps({'state':'SUBMITTING'}))); db.commit()
                            try: result = client.call('/deliveries',payload)
                            except Exception as error: raise Pending('submission outcome unknown') from error
                        # Submission responses can be compact; authoritative query has bindings.
                        result = client.call('/deliveries/query',{'idempotencyKey':key})
                        if result.get('state') == 'PREPARED':
                            client.call('/deliveries',payload)
                            result = client.call('/deliveries/query',{'idempotencyKey':key})
                        deadline = time.monotonic() + 300
                        while result.get('state') == 'SUBMITTING' and time.monotonic() < deadline:
                            time.sleep(5)
                            result = client.call('/deliveries/query',{'idempotencyKey':key})
                        for field in ('application','dataDay','mode','recipientId','rulesetSha256','inputSha256','cardSha256'):
                            if result.get(field)!=payload[field]: raise Pending('delivery binding mismatch')
                        if result.get('state') == 'QUEUED':
                            if (not accept_notice_queue or result.get('deliveryMode') != 'NOTICE_QUEUE'
                                    or result.get('noticeAccepted') is not True or result.get('sendAttempted') is not True
                                    or result.get('noticeApp') != sender_app
                                    or result.get('transport') != 'NOTICE_DUBBO'
                                    or result.get('assumedSuccess') is not False
                                    or result.get('readbackConfirmed') is not False or result.get('messageId') is not None
                                    or not result.get('deliveryId')):
                                raise Pending('unexpected Notice queue result')
                        elif result.get('state') not in ('SENT','VERIFIED'):
                            raise Pending('delivery unresolved: '+str(result.get('state')))
                        elif result.get('assumedSuccess') is True:
                            if (not assume_notice_success or result.get('deliveryMode') != 'ASSUMED_SUCCESS_NO_SEND'
                                    or result.get('readbackConfirmed') is not False or result.get('messageId') is not None
                                    or not result.get('deliveryId')):
                                raise Pending('unexpected assumed notice result')
                        else:
                            stage('VERIFYING')
                            key_delivery = result['deliveryId']
                            result = client.call('/deliveries/readback',{'deliveryId':key_delivery})
                            if (result.get('state')!='VERIFIED' or result.get('readbackConfirmed') is not True
                                or result.get('deliveryId')!=key_delivery
                                or result.get('recipientId')!=recipient or result.get('senderAppId')!=sender_app
                                or result.get('contentMatch') not in ('EXACT','RENDERED_MARKERS') or not result.get('messageId')):
                                raise Pending('readback mismatch')
                        db.execute('INSERT OR REPLACE INTO deliveries VALUES (?,?,?,?)',(day,mode,recipient,json.dumps(result))); db.commit()
                        evidence.append(result)
                    assumed = any(item.get('assumedSuccess') is True for item in evidence)
                    queued = any(item.get('state') == 'QUEUED' for item in evidence)
                    if queued and not all(item.get('state') == 'QUEUED' for item in evidence):
                        raise Pending('mixed delivery assurance requires reconciliation')
                    receipt.update(message_count=len(evidence),message_readback_confirmed=not (assumed or queued),deliveries=evidence,expected_recipients=recipients)
                    if queued:
                        receipt.update(delivery_status='NOTICE_ACCEPTED_UNCONFIRMED',notice_queue_accepted=True,
                                       send_attempted=True,assumedSuccess=False)
                    if assumed:
                        receipt.update(delivery_status='ASSUMED_SUCCESS_NO_SEND',assumedSuccess=True,send_attempted=False)
                atomic_write(out/f'enterprise-profit-alert-delivery-{mode}.json',receipt)
                validate_output(out,rules_path,rules,delivery_mode=mode)
                atomic_write(out/'enterprise-profit-alert-run.json',dict(status='SUCCESS',current_day=day,execution_mode=mode,ruleset_version=rules['version'],release=release,input_sha256=digest(history),assumed_notice_success=receipt.get('assumedSuccess',False),notice_queue_accepted=receipt.get('notice_queue_accepted',False)))
                stage('SUCCESS')
                return receipt
            except Exception as error:
                observed_stage = db.execute('SELECT stage FROM runs WHERE day=? AND mode=?',(day,mode)).fetchone()[0]
                stage('PENDING_RECONCILIATION' if isinstance(error,Pending) or observed_stage in ('DELIVERING','VERIFYING') else 'FAILED',str(error)); raise
        finally: db.close()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-dir',type=Path,required=True)
    parser.add_argument('--previous-history',type=Path)
    parser.add_argument('--data-day',default=(datetime.now(ZoneInfo('Asia/Shanghai')).date()-timedelta(days=1)).isoformat())
    parser.add_argument('--mode',choices=['dry-run','test','production'],required=True)
    parser.add_argument('--recipient',action='append',default=[])
    parser.add_argument('--sender-app')
    parser.add_argument('--assume-notice-success',action='store_true',help='Test only: accept explicit task placeholder receipts without real send/readback')
    parser.add_argument('--accept-notice-queue',action='store_true',help='Send via existing Notice; accept queue acknowledgement without Feishu delivery/readback confirmation')
    args=parser.parse_args()
    client=Client.from_env()
    execute(client,args.state_dir,args.data_day,args.mode,args.previous_history,args.recipient,sender_app=args.sender_app,assume_notice_success=args.assume_notice_success,accept_notice_queue=args.accept_notice_queue)

if __name__=='__main__': main()
