"""Identical fixed-seed dedicated fixture on frozen baseline/current production code."""
import argparse,csv,datetime,hashlib,json,os,shutil,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser();p.add_argument('variant',choices=['before','after']);args=p.parse_args()
 base=ROOT/'forge-addon/build/combat-upgrade-baseline'
 project=(base if args.variant=='before' else ROOT)/'forge-addon'
 if args.variant=='before':
  manifest=json.loads((base/'baseline-source.json').read_text())
  for name,digest in manifest.items():
   if any(part in name.replace('\\','/') for part in ['/src/main/','/src/compat/','/libs/']):
    assert hashlib.sha256((base/name).read_bytes()).hexdigest()==digest,name
  shutil.copy2(ROOT/'forge-addon/build.gradle',project/'build.gradle')
  shutil.copytree(ROOT/'forge-addon/src/profile',project/'src/profile',dirs_exist_ok=True)
 stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S')
 output=ROOT/'forge-addon/build/upgrade-profile'/f'{stamp}-{args.variant}';run=output/'runtime';run.mkdir(parents=True)
 eula=ROOT/'forge-addon/run/standalone/eula.txt'
 assert 'eula=true' in eula.read_text().replace(' ','')
 shutil.copy2(eula,run/'eula.txt')
 props={'server-ip':'127.0.0.1','server-port':'25596','online-mode':'false','level-name':'upgrade','level-seed':'8675309','level-type':'minecraft:flat','generator-settings':'{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}','view-distance':'4','simulation-distance':'4','difficulty':'normal'}
 (run/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n')
 command=[str(project/'gradlew.bat'),'runServer','-Pprofile','-PupgradeProfile',f'-PprofileDir={run}','--offline','--console=plain']
 sources={str(f.relative_to(project.parent)):hashlib.sha256(f.read_bytes()).hexdigest() for folder in ['forge-addon/src/main','forge-addon/src/profile','ai-core/src/main','forge-addon/libs'] for f in (project.parent/folder).rglob('*') if f.is_file()}
 started=time.monotonic()
 with (output/'server.log').open('w',encoding='utf-8') as stream:
  result=subprocess.run(command,cwd=project,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
 for name in ['upgrade.csv','upgrade-samples.csv','upgrade-phases.csv','profile.jfr']:
  if (run/name).exists():shutil.copy2(run/name,output/name)
 log=(output/'server.log').read_text(errors='replace')
 unchanged=all(hashlib.sha256((project.parent/name).read_bytes()).hexdigest()==digest for name,digest in sources.items())
 rows=list(csv.DictReader((output/'upgrade.csv').open())) if (output/'upgrade.csv').exists() else []
 names=['range64','range96','range128','running64','suppression','flank1v4','load16','load30','load50']
 ok=result.returncode==0 and 'UPGRADE_COMPLETE phases=9' in log and [r['phase'] for r in rows]==names and unchanged and (output/'profile.jfr').is_file()
 # Completion is a measurement receipt. Behaviour acceptance is separately explicit and fail-closed.
 acceptance={}
 if args.variant=='after' and len(rows)==9:
  acceptance['range_and_running']=all(int(r['detected_tick'])>=40 and int(r['rounds'])>0 and int(r['hits'])>0 for r in rows[:4])
  acceptance['sustained_pressure']=int(rows[4]['area_rounds'])>6
  acceptance['actual_flank']=int(rows[5]['flank_ticks'])>0 and int(rows[5]['flank_arrivals'])>0 and float(rows[5]['flank_displacement'])>=8
  acceptance['load_firing']=all(int(r['rounds'])>0 and int(r['shooters'])>=int(r['npc'])*.8 for r in rows[6:])
  ok=ok and all(acceptance.values())
 receipt=dict(status='PASS' if ok else 'FAIL',variant=args.variant,seconds=time.monotonic()-started,command=command,source_sha256=sources,sources_unchanged=unchanged,behaviour_acceptance=acceptance,output=str(output.relative_to(ROOT)))
 (output/'receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps({k:v for k,v in receipt.items() if k!='source_sha256'},indent=2));return 0 if ok else 1
if __name__=='__main__':raise SystemExit(main())
