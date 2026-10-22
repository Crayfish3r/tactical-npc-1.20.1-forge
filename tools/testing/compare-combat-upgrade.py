"""Compare actual matched-fixture receipts; lower firing can never earn performance credit."""
import argparse,csv,json
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
    receipts=[json.loads((d/'receipt.json').read_text()) for d in [a.before,a.after]]
    assert all(r['status']=='PASS' and r['sources_unchanged'] for r in receipts),'Both completed current receipts required'
    fixture='forge-addon/src/profile/java/dev/zuma/tacticalnpc/CombatUpgradeProfile.java'
    assert receipts[0]['source_sha256'][fixture.replace('/', '\\') if '\\' in next(iter(receipts[0]['source_sha256'])) else fixture]==receipts[1]['source_sha256'][fixture.replace('/', '\\') if '\\' in next(iter(receipts[1]['source_sha256'])) else fixture], 'Different fixture sources'
    tables=[list(csv.DictReader((d/'upgrade.csv').open())) for d in [a.before,a.after]]
    assert [r['phase'] for r in tables[0]]==[r['phase'] for r in tables[1]] and len(tables[0])==9
    for directory,table in zip([a.before,a.after],tables):
        samples=list(csv.DictReader((directory/'upgrade-samples.csv').open()))
        for phase in table:
            active=[int(s['active']) for s in samples if s['phase']==phase['phase']]
            # Age40 is the marker immediately before tags activate on the following START event.
            assert len(active)==int(phase['samples']) and all(n==int(phase['npc']) for n in active[1:]), 'Lost active cohort'
    rows=[]
    for old,new in zip(*tables):
        assert old['npc']==new['npc'] and old['samples']==new['samples']
        assert int(old['hits'])<=int(old['rounds']) and int(new['hits'])<=int(new['rounds']), 'Unique native projectile hits required'
        shots0,shots1=int(old['rounds']),int(new['rounds']);first0,first1=int(old['first_projectile_tick']),int(new['first_projectile_tick'])
        eligible=shots0>0 and .9<=shots1/shots0<=1.1 and int(new['shooters'])>=int(old['shooters']) and 0<=first1<=first0+1
        rows.append(dict(phase=old['phase'],npc=int(old['npc']),before=old,after=new,performance_comparable=eligible,
                         performance_gain_claimed=False,round_ratio=None if shots0==0 else shots1/shots0,
                         first_projectile_after_enable_ticks=[None if t<0 else t-40 for t in [first0,first1]]))
    result=dict(status='PASS',before=str(a.before),after=str(a.after),fixture_identical=True,full_active_cohorts=True,rows=rows,
                note='Single paired synthetic run; no optimization claim. Geometry/hit attempts on immortal FakePlayers are not human TTK/win rate. Fixture forces chunks; production search does not. Lower shots, fewer shooters or slower first fire prohibit performance credit.')
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(dict(status=result['status'],fixture_identical=True,rows=len(rows),performance_gain_claimed=False)))
if __name__=='__main__':main()
