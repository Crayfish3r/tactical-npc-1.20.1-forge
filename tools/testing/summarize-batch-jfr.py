"""Summarize real JFR CPU/allocation samples, retaining fixture/native/hearing/physics scopes."""
import argparse
import collections
import json
from pathlib import Path
import subprocess
import shutil
import bisect
import csv
import datetime as dt
import re


def read_events(path):
    """Decode one event at a time; a real 20-minute recording need not fit in RAM."""
    decoder=json.JSONDecoder()
    with path.open(encoding="utf-8") as stream:
        buffer=""
        while True:
            chunk=stream.read(1024*1024)
            if not chunk:raise ValueError("Missing JFR recording.events array")
            buffer+=chunk
            match=re.search(r'"events"\s*:\s*\[',buffer)
            if match:
                offset=match.end();break
        while True:
            separator=re.match(r'[\s,]*',buffer[offset:])
            offset+=separator.end()
            if offset<len(buffer) and buffer[offset]==']':return
            try:event,end=decoder.raw_decode(buffer,offset)
            except json.JSONDecodeError:
                chunk=stream.read(1024*1024)
                if not chunk:raise ValueError("Truncated JFR events JSON")
                buffer=buffer[offset:]+chunk;offset=0;continue
            yield event
            offset=end
            if offset>1024*1024:buffer=buffer[offset:];offset=0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recording", type=Path)
    args = parser.parse_args()
    jfr = shutil.which("jfr")
    assert jfr and args.recording.is_file(), "JDK jfr and actual recording required"
    raw = args.recording.with_suffix(".events.json")
    with raw.open("w", encoding="utf-8") as stream:
        subprocess.run([jfr, "print", "--json", "--events", "jdk.ExecutionSample,jdk.ObjectAllocationSample,jdk.GarbageCollection,jdk.GCHeapSummary", str(args.recording)], stdout=stream, check=True)
    events = read_events(raw)
    def epoch(value):return dt.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    phase_csv=args.recording.parent / "batch-summary.csv"
    if phase_csv.is_file():
        phases=sorted((epoch(r["start_utc"]),epoch(r["end_utc"]),f'{r["phase"]}:{r["npc_actors"]}:{r["repeat"]}') for r in csv.DictReader(phase_csv.open(encoding="utf-8")))
    else:
        phase_csv=args.recording.parent / "upgrade-phases.csv"
        if not phase_csv.is_file():phase_csv=args.recording.parent / "actions-phases.csv"
        assert phase_csv.is_file(), "Requires actual phase/warmup boundaries next to JFR"
        phases=sorted((epoch(r["start"]),epoch(r["end"]),r["phase"]) for r in csv.DictReader(phase_csv.open(encoding="utf-8")))
    starts=[p[0] for p in phases]
    coverage=collections.Counter()
    hot = collections.Counter()
    allocations = collections.Counter()
    allocation_stacks = collections.Counter()
    allocation_categories = collections.Counter()
    phase_cpu = collections.defaultdict(collections.Counter)
    phase_allocations = collections.defaultdict(collections.Counter)
    categories = collections.Counter()
    kinds = collections.Counter()
    first = last = None
    tactical_server = server_samples = 0
    def category(names):
        if any("EntityKineticBullet.tick" in n or "EntityKineticBullet.m_8119_" in n for n in names):return "projectile_physics"
        if any("GunHearing" in n for n in names):return "hearing_delivery"
        if any(any(token in n for token in ("TaczGunController.maintain", "LivingEntityDrawGun", "LivingEntityReload", "LivingEntityBolt", "LivingEntitySprint", "LivingEntityAim", "LivingEntityHeat", "LivingEntitySpeedModifier")) for n in names):return "native_maintenance"
        if any("LivingEntityShoot" in n for n in names):return "native_shoot"
        if any("MovementExecutor" in n or "ActionRouteExecutor" in n or "ActionRoutePlanner" in n for n in names):return "action_navigation"
        if any("TacticalCombatGoal" in n for n in names):return "goal_or_native_safety"
        if any("BatchProfile" in n or "ServerProfile" in n or "TeamCommandProfile" in n or "CombatUpgradeProfile" in n or "ActionRouteProfile" in n for n in names):return "profiling_fixture"
        return "other_server"
    for event in events:
        kind, value = event["type"], event["values"]
        kinds[kind] += 1
        at = value.get("startTime")
        if at:
            first = at if first is None or at < first else first
            last = at if last is None or at > last else last
        if not at:continue
        index=bisect.bisect_right(starts,epoch(at))-1
        if index<0 or epoch(at)>phases[index][1]:continue
        coverage[phases[index][2]]+=1
        thread = value.get("sampledThread") or value.get("eventThread") or {}
        if thread.get("javaName") != "Server thread":
            continue
        frames = (value.get("stackTrace") or {}).get("frames") or []
        names = [f["method"]["type"]["name"] + "." + f["method"]["name"] for f in frames]
        scope=category(names)
        if kind == "jdk.ObjectAllocationSample":
            weight=value.get("weight",0)
            allocations[(value.get("objectClass") or {}).get("name", "unknown")] += weight
            allocation_stacks[" <- ".join(names[:6])] += weight
            allocation_categories[scope] += weight
            phase_allocations[phases[index][2]][scope] += weight
        if kind != "jdk.ExecutionSample" or not names:
            continue
        server_samples += 1
        hot[" <- ".join(names[:6])] += 1
        if any(n.startswith("dev/zuma/tacticalnpc/") and not any(p in n for p in ("BatchProfile","ServerProfile","TeamCommandProfile","CombatUpgradeProfile","ActionRouteProfile")) for n in names):
            tactical_server += 1
        categories[scope] += 1
        phase_cpu[phases[index][2]][scope] += 1
    result = dict(status="PASS" if server_samples > 0 and kinds["jdk.ObjectAllocationSample"] > 0 else "FAIL",
                  recording=str(args.recording), event_counts=dict(kinds), first_event=first, last_event=last,
                  server_cpu_samples=server_samples, tactical_server_samples=tactical_server,
                  measured_phase_event_coverage=dict(coverage), excluded="startup,warmup,teardown; phase boundaries from actual CSV",
                  server_cpu_categories=dict(categories), top_server_stacks=hot.most_common(20),
                  server_cpu_categories_by_phase=dict(phase_cpu),
                  server_allocation_categories_estimated_bytes=dict(allocation_categories),
                  server_allocation_categories_by_phase_estimated_bytes=dict(phase_allocations),
                  top_server_allocation_stacks=allocation_stacks.most_common(20),
                  server_allocation_estimated_bytes=allocations.most_common(20),
                  note="Sampling estimates; categories are mutually exclusive by priority; fixture separated. Goal scope is not total server cost.")
    destination = args.recording.with_suffix(".summary.json")
    destination.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("status", "event_counts", "server_cpu_samples", "server_cpu_categories")}, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
