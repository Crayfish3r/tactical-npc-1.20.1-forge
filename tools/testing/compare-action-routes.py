"""Fail-closed matched fixture comparison; different useful work is not an optimization."""
import argparse,csv,hashlib,json
from pathlib import Path
def main():
    p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);args=p.parse_args()
    receipts=[json.loads((d/'receipt.json').read_text()) for d in [args.before,args.after]]
    assert all(r['status']=='PASS' and r['sources_unchanged'] for r in receipts)
    for r in receipts:r['source_sha256']={k.replace('\\','/'):v for k,v in r['source_sha256'].items()}
    key='forge-addon/src/profile/java/dev/zuma/tacticalnpc/ActionRouteProfile.java'
    assert receipts[0]['source_sha256'][key]==receipts[1]['source_sha256'][key]
    for name,digest in receipts[0]['source_sha256'].items():
        if name.startswith('forge-addon/libs/'):assert receipts[1]['source_sha256'][name]==digest
    before=list(csv.DictReader((args.before/'actions.csv').open()));after=list(csv.DictReader((args.after/'actions.csv').open()))
    assert [r['npc'] for r in before]==[r['npc'] for r in after]==['1','4','16','30','50']
    comparisons=[]
    for b,a in zip(before,after):
        assert int(a['arrivals'])==int(a['npc']) and int(a['rounds'])>0 and int(a['hits'])>0
        assert int(a['samples'])==int(b['samples'])==961
        assert all(int(r['alive_min'])==int(r['npc']) and int(r['ticking_min'])==int(r['npc']) for r in [b,a])
        comparisons.append({'npc':int(a['npc']),'groups':int(a['groups']),'before':b,'after':a,
            'mspt_p95_change_percent':(float(a['tick_p95_ms'])/float(b['tick_p95_ms'])-1)*100,
            'matched_useful_work':b['arrivals']==a['arrivals'] and abs(int(a['rounds'])-int(b['rounds']))<=max(1,int(b['rounds'])*.1),
            'optimization_claim':False})
    print(json.dumps({'status':'PASS','fixture_sha256':receipts[1]['source_sha256'][key],
        'scope':'ordinary dedicated fixed seed, synthetic route orders then actual native combat; one pair only',
        'comparisons':comparisons},indent=2))
if __name__=='__main__':main()
