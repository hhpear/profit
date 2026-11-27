"""Repeatable synthetic B1 demonstration; writes only to the supplied directory."""
import argparse
from pathlib import Path
from scripts.profit_alert_mock import MockService
from scripts.profit_alert_http import Client
from scripts.profit_alert_b1 import execute
from scripts.normalize_pigeon_artifact_receipts import atomic_write
from scripts.validate_enterprise_profit_alert_governance import validate_active


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir',type=Path,required=True)
    args=parser.parse_args()
    _,_,rules=validate_active()
    prior=args.output_dir/'synthetic-previous.json'
    atomic_write(prior,dict(status='SUCCESS',ruleset_version=rules['version'],coverage={'start':'2026-09-26','end':'2026-11-25'},
        rows=[dict(business_type='国内',enterprise_code='SYNTHETIC-001',enterprise_name='合成企业',daily={'2026-11-25':{'income':'2000','profit':'1000'}})]))
    service=MockService()
    client=Client('https://synthetic.invalid','synthetic','synthetic-secret',transport=service.transport)
    for mode in ('dry-run','test','production'):
        execute(client,args.output_dir/'state','2026-11-26',mode,prior,['synthetic-a','synthetic-b'],sender_app='synthetic-app')
    atomic_write(args.output_dir/'demo-summary.json',{'synthetic':True,'real_messages_sent':0,'simulated_recipients':service.sent,'modes':['dry-run','test','production']})

if __name__=='__main__':main()
