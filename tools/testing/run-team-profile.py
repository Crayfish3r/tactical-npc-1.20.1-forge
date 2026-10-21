"""One isolated ordinary dedicated 30-NPC team-command release scene, with actual JFR."""
import datetime as dt
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT=Path(__file__).resolve().parents[2]
PROJECT=ROOT/'forge-addon'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--per-team',type=int,choices=[4,15],default=15);parser.add_argument('--crowded',action='store_true');args=parser.parse_args()
    stamp=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S')
    output=PROJECT/'build/team-profile'/stamp;run=output/'runtime';run.mkdir(parents=True,exist_ok=False)
    eula=PROJECT/'run/standalone/eula.txt'
    assert 'eula=true' in eula.read_text(encoding='utf-8').replace(' ',''),'Requires existing owner-accepted EULA'
    shutil.copy2(eula,run/'eula.txt')
    props={'server-ip':'127.0.0.1','server-port':'25594','online-mode':'false','level-name':'team-profile-'+stamp,'level-seed':'8675309','level-type':'minecraft:flat','generator-settings':'{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}','view-distance':'4','simulation-distance':'4','difficulty':'normal'}
    (run/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n',encoding='utf-8')
    command=[str(PROJECT/('gradlew.bat' if os.name=='nt' else 'gradlew')),'runServer','compileCompatJava','-Pprofile','-PteamProfile',f'-PprofileDir={run}','--offline','--console=plain']
    command.append(f'-PteamCount={args.per_team}')
    if args.crowded:command.append('-PteamCrowded')
    source_paths=['forge-addon/build.gradle','forge-addon/src/profile/java/dev/zuma/tacticalnpc/TeamCommandProfile.java','forge-addon/src/main/java/dev/zuma/tacticalnpc/debug/DebugCommands.java']
    sources={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in source_paths}
    started=time.monotonic()
    with (output/'server.log').open('w',encoding='utf-8') as stream:process=subprocess.run(command,cwd=PROJECT,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT)
    for name in ('profile.jfr','team-profile.json','team-environment.json','team-samples.csv','batch-summary.csv'):
        if (run/name).exists():shutil.copy2(run/name,output/name)
    data=json.loads((output/'team-profile.json').read_text(encoding='utf-8')) if (output/'team-profile.json').exists() else {}
    ok=process.returncode==0 and data.get('status')=='PASS' and data.get('actors')==args.per_team*2 and data.get('samples')==600 and (output/'profile.jfr').is_file()
    unchanged=all(hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==value for name,value in sources.items());ok=ok and unchanged
    receipt=dict(status='PASS' if ok else 'FAIL',seconds=round(time.monotonic()-started,3),command=command,output=str(output.relative_to(ROOT)),source_sha256=sources,sources_unchanged=unchanged,metrics=data)
    (output/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt,indent=2));return 0 if ok else 1

if __name__=='__main__':raise SystemExit(main())
