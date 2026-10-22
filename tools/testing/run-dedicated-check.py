"""Plain dedicated startup/reload/save/restart/stop on a new isolated world; preserve properties."""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "forge-addon"
SELECTOR = "@e[type=tacticalnpcai:npc,tag=team-update-subject,limit=1]"


def text(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def wait(log, process, predicate, timeout=120):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        content = text(log)
        if predicate(content):
            return content
        if process.poll() is not None:
            raise RuntimeError(f"Server exited early ({process.returncode}); {log}")
        time.sleep(.25)
    raise TimeoutError(str(log))


def send(process, *commands):
    process.stdin.write("\n".join(commands) + "\n")
    process.stdin.flush()


def capture(process, log):
    offset = len(text(log))
    send(process, *(f"data get entity {SELECTOR} {field}" for field in ("tacticalnpc", "UUID", "HandItems")))
    content = wait(log, process, lambda s: s[offset:].count("has the following entity data:") >= 3, 30)
    lines = [line.split("has the following entity data: ", 1)[1] for line in content[offset:].splitlines() if "has the following entity data: " in line]
    assert len(lines) == 3, lines
    return dict(zip(("policy", "uuid", "hand_items"), lines))


def unload_reload(process, log):
    remote="@e[type=tacticalnpcai:npc,tag=name-unload-probe,limit=1]"
    send(process,"forceload add 512 512 528 528")
    until=time.monotonic()+40
    while True:
        offset=len(text(log));send(process,"execute if block 520 -61 520 minecraft:grass_block run say REMOTE_CHUNK_READY");time.sleep(1)
        if 'REMOTE_CHUNK_READY' in text(log)[offset:]:break
        assert time.monotonic()<until,"Remote fixture chunks not ready"
    offset=len(text(log))
    send(process,"execute positioned 520 -60 520 run npc spawn team remote-probe", "tag @e[type=tacticalnpcai:npc,team=remote-probe] add name-unload-probe", f"data get entity {remote} tacticalnpc")
    content=wait(log,process,lambda s:'has the following entity data:' in s[offset:],40)[offset:]
    match=re.search(r'name: "([A-Za-z][A-Za-z0-9]*)"',content)
    assert match,content
    name=match.group(1)
    send(process,f"npc mode {name} stand", "forceload remove 512 512 528 528", "save-all flush")
    until=time.monotonic()+60
    while True:
        offset=len(text(log))
        send(process,f"execute unless entity {remote} run say NAME_PROBE_UNLOADED",f"data get entity {remote} tacticalnpc")
        time.sleep(1)
        if 'NAME_PROBE_UNLOADED' in text(log)[offset:]:break
        assert time.monotonic()<until,"Remote entity failed to unload naturally"
    offset=len(text(log));send(process,f"npc mode {name} assault")
    wait(log,process,lambda s:f"NPC {name} is unloaded" in s[offset:],20)
    send(process,"forceload add 512 512 528 528")
    until=time.monotonic()+40
    while True:
        offset=len(text(log));send(process,f"data get entity {remote} tacticalnpc");time.sleep(1)
        current=text(log)[offset:]
        if 'has the following entity data:' in current:
            assert f'name: "{name}"' in current and 'order: "HOLD_POSITION"' in current,current
            break
        assert time.monotonic()<until,"Remote entity failed to reload"
    offset=len(text(log));send(process,f"npc info {name}")
    wait(log,process,lambda s:f"{name} team=remote-probe mode=stand availability=loaded" in s[offset:],20)
    send(process,f"kill {remote}","forceload remove 512 512 528 528")
    return name


def run(output, index, first):
    log = output / f"server-{index}.log"
    command = [str(PROJECT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "runServer", "--offline", "--console=plain", f"-PdedicatedDir={output / 'runtime'}"]
    with log.open("w", encoding="utf-8") as stream:
        process = subprocess.Popen(command, cwd=PROJECT, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT, text=True,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        try:
            wait(log, process, lambda s: bool(re.search(r'Done \([^)]+\)!', s)), 180)
            if first:
                send(process, "gamerule doMobSpawning false", "forceload add -32 -32 32 32")
                time.sleep(1)
                send(process, "execute positioned 0 -60 0 run npc spawn team team-update",
                     "tag @e[type=tacticalnpcai:npc] add team-update-subject", "execute positioned 0 -60 8 run npc spawn team spare",
                     "kill @e[type=tacticalnpcai:npc,tag=!team-update-subject]",
                     "npc ammo Steve finite 37", "npc equip Steve tacz:glock_17", "npc team Steve team-update",
                     "npc mode Steve patrol 8", "npc mode Steve assault", "npc mode Steve stand", "npc mode Steve defend 128")
                time.sleep(8)  # Ordinary native time; no GameTest pacing or FakePlayer.
                count = text(log).count("Pinned TaCZ xmag reload script verified: true")
                send(process, "reload")
                wait(log, process, lambda s: s.count("Pinned TaCZ xmag reload script verified: true") > count, 120)
            if not first:
                offset=len(text(log))
                send(process,"execute positioned 0 -60 8 run npc spawn team restart-probe","kill @e[type=tacticalnpcai:npc,tag=!team-update-subject]")
                fresh=wait(log,process,lambda s:'names=[' in s[offset:],30)[offset:]
                assert 'names=[Tyler]' in fresh,"Restart reused an archived name: "+fresh
            time.sleep(4)
            if first:
                probe_name=unload_reload(process,log)
                assert probe_name not in ("Steve","Kevin")
            data = capture(process, log)
            assert "schema: 6" in data["policy"] and "leash: 128.0d" in data["policy"] and 'name: "Steve"' in data["policy"], data["policy"]
            offset=len(text(log))
            team_selector=SELECTOR.replace(',limit=1',',team=team-update,limit=1')
            send(process,f"execute if entity {team_selector} run say TEAM_MEMBERSHIP_PRESENT","npc info Steve")
            wait(log,process,lambda s:'TEAM_MEMBERSHIP_PRESENT' in s[offset:] and 'team=team-update mode=' in s[offset:],30)
            data['native_team']='team-update'
            send(process, "npc debug diagnostics", "save-all flush", "stop")
            assert process.wait(timeout=120) == 0, log
            content = text(log)
            assert "BUILD SUCCESSFUL" in content and "All dimensions are saved" in content, log
            if first:
                for marker in ("Ammo policy FINITE reserve=37 assigned to 1", "Equipped tacz:glock_17 for 1", "Team team-update assigned to 1", "Order PATROL assigned to 1",
                               "Order ASSAULT assigned to 1", "Order HOLD_POSITION assigned to 1", "Order DEFEND_AREA assigned to 1"):
                    assert marker in content, f"Missing successful ordinary command: {marker}; {log}"
                assert content.count("Spawned 1/1 independent NPC(s)") == 3, log
            assert not any(marker in content for marker in ("TACTICAL_TEST_SELECTION", "BATCH_PROFILE_COMPLETE", "Game test server shutting down")), "Plain run contains test/profile fixture"
            return data
        finally:
            if process.poll() is None:
                try:
                    send(process, "stop")
                    process.wait(timeout=120)
                except (OSError, subprocess.TimeoutExpired):
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


def main():
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S")
    output = PROJECT / "build" / "command-dedicated-check" / stamp
    output.mkdir(parents=True, exist_ok=False)
    original_properties = PROJECT / "run/standalone/server.properties"
    original_eula = PROJECT / "run/standalone/eula.txt"
    original = original_properties.read_bytes()
    eula = original_eula.read_bytes()
    assert "eula=true" in eula.decode("utf-8").replace(" ", ""), "Requires owner-accepted EULA"
    (output / "server.properties.preserved").write_bytes(original)
    run_dir=output / "runtime";run_dir.mkdir()
    properties=run_dir / "server.properties"
    (run_dir / "eula.txt").write_bytes(eula)
    world = "command-normal-" + stamp
    # Seed actual legacy world configuration before the ordinary loading event.
    config_dir=run_dir / world / "serverconfig";config_dir.mkdir(parents=True)
    config_file=config_dir / "tacticalnpcai-server.toml"
    config_file.write_text('difficultyProfile = "UNFAIR"\nhearingRange = 192.0\nturnRate = 2.0\nreactionTicks = 20\ntelemetry = true\n',encoding="utf-8")
    props = dict(**{"server-ip": "127.0.0.1", "server-port": "25595", "online-mode": "false", "level-name": world,
        "level-seed": "8675309", "level-type": "minecraft:flat", "generator-settings": '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}', "spawn-protection": "0", "view-distance": "4", "simulation-distance": "4"})
    properties.write_text("\n".join(f"{k}={v}" for k, v in props.items()) + "\n", encoding="utf-8")
    started = time.monotonic()
    try:
        before, after = run(output, 1, True), run(output, 2, False)
        (output / "before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
        (output / "after.json").write_text(json.dumps(after, indent=2), encoding="utf-8")
        migrated=config_file.read_text(encoding="utf-8")
        assert 'difficultyProfile = "HARD"' in migrated and 'hearingRange = 128.0' in migrated and 'turnRate = 17.0' in migrated and 'reactionTicks = 3' in migrated and 'telemetry = true' in migrated,migrated
        assert 'Migrated legacy NPC difficulty/combat overrides to HARD' in text(output / 'server-1.log')
        assert before == after, "Save/restart changed name/identity/policy/anchor/native stack"
        receipt = dict(status="PASS", world=world, seconds=round(time.monotonic()-started, 3), output=str(output.relative_to(ROOT)),
                       scope="plain dedicated startup, real reload, unified name/selector commands, native scoreboard team, PATROL/STAND/DEFEND128, name/UUID/policy/finite/native stack save/restart, natural remote chunk unload/load, actual UNFAIR config migration, clean stop; no human combat")
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
        print(json.dumps(receipt, indent=2))
    except Exception as error:
        (output / "receipt.json").write_text(json.dumps(dict(status="FAIL",error=str(error),output=str(output.relative_to(ROOT)),seconds=round(time.monotonic()-started,3)),indent=2),encoding="utf-8")
        raise
    finally:
        assert original_properties.read_bytes() == original and original_eula.read_bytes() == eula
        (output / "preservation.json").write_text(json.dumps(dict(properties_sha256=hashlib.sha256(original).hexdigest(), eula_sha256=hashlib.sha256(eula).hexdigest(), original_files_unchanged=True, isolated_runtime=str(run_dir)), indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
