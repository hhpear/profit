"""Build an allowlisted local deployment archive; excludes secrets and customer outputs."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    if args.output.exists():raise ValueError('output already exists')
    files=[]
    for directory in ('scripts','config','deploy','data_monitor_platform_server_latest'):
        files.extend(p for p in (root/directory).rglob('*') if p.is_file() and p.suffix in ('.py','.json','.md','.lock','.service','.timer','.example') or p.is_file() and p.name=='Dockerfile')
    files.extend(root/p for p in ('README.md','.dockerignore','docs/企业客户利润异常告警.md'))
    files=sorted(set(files))
    manifest={'files':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as archive:
        for p in files:archive.write(p,str(p.relative_to(root)))
        archive.writestr('DEPLOYMENT_MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2))
    print(json.dumps({'archive':str(args.output.resolve()),'files':len(files),'sha256':hashlib.sha256(args.output.read_bytes()).hexdigest()}))

if __name__=='__main__':main()
