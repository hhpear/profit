import fcntl
import shutil
import sqlite3
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from scripts.profit_alert_ops import snapshot,restore,check,run
from scripts.profit_alert_mock import MockService
from scripts.profit_alert_http import Client
from scripts.profit_alert_b1 import execute,Pending
from scripts.normalize_pigeon_artifact_receipts import atomic_write
from scripts.validate_enterprise_profit_alert_governance import validate_active,validate_output

class OperationsTest(unittest.TestCase):
    def setUp(self):
        self.tmp=TemporaryDirectory();self.root=Path(self.tmp.name);self.state=self.root/'state'
        _,_,rules=validate_active();self.previous=self.root/'previous.json'
        atomic_write(self.previous,dict(status='SUCCESS',ruleset_version=rules['version'],coverage={'start':'2026-09-26','end':'2026-11-25'},rows=[dict(business_type='国内',enterprise_code='SYNTHETIC-001',enterprise_name='合成企业',daily={'2026-11-25':{'income':'2000','profit':'1000'}})]))
    def tearDown(self):self.tmp.cleanup()
    def execute(self,service=None):
        service=service or MockService()
        return execute(Client('https://synthetic.invalid','synthetic','synthetic-secret',transport=service.transport),self.state,'2026-11-26','test',self.previous,['a'],sender_app='synthetic-app')
    def test_missing_and_success_watchdog(self):
        self.assertEqual(check(self.state,'test','2026-11-26')[0],20)
        self.execute();self.assertEqual(check(self.state,'test','2026-11-26'),(0,'SUCCESS'))
    def test_restore_to_original_path_retains_evidence(self):
        self.execute();backup=self.root/'backup';snapshot(self.state,backup)
        shutil.move(str(self.state),str(self.root/'old-state'));restore(backup,self.state)
        self.assertEqual(check(self.state,'test','2026-11-26')[0],30)
        self.assertEqual(run(self.state,'test',5),30)
        _,rules_path,rules=validate_active()
        validate_output(self.state/'test/2026-11-26',rules_path,rules,delivery_mode='test')
        with sqlite3.connect(self.state/'runs.sqlite') as db:
            self.assertEqual(db.execute('SELECT stage FROM runs').fetchone()[0],'SUCCESS')
    def test_corrupt_backup_is_rejected(self):
        self.execute();backup=self.root/'backup';snapshot(self.state,backup)
        (backup/'runs.sqlite').write_bytes(b'corrupt')
        with self.assertRaises(RuntimeError):restore(backup,self.root/'restored')
        self.assertFalse((self.root/'restored').exists())
    def test_backup_will_not_race_active_run(self):
        self.execute()
        with (self.state/'workflow.lock').open('a') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            with self.assertRaises(BlockingIOError):snapshot(self.state,self.root/'backup')
    def test_unresolved_delivery_survives_backup(self):
        service=MockService();service.fault='disconnect'
        with self.assertRaises(Pending):self.execute(service)
        self.assertEqual(check(self.state,'test','2026-11-26')[0],30)
        snapshot(self.state,self.root/'backup');restore(self.root/'backup',self.root/'restored')
        with sqlite3.connect(self.root/'restored/runs.sqlite') as db:
            self.assertEqual(db.execute('SELECT stage FROM runs').fetchone()[0],'PENDING_RECONCILIATION')
            self.assertEqual(db.execute('SELECT COUNT(*) FROM deliveries').fetchone()[0],1)
    def test_missing_manifest_is_not_success(self):
        self.execute();(self.state/'test/2026-11-26/enterprise-profit-alert-run.json').unlink()
        self.assertEqual(check(self.state,'test','2026-11-26')[0],20)

    def test_timeout_exit_code(self):
        from unittest.mock import patch
        import subprocess
        with patch('scripts.profit_alert_ops.subprocess.run',side_effect=subprocess.TimeoutExpired('worker',1)):
            self.assertEqual(run(self.root/'timeout-state','dry-run',1),40)
    def test_duplicate_execution_is_locked(self):
        self.execute()
        with (self.state/'workflow.lock').open('a') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            with self.assertRaises(BlockingIOError):self.execute()

if __name__=='__main__':unittest.main()
