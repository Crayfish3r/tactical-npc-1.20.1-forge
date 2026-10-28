"""Reuse the ordinary dedicated harness with accuracy-specific commands/assertions."""
from pathlib import Path

path=Path(__file__).with_name('run-dedicated-check.py')
source=path.read_text(encoding='utf-8')
source=source.replace('"npc ammo Steve finite 37",','"npc difficulty Steve default", "npc ammo Steve finite 37",')
source=source.replace('assert "schema: 7" in data["policy"]','assert \'difficulty: "DEFAULT"\' in data["policy"] and "schema: 7" in data["policy"]')
source=source.replace('telemetry = true\\n\',encoding=', 'telemetry = true\\ndefaultAimErrorMultiplier = 4.0\\n\',encoding=')
source=source.replace("assert 'difficultyProfile =", "assert 'defaultAimErrorMultiplier = 4.0' in migrated\n        assert 'difficultyProfile =")
source=source.replace('plain dedicated startup, real reload,','accuracy DEFAULT command and NBT persistence; multiplier preserved by migration; plain dedicated startup, real reload,')
exec(compile(source,str(path),'exec'),dict(__file__=str(path),__name__='__main__'))
