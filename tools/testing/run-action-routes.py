"""Matched ordinary dedicated action-route fixture. Baseline is archived before any edits."""
import argparse,csv,datetime,hashlib,json,os,shutil,subprocess,tarfile,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('variant',choices=['before','after']);args=parser.parse_args()
    base=ROOT/'forge-addon/build/action-route-baseline'
    if args.variant=='before':
        if not base.exists():
            base.mkdir()
            with tarfile.open(ROOT/'forge-addon/build/action-route-baseline.tar') as archive:
                for member in archive.getmembers():
                    assert not member.issym() and not member.islnk()
                    assert (base/member.name).resolve().is_relative_to(base.resolve())
                archive.extractall(base)
            shutil.copytree(ROOT/'forge-addon/libs',base/'forge-addon/libs')
            source={str(f.relative_to(base)):hashlib.sha256(f.read_bytes()).hexdigest() for folder in ['forge-addon/src/main','forge-addon/src/compat','ai-core/src/main','forge-addon/libs'] for f in (base/folder).rglob('*') if f.is_file()}
            (base/'source.json').write_text(json.dumps(source,indent=2))
        for name,digest in json.loads((base/'source.json').read_text()).items():assert hashlib.sha256((base/name).read_bytes()).hexdigest()==digest,name
        shutil.copy2(ROOT/'forge-addon/build.gradle',base/'forge-addon/build.gradle')
        shutil.copytree(ROOT/'forge-addon/src/profile',base/'forge-addon/src/profile',dirs_exist_ok=True)
    project=(base if args.variant=='before' else ROOT)/'forge-addon'
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S')
    output=ROOT/'forge-addon/build/action-profile'/f'{stamp}-{args.variant}';run=output/'runtime';run.mkdir(parents=True)
    eula=ROOT/'forge-addon/run/standalone/eula.txt';assert 'eula=true' in eula.read_text().replace(' ','');shutil.copy2(eula,run/'eula.txt')
    props={'server-ip':'127.0.0.1','server-port':'25596','online-mode':'false','level-name':'actions','level-seed':'8675309','level-type':'minecraft:flat','generator-settings':'{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}','view-distance':'4','simulation-distance':'4','difficulty':'normal'}
    (run/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n')
    command=[str(project/'gradlew.bat'),'runServer','-Pprofile','-PactionProfile',f'-PprofileDir={run}','--offline','--console=plain','--rerun-tasks']
    sources={str(f.relative_to(project.parent)):hashlib.sha256(f.read_bytes()).hexdigest() for folder in ['forge-addon/src/main','forge-addon/src/profile','ai-core/src/main','forge-addon/libs'] for f in (project.parent/folder).rglob('*') if f.is_file()}
    started=time.monotonic()
    with (output/'server.log').open('w',encoding='utf-8') as log:
        result=subprocess.run(command,cwd=project,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    for name in ['actions.csv','actions-samples.csv','actions-phases.csv','actions-actors.csv','profile.jfr']:
        if (run/name).exists():shutil.copy2(run/name,output/name)
    log=(output/'server.log').read_text(errors='replace')
    rows=list(csv.DictReader((output/'actions.csv').open())) if (output/'actions.csv').exists() else []
    unchanged=all(hashlib.sha256((project.parent/name).read_bytes()).hexdigest()==digest for name,digest in sources.items())
    ok=result.returncode==0 and 'ACTIONS_COMPLETE phases=5' in log and [r['npc'] for r in rows]==['1','4','16','30','50'] and unchanged and (output/'profile.jfr').is_file()
    acceptance={}
    if len(rows)==5:
        acceptance['all_alive_and_entity_ticking']=all(int(r['alive_min'])==int(r['npc']) and int(r['ticking_min'])==int(r['npc']) for r in rows)
        ok=ok and acceptance['all_alive_and_entity_ticking']
    if args.variant=='after' and len(rows)==5:
        acceptance['all_arrived']=all(int(r['arrivals'])==int(r['npc']) for r in rows)
        acceptance['actual_combat']=all(int(r['rounds'])>0 and int(r['hits'])>0 for r in rows)
        ok=ok and all(acceptance.values())
    receipt=dict(status='PASS' if ok else 'FAIL',variant=args.variant,seconds=time.monotonic()-started,command=command,sources_unchanged=unchanged,source_sha256=sources,acceptance=acceptance,output=str(output.relative_to(ROOT)))
    (output/'receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps({k:v for k,v in receipt.items() if k!='source_sha256'},indent=2));return 0 if ok else 1
if __name__=='__main__':raise SystemExit(main())
