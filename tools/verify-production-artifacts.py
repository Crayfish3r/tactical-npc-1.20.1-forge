"""Verify the distribution boundary after a plain Gradle build (no test flags)."""
import hashlib
import json
import pathlib
import struct
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
artifacts = []
for name, compat in [("tacticalnpcai-0.2.0.jar", False),
                     ("tacticalnpcai-0.2.0-npcs-compat.jar", True)]:
    path = root / "forge-addon/build/libs" / name
    with zipfile.ZipFile(path) as jar:
        assert not any(n.endswith(".jar") for n in jar.namelist()), "Bundled dependency JAR"
        assert not any(n.startswith("data/tacticalnpcai/structures/") for n in jar.namelist()), "Test arena shipped"
        classes = [n for n in jar.namelist() if n.endswith(".class")]
        assert classes, f"No compiled classes: {name}"
        for entry in classes:
            data = jar.read(entry)
            assert struct.unpack(">H", data[6:8])[0] == 61, entry
            assert entry.startswith("dev/zuma/tacticalnpc/"), f"Bundled dependency: {entry}"
            assert not any(token in entry for token in ["Tests", "ClientSmoke", "ServerProfile", "BatchProfile", "TeamCommandProfile", "CombatUpgradeProfile", "ActionRouteProfile",
                "RealTimeGameTestClock", "ProjectileProbe", "ScenarioIsolation"]), f"Test fixture shipped: {entry}"
            if not compat:
                assert b"mod/cdv/tnpc" not in data and b"mod.cdv.tnpc" not in data, f"NPCs linked: {entry}"
            if "/core/" in entry:
                assert not any(token in data for token in [b"net/minecraft/", b"net/minecraftforge/",
                    b"com/tacz/", b"mod/cdv/"]), f"Game dependency in core: {entry}"
        metadata = jar.read("META-INF/mods.toml").decode()
        assert 'version="0.2.0"' in metadata
        if compat:
            assert 'modId="tacznpcs"' in metadata and 'versionRange="[2.1.0]"' in metadata
        else:
            assert 'modId="tacznpcs"' not in metadata
            assert 'versionRange="[1.1.8-hotfix2]"' in metadata
            assert 'f_21361_' in jar.read("META-INF/accesstransformer.cfg").decode()
    artifacts.append(dict(file=str(path.relative_to(root)), bytes=path.stat().st_size,
                          sha256=hashlib.sha256(path.read_bytes()).hexdigest().upper(),
                          classes=len(classes), java_major=61, npc_compat=compat))
print(json.dumps(dict(result="PASS", artifacts=artifacts), indent=2))
