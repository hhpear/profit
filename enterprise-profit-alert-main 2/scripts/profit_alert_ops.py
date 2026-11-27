"""Single-host operational wrapper, immutable backups, restore verification and watchdog."""
import argparse
import fcntl
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
from datetime import datetime,timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
from scripts.normalize_pigeon_artifact_receipts import atomic_write


def hashes(root):
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob('*')) if p.is_file() and p.name!='backup-manifest.json'}


def snapshot(source,target):
    source,target=Path(source).resolve(),Path(target).resolve()
    if target==source or source in target.parents:raise ValueError('backup must be outside state directory')
    if target.exists():raise ValueError('new backup directory required')
    if not (source/'runs.sqlite').is_file():raise ValueError('state database missing')
    with (source/'workflow.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        target.mkdir(parents=True)
        try:
            for p in source.iterdir():
                if p.name in ('workflow.lock','runs.sqlite','runs.sqlite-wal','runs.sqlite-shm'):continue
                if p.is_dir():shutil.copytree(p,target/p.name)
                elif p.is_file():shutil.copy2(p,target/p.name)
            with sqlite3.connect(source/'runs.sqlite') as db,sqlite3.connect(target/'runs.sqlite') as dest:
                db.backup(dest)
                if dest.execute('PRAGMA integrity_check').fetchone()[0]!='ok':raise RuntimeError('database corrupt')
            atomic_write(target/'backup-manifest.json',{'source_root':str(source),'files':hashes(target),'created_at':datetime.now(ZoneInfo('Asia/Shanghai')).isoformat()})
        except Exception:
            # Incomplete backups intentionally have no valid manifest.
            raise


def restore(backup,target):
    backup,target=Path(backup).resolve(),Path(target).resolve()
    manifest=json.loads((backup/'backup-manifest.json').read_text())
    if manifest['files']!=hashes(backup):raise RuntimeError('backup digest mismatch')
    if target.exists():raise ValueError('restore requires a new directory')
    shutil.copytree(backup,target)
    # Never rewrite input hashes bound to already submitted messages.
    old=Path(manifest['source_root'])
    atomic_write(target/'RESTORE_REQUIRES_RECONCILIATION.json',{'original_root':str(old),'restore_root':str(target),'send_enabled':False})
    with sqlite3.connect(target/'runs.sqlite') as db:
        if db.execute('PRAGMA integrity_check').fetchone()[0]!='ok':raise RuntimeError('restored database corrupt')


def check(root,mode,day):
    root=Path(root)
    if (root/'RESTORE_REQUIRES_RECONCILIATION.json').exists():return 30,'RESTORE_REQUIRES_RECONCILIATION'
    if not (root/'runs.sqlite').is_file():return 20,'MISSED_RUN'
    with sqlite3.connect('file:'+str((root/'runs.sqlite').resolve())+'?mode=ro',uri=True) as db:
        row=db.execute('SELECT stage FROM runs WHERE day=? AND mode=?',(day,mode)).fetchone()
    if not row:return 20,'MISSED_RUN'
    if row[0]!='SUCCESS':return 30 if row[0]=='PENDING_RECONCILIATION' else 20,row[0]
    manifest=root/mode/day/'enterprise-profit-alert-run.json'
    if not manifest.is_file() or json.loads(manifest.read_text()).get('status')!='SUCCESS':return 20,'MANIFEST_MISSING'
    try:
        from scripts.validate_enterprise_profit_alert_governance import validate_active,validate_output
        _,rules_path,rules=validate_active()
        validate_output(root/mode/day,rules_path,rules,delivery_mode=mode)
    except Exception:
        return 20,'ARTIFACT_VALIDATION_FAILED'
    return 0,'SUCCESS'


def run(root,mode,timeout,previous=None):
    root=Path(root)
    if (root/'RESTORE_REQUIRES_RECONCILIATION.json').exists():return 30
    command=[sys.executable,'-m','scripts.profit_alert_b1','--state-dir',str(root),'--mode',mode]
    if previous:command+=['--previous-history',str(previous)]
    try:
        child=subprocess.run(command,timeout=timeout)
        if child.returncode==0:return 0
        day=(datetime.now(ZoneInfo('Asia/Shanghai')).date()-timedelta(days=1)).isoformat()
        code,_=check(root,mode,day)
        return code if code else 10
    except subprocess.TimeoutExpired:return 40


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['run','check','backup','restore'])
    parser.add_argument('--state-dir',type=Path,required=True)
    parser.add_argument('--archive',type=Path)
    parser.add_argument('--archive-root',type=Path)
    parser.add_argument('--mode',choices=['dry-run','test','production'],default='dry-run')
    parser.add_argument('--data-day',default=(datetime.now(ZoneInfo('Asia/Shanghai')).date()-timedelta(days=1)).isoformat())
    parser.add_argument('--timeout',type=int,default=1800)
    parser.add_argument('--previous-history',type=Path)
    args=parser.parse_args()
    if args.action=='backup':
        target=args.archive
        if target is None and args.archive_root:
            target=args.archive_root/datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y%m%dT%H%M%S%f')
        if target is None:parser.error('backup requires --archive or --archive-root')
        snapshot(args.state_dir,target)
    elif args.action=='restore':
        if args.archive is None:parser.error('restore requires --archive')
        restore(args.archive,args.state_dir)
    elif args.action=='check':
        code,state=check(args.state_dir,args.mode,args.data_day);print(json.dumps({'data_day':args.data_day,'mode':args.mode,'state':state}));sys.exit(code)
    else:sys.exit(run(args.state_dir,args.mode,args.timeout,args.previous_history))

if __name__=='__main__':main()
