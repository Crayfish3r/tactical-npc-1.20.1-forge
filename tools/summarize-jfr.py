"""Summarize sampled server stacks from `jfr print --json --events jdk.ExecutionSample`."""
import collections
import json
import sys
events = json.load(open(sys.argv[1], encoding="utf-8-sig"))["recording"]["events"]
leaf, ai = collections.Counter(), collections.Counter()
server = tactical = 0
for event in events:
    value = event["values"]
    if (value.get("sampledThread") or {}).get("javaName") != "Server thread":
        continue
    frames = (value.get("stackTrace") or {}).get("frames") or []
    if not frames:
        continue
    names = [f['method']['type']['name'] + '.' + f['method']['name'] for f in frames]
    server += 1
    leaf[names[0]] += 1
    if any(n.startswith('dev/zuma/tacticalnpc/') for n in names):
        tactical += 1
        ai[names[0]] += 1
print('Server samples:', server, 'samples containing Tactical NPC:', tactical)
print('Server leaf:', leaf.most_common(15))
print('Tactical stack leaf:', ai.most_common(20))
