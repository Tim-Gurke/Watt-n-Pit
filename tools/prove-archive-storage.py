#!/usr/bin/env python3
"""Check actual HistoryDb and Repository through a small JDBC-backed Android SQL adapter.

No Gradle, APK, Android service or radio is run. The adapter substitutes storage APIs only.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--android-jar', required=True, type=Path)
parser.add_argument('--sqlite-jar', required=True, type=Path)
parser.add_argument('--slf4j-jar', required=True, type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
generated = out / 'generated/de/wattpit/app'
generated.mkdir(parents=True, exist_ok=True)
(generated / 'R.java').write_text('package de.wattpit.app; public final class R { public static final class drawable { public static final int ic_bolt=1; } }')
(generated / 'BuildConfig.java').write_text('package de.wattpit.app; public final class BuildConfig { public static final String VERSION_NAME="1.3.5"; }')
classes = out / 'classes'
classes.mkdir(exist_ok=True)
sources = list((root / 'app/src/main/java').rglob('*.java')) + list((root / 'tools/testing').rglob('*.java')) + list(generated.glob('*.java')) + [root / 'tools/ArchiveStorageProof.java']
subprocess.run(['java', 'com.sun.tools.javac.Main', '-cp', str(args.android_jar.resolve()), '-d', str(classes), *map(str, sources)], check=True)
classpath = os.pathsep.join(map(str, [classes, args.android_jar.resolve(), args.sqlite_jar.resolve(), args.slf4j_jar.resolve()]))
database = Path(tempfile.mkdtemp(prefix='database-', dir=out)) / 'proof.db'
log = []
phases = {}
for phase in ['atomic_write', 'atomic_resume', 'live_and_offline', 'metadata_resume', 'config_write', 'config_resume']:
    run = subprocess.run(['java', '-cp', classpath, 'de.wattpit.app.ArchiveStorageProof', str(database), phase], capture_output=True, text=True)
    log.append(run.stdout + run.stderr)
    print(run.stdout + run.stderr, end='', flush=True)
    (out / 'results.log').write_text(''.join(log))
    phases[phase] = {'exit_code': run.returncode, 'checks': [line[5:] for line in run.stdout.splitlines() if line.startswith('PASS ')]}
    (out / 'report.json').write_text(json.dumps({
        'scope': 'Actual working-tree HistoryDb and Repository methods, with JVM adapters for Android storage APIs and real SQLite. Each phase uses a fresh JVM. No Android lifecycle, GATT or physical device is exercised.',
        'source_sha256': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources if p.is_relative_to(root)},
        'phases': phases,
    }, indent=2) + '\n')
    run.check_returncode()
