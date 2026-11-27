import copy
import hashlib
import hmac
import json
import sqlite3
import unittest
from datetime import date,timedelta
from pathlib import Path
from tempfile import TemporaryDirectory
from scripts.profit_alert_http import Client,BASE,digest
from scripts.profit_alert_b1 import execute,Pending,select_history
from scripts.validate_enterprise_profit_alert_governance import validate_active
from scripts.normalize_pigeon_artifact_receipts import atomic_write


from scripts.profit_alert_mock import MockService


class WorkflowTest(unittest.TestCase):
    def setUp(self):
        self.tmp=TemporaryDirectory(); self.root=Path(self.tmp.name)
        _,_,rules=validate_active()
        self.previous=self.root/'previous.json'
        atomic_write(self.previous,dict(status='SUCCESS',ruleset_version=rules['version'],coverage={'start':'2026-09-26','end':'2026-11-25'},rows=[dict(business_type='国内',enterprise_code='SYNTHETIC-001',enterprise_name='合成企业',daily={'2026-11-25':{'income':'2000','profit':'1000'}})]))
    def tearDown(self):self.tmp.cleanup()
    def run_workflow(self,service,mode='test'):
        return execute(Client('https://synthetic.invalid','synthetic','synthetic-secret',transport=service.transport),self.root/'state','2026-11-26',mode,self.previous,['a','b','c'],sender_app='synthetic-app')
    def test_test_mode_and_repeat(self):
        service=MockService(); result=self.run_workflow(service)
        self.assertEqual(result['message_count'],3)
        self.run_workflow(service); self.assertEqual(service.sent,['a','b','c'])
    def test_dry_run_never_sends(self):
        service=MockService(); result=self.run_workflow(service,'dry-run')
        self.assertFalse(result['send_attempted']);self.assertEqual(service.sent,[])
    def test_zero_alert(self):
        service=MockService(False); result=self.run_workflow(service,'production')
        self.assertEqual(result['message_count'],0); self.assertEqual(service.sent,[])
    def test_partial_then_resume(self):
        service=MockService();service.fault='third'
        with self.assertRaises(Pending):self.run_workflow(service)
        service.fault=None;self.run_workflow(service)
        self.assertEqual(service.sent,['a','b','c'])
    def test_disconnect_then_query_without_resend(self):
        service=MockService();service.fault='disconnect'
        with self.assertRaises(Pending):self.run_workflow(service)
        service.fault=None;self.run_workflow(service)
        self.assertEqual(service.sent,['a','b','c'])
    def test_lost_authority_stops(self):
        service=MockService();service.fault='disconnect'
        with self.assertRaises(Pending):self.run_workflow(service)
        service.fault=None;service.records.clear()
        with self.assertRaises(Pending):self.run_workflow(service)
        self.assertEqual(service.sent,['a'])
    def test_bad_source_stops(self):
        service=MockService();service.fault='amount'
        with self.assertRaises(RuntimeError):self.run_workflow(service)
        self.assertEqual(service.sent,[])
    def test_missing_page_stops(self):
        service=MockService();service.fault='missing_page'
        with self.assertRaises(RuntimeError):self.run_workflow(service)
        self.assertEqual(service.sent,[])
    def test_input_tamper_stops_before_delivery(self):
        service=MockService();self.run_workflow(service,'dry-run')
        path=self.root/'state/dry-run/2026-11-26/frozen.json'
        value=json.loads(path.read_text());value['history']['rows'][0]['enterprise_name']='tampered'
        atomic_write(path,value)
        with self.assertRaises(RuntimeError):self.run_workflow(service,'dry-run')
        self.assertEqual(service.sent,[])
    def test_run_binding_cannot_change(self):
        service=MockService();self.run_workflow(service)
        with self.assertRaises(RuntimeError):
            execute(Client('https://synthetic.invalid','synthetic','synthetic-secret',transport=service.transport),self.root/'state','2026-11-26','test',self.previous,['different'],sender_app='synthetic-app')
        self.assertEqual(service.sent,['a','b','c'])
    def test_source_failure_saved(self):
        service=MockService();service.fault='amount'
        with self.assertRaises(RuntimeError):self.run_workflow(service)
        with sqlite3.connect(self.root/'state/runs.sqlite') as db:
            self.assertEqual(db.execute('SELECT stage FROM runs').fetchone()[0],'FAILED')
    def test_unknown_saved(self):
        service=MockService();service.fault='disconnect'
        with self.assertRaises(Pending):self.run_workflow(service)
        with sqlite3.connect(self.root/'state/runs.sqlite') as db:
            self.assertEqual(db.execute('SELECT stage FROM runs').fetchone()[0],'PENDING_RECONCILIATION')
    def test_compatible_history_selection_and_mode_isolation(self):
        service=MockService();self.run_workflow(service,'dry-run')
        _,_,rules=validate_active()
        selected=select_history(self.root/'state','2026-11-27','dry-run',rules['version'])
        self.assertEqual(json.loads(selected.read_text())['coverage']['end'],'2026-11-26')
        with self.assertRaises(RuntimeError):
            select_history(self.root/'state','2026-11-27','test',rules['version'])
    def test_canonical_vector(self):
        self.assertEqual(digest({'中':{'b':1,'a':'金额'}}),digest({'中':{'a':'金额','b':1}}))
        with self.assertRaises(ValueError):digest({'amount':1.5})

if __name__=='__main__':unittest.main()
