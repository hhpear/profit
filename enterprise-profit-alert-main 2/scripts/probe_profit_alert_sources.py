"""Read-only company source acceptance probe. Never calls delivery endpoints."""
import argparse
import os
import json
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
from scripts.profit_alert_http import Client, digest
from scripts.profit_alert_b1 import domestic
from scripts.normalize_pigeon_artifact_receipts import atomic_write
from scripts.fetch_enterprise_profit_alert_pigeon import validate
from scripts.run_enterprise_profit_alert_daily import active_operating_rules
from scripts.validate_enterprise_profit_alert_governance import validate_active, read
from scripts.build_enterprise_profit_alert import build_domestic_calendar_history, domestic_day


def probe(client, day, output):
    if date.fromisoformat(day) >= datetime.now(ZoneInfo('Asia/Shanghai')).date():
        raise ValueError('only complete natural days allowed')
    if output.exists():
        raise ValueError('use a new output directory to preserve previous evidence')
    output.mkdir(parents=True)
    try:
        _, _, rules = validate_active()
        start = (date.fromisoformat(day)-timedelta(days=60)).isoformat()
        hist = domestic(client, start, day)
        atomic_write(output/'domestic-history.json', hist)
        detail = domestic(client, day, day, True)
        atomic_write(output/'domestic-current.json', detail)
        calendar = build_domestic_calendar_history(hist, day)
        current, _ = domestic_day(detail, day)
        from decimal import Decimal
        expected = {row['enterprise_code']: Decimal(row['daily'][day]['profit'])
                    for row in calendar['rows'] if day in row['daily']}
        if expected != {code: row['profit'] for code, row in current.items()}:
            raise RuntimeError('domestic history/current do not reconcile')
        international = client.call('/international/daily', {'dataDay': day})
        atomic_write(output/'international-envelope.json', international)
        validate(international['receipt'], day, read(active_operating_rules()))
        repeated = client.call('/international/daily', {'dataDay': day})
        atomic_write(output/'international-repeat-envelope.json', repeated)
        if digest(international['receipt']) != digest(repeated['receipt']):
            raise RuntimeError('international receipt changed between reads')
        report = dict(status='SOURCE_CHECKS_PASSED', data_day=day, ruleset_version=rules['version'],
                      domestic_history_start=start, domestic_enterprises=len(current),
                      international_rows=international['receipt']['row_count'],
                      delivery_attempted=False, business_equivalence_confirmed=False,
                      note='Client validation only; source call counts and old/new business equivalence require separate evidence.')
        atomic_write(output/'source-probe-report.json', report)
        return report
    except Exception as error:
        atomic_write(output/'source-probe-report.json', dict(status='FAILED',data_day=day,
                     delivery_attempted=False,error_type=type(error).__name__))
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-day',required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    args=parser.parse_args()
    required=('PROFIT_API_URL','PROFIT_API_KEY','PROFIT_API_SECRET')
    missing=[key for key in required if not os.environ.get(key)]
    if missing: parser.error('missing environment configuration: '+', '.join(missing))
    report=probe(Client.from_env(),args.data_day,args.output_dir)
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__': main()
