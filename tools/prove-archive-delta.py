#!/usr/bin/env python3
"""Run the working-tree planning classes locally; every phase uses a fresh JVM and SQLite connection."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--sqlite-jar', required=True, type=Path)
parser.add_argument('--slf4j-jar', required=True, type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
classes = out / 'classes'
classes.mkdir(exist_ok=True)
names = ['ArchiveCodec', 'ArchiveTimeReference', 'VictronSettings', 'ArchiveMerge', 'ArchiveSyncPlan', 'ArchiveTransferPlan',
         'ArchiveSchedule', 'HistorySchema', 'Reading', 'SampleTimeline', 'EnergyIntegral']
sources = [root / 'app/src/main/java/de/wattpit/app/core' / (name + '.java') for name in names]
subprocess.run(['java', 'com.sun.tools.javac.Main', '-d', str(classes),
                *map(str, sources), str(root / 'tools/ArchiveDeltaProof.java')], check=True)
classpath = os.pathsep.join(map(str, [classes, args.sqlite_jar.resolve(), args.slf4j_jar.resolve()]))
end = 21 * 86400
results = {}

def phase(name, database, now, *, newest=end, limit=-1, unavailable=False, live=False):
    command = ['java', '-cp', classpath, 'de.wattpit.app.core.ArchiveDeltaProof',
               str(out / database), str(newest), str(now), str(limit), str(unavailable).lower(), str(live).lower()]
    run = subprocess.run(command, check=True, capture_output=True, text=True)
    (out / (name + '.log')).write_text(run.stdout + run.stderr)
    result = json.loads(run.stdout.strip().splitlines()[-1])
    shutil.copyfile(out / (database + '.requests.tsv'), out / (name + '.requests.tsv'))
    results[name] = result
    print(f"{name}: requests={result['requests']} points={result['points']} repeated={result['repeated']} audit_missing={result['audit_missing']}", flush=True)
    return result

for p in out.glob('*.db*'):
    p.unlink()

closed = phase('closed_first', 'closed.db', end + 10000)
phase('closed_second', 'closed.db', end + 10000 + closed['elapsed_seconds'] + 1)
phase('closed_new_values_only', 'closed.db', end + 10000 + closed['elapsed_seconds'] + 62, newest=end + 60)
fresh = phase('fresh_first', 'fresh.db', end)
phase('fresh_second', 'fresh.db', end + fresh['elapsed_seconds'] + 1)
unavailable = phase('unavailable_first', 'unavailable.db', end, unavailable=True)
phase('unavailable_second_after_5min', 'unavailable.db', end + unavailable['elapsed_seconds'] + 300, unavailable=True)
cancel_closed = phase('cancel_closed_after_one_block', 'cancel-closed.db', end + 10000, limit=1)
phase('resume_closed', 'cancel-closed.db', end + 10000 + cancel_closed['elapsed_seconds'] + 5)
cancel_fresh = phase('cancel_fresh_after_eight_blocks', 'cancel-fresh.db', end, limit=8, unavailable=True)
phase('resume_fresh', 'cancel-fresh.db', end + cancel_fresh['elapsed_seconds'] + 5, unavailable=True)
phase('complete_live_recording', 'live.db', end, live=True)

checks = {
    'three_weeks_complete': results['closed_first']['audit_missing'] == 0 and results['closed_first']['solar_coverage'] == 1 and results['closed_first']['load_coverage'] == 1,
    'second_closed_run_zero_requests': results['closed_second']['points'] == 0,
    'new_values_without_repeating_confirmed_points': results['closed_new_values_only']['repeated'] == 0,
    'second_fresh_run_zero_requests': results['fresh_second']['points'] == 0,
    'unchanged_unavailable_values_zero_requests': results['unavailable_second_after_5min']['points'] == 0,
    'resume_closed_without_repeats': results['resume_closed']['repeated'] == 0,
    'resume_fresh_without_repeats': results['resume_fresh']['repeated'] == 0,
    'complete_live_data_no_measurement_requests': results['complete_live_recording']['points'] == 0,
    'no_automatic_connection_permission_for_complete_data': not results['complete_live_recording']['automatic_due_after_five_minutes'],
}
report = {'version': 'delta-fix-working-tree', 'scope': 'Working-tree production core and production SQLite schema/queries; independent deterministic protocol peer. Every phase runs in a new JVM. One second of simulated transfer time per response is applied to the production completion-marker timestamps. The next phase starts after this elapsed time. Android radio and user hardware are not executed.',
          'source_sha256': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
          'scenarios': results, 'requirements': checks, 'all_requirements_met': all(checks.values())}
(out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
print('\nRequirement results:')
for name, ok in checks.items():
    print(('PASS ' if ok else 'FAIL ') + name)
raise SystemExit(0 if report['all_requirements_met'] else 1)
