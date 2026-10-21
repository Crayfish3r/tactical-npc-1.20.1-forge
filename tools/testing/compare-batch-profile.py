"""Compare actual paired CSV receipts; never credit lower firing as AI speedup."""
import argparse
import csv
import json
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before",type=Path)
    parser.add_argument("after",type=Path)
    args=parser.parse_args()
    manifests=[json.loads((p/"source-manifest.json").read_text(encoding="utf-8")) for p in (args.before,args.after)]
    fixtures=[{k.replace("\\","/"):v for k,v in m.items() if "/src/profile/" in k.replace("\\","/")} for m in manifests]
    assert fixtures[0] and fixtures[0]==fixtures[1],"Different profiling fixture source"
    envs=[json.loads((p/"batch-environment.json").read_text(encoding="utf-8")) for p in (args.before,args.after)]
    for key in ("seed","java","cpu","processors","weapon","warmupTicks"):
        assert envs[0][key]==envs[1][key],f"Environment mismatch: {key}"
    assert sorted(envs[0]["mods"])==sorted(envs[1]["mods"]),"Different modlist"
    flags=lambda env:[a for a in env["jvm"] if a.startswith(("-X","--add-opens","--add-exports"))]
    assert flags(envs[0])==flags(envs[1]),"JVM flags differ"
    load=lambda p:{(r["phase"],int(r["npc_actors"]),int(r["repeat"])):r for r in csv.DictReader((p/"batch-summary.csv").open(encoding="utf-8"))}
    before,after=load(args.before),load(args.after)
    assert before.keys()==after.keys() and len(before)==84,"Incomplete paired matrix"
    results=[]
    for key,b in before.items():
        a=after[key];bp,ap=int(b["projectiles"]),int(a["projectiles"])
        assert b["samples"]==a["samples"] and b["total_npcs"]==a["total_npcs"],key
        comparable=bp==ap==0 or min(bp,ap)>0 and abs(ap-bp)/max(bp,ap)<=.10
        latencies=("wall_first_p95_ticks","visual_first_p95_ticks","reacquire_p95_ticks")
        latency_ok=all(float(b[k])<0 and float(a[k])<0 or min(float(b[k]),float(a[k]))>=0 and float(a[k])<=float(b[k])+1 for k in latencies)
        # Non-wall scenes count warmup owners too; do not infer measured firing fairness from them.
        owner_scope=key[0] in ("combat","dynamic","soak","neutral","idle","patrol","unload")
        credit=comparable and latency_ok and owner_scope and int(a["owners_with_projectiles"])>=int(b["owners_with_projectiles"])
        results.append(dict(phase=key[0],count=key[1],repeat=key[2],comparable_projectiles=comparable,
            before_projectiles=bp,after_projectiles=ap,before_owners=int(b["owners_with_projectiles"]),after_owners=int(a["owners_with_projectiles"]),
            before_ai_p95_ms=float(b["ai_p95_ms"]),after_ai_p95_ms=float(a["ai_p95_ms"]),
            before_tick_p95_ms=float(b["tick_p95_ms"]),after_tick_p95_ms=float(a["tick_p95_ms"]),
            before_wall_first_p95_ticks=float(b["wall_first_p95_ticks"]),after_wall_first_p95_ticks=float(a["wall_first_p95_ticks"]),
            before_visual_first_p95_ticks=float(b["visual_first_p95_ticks"]),after_visual_first_p95_ticks=float(a["visual_first_p95_ticks"]),
            latency_not_slower=latency_ok,owners_measured_only=owner_scope,performance_credit_eligible=credit,
            ai_ratio_after_before=float(a["ai_p95_ms"])/float(b["ai_p95_ms"]) if credit and float(b["ai_p95_ms"])>0 else None))
    destination=args.after/"comparison.json"
    report=dict(environment_matched=True,paired_phases=len(results),comparable_phases=sum(r["comparable_projectiles"] for r in results),
                firing_phases=sum(max(r["before_projectiles"],r["after_projectiles"])>0 for r in results),
                matched_firing_phases=sum(r["comparable_projectiles"] and max(r["before_projectiles"],r["after_projectiles"])>0 for r in results),
                eligible_firing_phases=sum(r["performance_credit_eligible"] and min(r["before_projectiles"],r["after_projectiles"])>0 for r in results),
                comparison_rule="Same scenario/config; actual round count within 10% symmetric difference, otherwise no speedup credit",rows=results)
    destination.write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(json.dumps({k:v for k,v in report.items() if k!="rows"},indent=2))


if __name__=="__main__":main()
