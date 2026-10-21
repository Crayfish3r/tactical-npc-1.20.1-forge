"""Run one explicit Forge test selection, retain raw log, fail closed on missing counts."""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]


def counts(text, exit_code):
    selected = re.findall(r"TACTICAL_TEST_SELECTION profile=\w+ count=(\d+)", text)
    completed = re.findall(r"(\d+) GAME TESTS COMPLETE", text)
    passed = re.findall(r"All (\d+) required tests passed", text, flags=re.IGNORECASE)
    count = int(completed[-1]) if completed else 0
    ok = (exit_code == 0 and len(selected) == len(completed) == len(passed) == 1
          and count > 0 and int(selected[0]) == count == int(passed[0]))
    return ok, count, int(selected[-1]) if selected else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=["FAST", "STAGE", "FULL"])
    parser.add_argument("--tests", help="Exact Class or Class#method selectors, comma-separated")
    parser.add_argument("--legacy", action="store_true", help="Only for adapter/integration risk or FULL matrix")
    parser.add_argument("--offline", action="store_true", help="Use only with populated pinned dependency cache")
    parser.add_argument("--plan", action="store_true", help="Gradle dry-run; never a test PASS")
    args = parser.parse_args()
    if (args.profile == "FULL") == bool(args.tests):
        parser.error("FULL forbids --tests; FAST/STAGE require --tests")
    if args.tests and not re.fullmatch(r"[A-Za-z0-9_#]+(?:,[A-Za-z0-9_#]+)*", args.tests):
        parser.error("Invalid selector syntax")
    command = [str(ROOT / "forge-addon" / ("gradlew.bat" if os.name == "nt" else "gradlew")),
               "runGameTestServer", "compileCompatJava", "-PcombatTests",
               f"-PtestProfile={args.profile}", "--console=plain"]
    if args.tests:
        command.append(f"-PtestSelection={args.tests}")
    if args.legacy:
        command.append("-PlegacyCompatibility")
    if args.offline:
        command.append("--offline")
    if args.plan:
        command.append("--dry-run")
    output = ROOT / "forge-addon/build/test-runs"
    output.mkdir(parents=True, exist_ok=True)
    name = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    # Preserve a fresh world/config per receipt; stale native forced chunks and
    # structure remnants from earlier selections must not become test inputs.
    runtime = output / f"{name}-runtime"
    runtime.mkdir(exist_ok=False)
    (runtime / "server.properties").write_text("# Isolated GameTest defaults\n", encoding="utf-8")
    command.append(f"-PgameTestDir={runtime}")
    log = output / f"{name}-{args.profile}{'-compat' if args.legacy else ''}.log"
    started = time.monotonic()
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=ROOT / "forge-addon", stdout=stream, stderr=subprocess.STDOUT)
    text = log.read_text(encoding="utf-8", errors="replace")
    ok, count, selected = counts(text, result.returncode)
    status = "NOT RUN" if args.plan and result.returncode == 0 else "PASS" if ok else "FAIL"
    summary = dict(status=status, profile=args.profile, legacy=args.legacy, executed=count,
                   selected=selected, seconds=round(time.monotonic()-started, 3),
                   exit_code=result.returncode, command=command, log=str(log.relative_to(ROOT)),
                   runtime=str(runtime.relative_to(ROOT)),
                   tasks=re.findall(r"^> Task (.+)$", text, flags=re.MULTILINE))
    log.with_suffix(".json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    if status == "FAIL":
        print("\n".join(text.splitlines()[-12:]), file=sys.stderr)
    return 0 if ok or status == "NOT RUN" else 1


if __name__ == "__main__":
    sys.exit(main())
