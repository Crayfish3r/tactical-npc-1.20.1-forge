"""Isolated graphical client smoke, native readmission and a second client connection."""
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "forge-addon"


def content(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def stop(process):
    if process is not None and process.poll() is None:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=60)
        except (OSError, AttributeError, subprocess.TimeoutExpired):
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, check=False)


def main():
    output = PROJECT / "build/name-client-check" / dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S")
    server_dir, client_dir = output / "server", output / "client"
    server_dir.mkdir(parents=True)
    client_dir.mkdir()
    eula = (PROJECT / "run/standalone/eula.txt").read_bytes()
    assert "eula=true" in eula.decode().replace(" ", ""), "Requires previously accepted EULA"
    (server_dir / "eula.txt").write_bytes(eula)
    properties = {"server-ip": "127.0.0.1", "server-port": "25597", "online-mode": "false", "level-name": "names-client",
                  "level-seed": "8675309", "level-type": "minecraft:flat", "view-distance": "4", "simulation-distance": "4",
                  "generator-settings": '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'}
    (server_dir / "server.properties").write_text("\n".join(f"{k}={v}" for k, v in properties.items()) + "\n", encoding="utf-8")
    (client_dir / "options.txt").write_text("version:3465\nonboardAccessibility:false\nskipMultiplayerWarning:true\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nfullscreen:false\noverrideWidth:960\noverrideHeight:540\n", encoding="utf-8")
    base = [str(PROJECT / "gradlew.bat"), "--offline", "--console=plain", "-PclientSmoke", "-PclientSmokeConnect", "-PclientSmokePort=25597"]
    receipt = {"status": "FAIL", "output": str(output.relative_to(ROOT)), "clients": [], "scope": "graphical standalone client; aimed NPC at <6 blocks; API team NEVER; team recreation on reconnect; native NBT unload/readmission; forced RenderNameTagEvent ALLOW; vanilla VisibleSheep positive control; five skin stages per connection"}
    server, client = None, None
    started = time.monotonic()
    try:
        server_log = output / "server.log"
        with server_log.open("w", encoding="utf-8") as stream:
            command = base + ["runServer", f"-PdedicatedDir={server_dir}"]
            receipt["server_command"] = command
            server = subprocess.Popen(command, cwd=PROJECT, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT, text=True,
                                      creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            until = time.monotonic() + 180
            while not re.search(r"Done \([^)]+\)!", content(server_log)):
                assert server.poll() is None and time.monotonic() < until, str(server_log)
                time.sleep(.5)
            for connection in (1, 2):
                log = output / f"client-{connection}.log"
                command = base + ["runClient", f"-PclientDir={client_dir}"]
                with log.open("w", encoding="utf-8") as client_stream:
                    client = subprocess.Popen(command, cwd=PROJECT, stdin=subprocess.PIPE, stdout=client_stream, stderr=subprocess.STDOUT, text=True,
                                              creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
                    client.wait(timeout=240)
                text = content(log)
                receipt["clients"].append({"connection": connection, "exit_code": client.returncode, "command": command, "log": str(log.relative_to(ROOT))})
                assert client.returncode == 0 and "TACTICAL_CLIENT_SMOKE_PASS stages=5" in text and "hidden=true" in text and "TACTICAL_CLIENT_SMOKE_FAIL" not in text, str(log)
                assert text.count("TACTICAL_SKIN_SMOKE_STAGE stage=") == 5, str(log)
            assert content(server_log).count("TACTICAL_NAME_SMOKE_READMISSION count=2") == 2, str(server_log)
            stop(server)
            assert server.returncode == 0 and "All dimensions are saved" in content(server_log)
        receipt["screenshots"] = [str(p.relative_to(ROOT)) for p in sorted((client_dir / "screenshots").glob("*.png"))]
        assert len(receipt["screenshots"]) == 10, "Expected five captures on each connection"
        receipt["status"] = "PASS"
    except Exception as error:
        receipt["error"] = str(error)
        raise
    finally:
        stop(client)
        stop(server)
        receipt["seconds"] = round(time.monotonic() - started, 3)
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
        print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
