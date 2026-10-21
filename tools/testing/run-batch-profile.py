"""Run the identical 08-14 ordinary dedicated fixture on frozen pre-09/current code."""
import argparse
import csv
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("variant", choices=["before", "after"])
    args = parser.parse_args()
    frozen = ROOT / "forge-addon/build/batch-baseline"
    project = (frozen if args.variant == "before" else ROOT) / "forge-addon"
    if args.variant == "before":
        assert (frozen / "baseline-source.json").exists(), "Missing captured pre-09 baseline"
        manifest = json.loads((frozen / "baseline-source.json").read_text(encoding="utf-8"))
        verified=0
        for name, digest in manifest.items():
            if name.replace("\\", "/").startswith(("forge-addon/src/main/", "forge-addon/src/compat/", "ai-core/src/main/", "forge-addon/libs/")):
                assert hashlib.sha256((frozen / name).read_bytes()).hexdigest() == digest, f"Baseline changed: {name}"
                verified+=1
        assert verified>0,"No baseline production files verified"
        # The SAME fixture/build configuration is required; production main/core stays frozen.
        shutil.copy2(ROOT / "forge-addon/build.gradle", project / "build.gradle")
        shutil.copytree(ROOT / "forge-addon/src/profile", project / "src/profile", dirs_exist_ok=True)
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S")
    output = ROOT / "forge-addon/build/batch14-profile" / f"{stamp}-{args.variant}"
    output.mkdir(parents=True, exist_ok=False)
    run=output / "runtime";run.mkdir()
    source_manifest = {str(p.relative_to(project.parent)): hashlib.sha256(p.read_bytes()).hexdigest()
        for folder in (project / "src/main", project / "src/compat", project / "src/profile", project.parent / "ai-core/src/main")
        for p in folder.rglob("*") if p.is_file()}
    (output / "source-manifest.json").write_text(json.dumps(source_manifest, indent=2), encoding="utf-8")
    names = ["server.properties", "eula.txt", "profile.jfr", "batch-summary.csv", "batch-samples.csv", "batch-environment.json"]
    existing = set()
    for name in names:
        if (run / name).exists():
            existing.add(name)
            shutil.copy2(run / name, output / f"preserved-{name}")
    eula = ROOT / "forge-addon/run/standalone/eula.txt"
    assert "eula=true" in eula.read_text(encoding="utf-8").replace(" ", ""), "Requires existing owner-accepted EULA"
    shutil.copy2(eula, run / "eula.txt")
    props = dict(**{"server-ip": "127.0.0.1", "server-port": "25594", "online-mode": "false",
        "level-name": f"batch14-{args.variant}-{stamp}", "level-seed": "8675309", "level-type": "minecraft:flat",
        "generator-settings": '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
        "view-distance": "4", "simulation-distance": "4", "difficulty": "normal", "spawn-protection": "0",
        "max-tick-time": "120000", "sync-chunk-writes": "true", "allow-flight": "true"})
    (run / "server.properties").write_text("\n".join(f"{k}={v}" for k, v in props.items()) + "\n", encoding="utf-8")
    command = [str(project / ("gradlew.bat" if os.name == "nt" else "gradlew")), "runServer", "compileCompatJava",
               "-Pprofile", "-PbatchProfile", f"-PprofileDir={run}", "--offline", "--console=plain"]
    started = time.monotonic()
    try:
        with (output / "server.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=project, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        for name in names[2:]:
            if (run / name).exists():
                shutil.copy2(run / name, output / name)
        rows = list(csv.DictReader((output / "batch-summary.csv").open(encoding="utf-8"))) if (output / "batch-summary.csv").exists() else []
        text = (output / "server.log").read_text(encoding="utf-8", errors="replace")
        ok = result.returncode == 0 and len(rows) == 84 and "BATCH_PROFILE_COMPLETE phases=85" in text and "BATCH_NATIVE_WARMUP_PASS" in text
        ok = ok and text.count("BATCH_CHUNK_UNLOAD_PASS actors=30") == 2 and text.count("BATCH_CHUNK_RELOAD_PASS actors=30") == 2
        meaningful = [r for r in rows if r["phase"] in ("combat", "soak") or r["phase"] == "npc_only" and int(r["npc_actors"]) > 1]
        ok = ok and all(int(r["projectiles"]) > 0 for r in meaningful) and len(meaningful) == 20
        summary = dict(status="PASS" if ok else "FAIL", variant=args.variant, rows=len(rows), exit_code=result.returncode,
                       seconds=round(time.monotonic()-started, 3), command=command, output=str(output.relative_to(ROOT)),
                       world=props["level-name"], environment=json.loads((output / "batch-environment.json").read_text(encoding="utf-8")) if (output / "batch-environment.json").exists() else None)
        (output / "receipt.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(json.dumps(summary, indent=2))
        return 0 if ok else 1
    finally:
        for name in names:
            if name in existing:
                shutil.copy2(output / f"preserved-{name}", run / name)
                assert hashlib.sha256((run / name).read_bytes()).digest() == hashlib.sha256((output / f"preserved-{name}").read_bytes()).digest()
            # A new isolated runtime has no owner files to restore; retain its reproducible config/world.


if __name__ == "__main__":
    raise SystemExit(main())
