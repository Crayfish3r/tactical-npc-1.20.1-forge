"""Compile a separate Forge consumer against published main JAR; ordinary dedicated disk restart."""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "forge-addon"


def main():
    output = PROJECT / "build/match-api-consumer" / dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S")
    output.mkdir(parents=True, exist_ok=False)
    consumer = output / "consumer"
    shutil.copytree(ROOT / "tools/testing/fixtures/match-api-consumer", consumer)
    runtime = output / "runtime"
    runtime.mkdir()
    eula = (PROJECT / "run/standalone/eula.txt").read_bytes()
    assert "eula=true" in eula.decode().replace(" ", ""), "Requires previously accepted EULA"
    (runtime / "eula.txt").write_bytes(eula)
    properties = {"server-ip": "127.0.0.1", "server-port": "25596", "online-mode": "false", "level-name": "match-probe",
                  "level-seed": "8675309", "level-type": "minecraft:flat", "view-distance": "3", "simulation-distance": "3",
                  "generator-settings": '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'}
    (runtime / "server.properties").write_text("\n".join(f"{k}={v}" for k, v in properties.items()) + "\n", encoding="utf-8")
    artifact = PROJECT / "build/repository/dev/zuma/tacticalnpc/tacticalnpcai/0.2.0/tacticalnpcai-0.2.0.jar"
    started = time.monotonic()
    receipt = {"status": "FAIL", "artifact_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "phases": []}
    try:
        for phase, marker in [("save", "MATCH_API_PROBE_SAVED"), ("restart", "MATCH_API_PROBE_RESTART_PASS")]:
            log = output / f"{phase}.log"
            command = [str(PROJECT / "gradlew.bat"), "-p", str(consumer), "runServer", "--offline", "--console=plain",
                       "-Dorg.gradle.jvmargs=-Xmx3G", f"-PtacticalNpcProject={PROJECT}", f"-PprobeRuntime={runtime}", f"-PprobePhase={phase}"]
            with log.open("w", encoding="utf-8") as stream:
                process = subprocess.Popen(command, cwd=consumer, stdout=stream, stderr=subprocess.STDOUT,
                                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
                try:
                    process.wait(timeout=300)
                except subprocess.TimeoutExpired:
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, check=False)
                    raise
            content = log.read_text(encoding="utf-8", errors="replace")
            receipt["phases"].append({"phase": phase, "exit_code": process.returncode, "command": command, "log": str(log.relative_to(ROOT))})
            assert process.returncode == 0 and marker in content and "BUILD SUCCESSFUL" in content and "MATCH_API_PROBE_FAIL" not in content, str(log)
            assert "All dimensions are saved" in content and "TACTICAL_TEST_SELECTION" not in content
        receipt["status"] = "PASS"
    except Exception as error:
        receipt["error"] = str(error)
        raise
    finally:
        receipt.update(seconds=round(time.monotonic() - started, 3), output=str(output.relative_to(ROOT)),
                       scope="external ForgeGradle fg.deobf consumer compile; ordinary dedicated without NPCs; API group4+remote1; real remote chunk unload; disk save/restart; orphan admission; teams and manual state; no GameTest/profile source flags; test-only chunk tickets")
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
        print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
