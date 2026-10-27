#!/usr/bin/env python3
"""Check actual HistoryDb and Repository through a small JDBC-backed Android SQL adapter.

No Gradle, APK, Android service or radio is run. The adapter substitutes storage APIs only.
"""
import argparse
import hashlib
import json
import os
import re
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
version=re.search(r"versionName '([^']+)'",(root/'app/build.gradle').read_text()).group(1)
(generated / 'BuildConfig.java').write_text('package de.wattpit.app; public final class BuildConfig { public static final String VERSION_NAME='+json.dumps(version)+'; }')
service=(root/'app/src/main/java/de/wattpit/app/ArchiveService.java').read_text()
sig='private void incoming(BluetoothGattCharacteristic c,byte[] v)'
start=service.index(sig);brace=service.index('{',start);depth=1;end=brace+1
while depth:
    depth+=(service[end]=='{')-(service[end]=='}');end+=1
incoming=service[start:end]
(generated/'IncomingHarness.java').write_text('package de.wattpit.app;\nimport java.util.*;import java.util.concurrent.*;import de.wattpit.app.core.*;\nfinal class IncomingHarness {\n static UUID uuid(int n){return UUID.fromString(String.format(Locale.ROOT,"306b%04x-b081-4037-83dc-e59fcc3cdfd0",n));}\n static final UUID CONTROL=uuid(2),DATA=uuid(3),BULK=uuid(4);\n final ArchiveLink link=new ArchiveLink();final Queue<byte[]> received=new LinkedBlockingQueue<>(512);boolean cancelled;\n static final class BluetoothGattCharacteristic {final UUID id;BluetoothGattCharacteristic(UUID id){this.id=id;}UUID getUuid(){return id;}}\n void feed(int channel,byte[] bytes){incoming(new BluetoothGattCharacteristic(uuid(channel)),bytes);}\n'+incoming+'\n}')
classes = out / 'classes'
classes.mkdir(exist_ok=True)
sources = list((root / 'app/src/main/java').rglob('*.java')) + list((root / 'tools/testing').rglob('*.java')) + list(generated.glob('*.java')) + [root / 'tools/ArchiveStorageProof.java', root / 'tools/ArchiveClockProof.java', root / 'tools/SolarPlanningProof.java', root / 'tools/WeatherContractProof.java', root / 'app/src/test/java/de/wattpit/app/core/CapturedVictronDevice.java']
native_cp=os.pathsep.join(map(str,[args.android_jar.resolve(),*args.android_jar.resolve().parent.glob('zxing-*.jar')]))
subprocess.run(['java', 'com.sun.tools.javac.Main', '-cp', native_cp, '-d', str(classes), *map(str, sources)], check=True)
classpath = os.pathsep.join(map(str, [classes, args.android_jar.resolve(), args.sqlite_jar.resolve(), args.slf4j_jar.resolve(),*args.android_jar.resolve().parent.glob('zxing-*.jar')]))
database = Path(tempfile.mkdtemp(prefix='database-', dir=out)) / 'proof.db'
log = []
phases = {}
for phase in ['atomic_write', 'atomic_resume', 'live_and_offline', 'metadata_resume', 'config_write', 'config_resume', 'capture_write', 'capture_resume', 'capture_gap', 'manual_queue', 'daily_energy', 'solar_planning', 'weather_contract', 'unavailable_current', 'clock_diagnostic_write', 'clock_diagnostic_resume', 'clock_alignment_write', 'clock_alignment_resume', 'clock_alignment_failure', 'clock_alignment_reset']:
    run = subprocess.run(['java', '-cp', classpath, 'de.wattpit.app.ArchiveStorageProof', str(database), phase, str(root / 'app/src/test/resources/victron/smartshunt-trends.tsv'), str(out/'weather-contract-fixture.json')], capture_output=True, text=True)
    log.append(run.stdout + run.stderr)
    print(run.stdout + run.stderr, end='', flush=True)
    (out / 'results.log').write_text(''.join(log))
    phases[phase] = {'exit_code': run.returncode, 'checks': [line[5:] for line in run.stdout.splitlines() if line.startswith('PASS ')]}
    (out / 'report.json').write_text(json.dumps({
        'scope': 'Actual HistoryDb, Repository and incoming notification method with Android API adapters and real SQLite. Public sanitized VictronConnect samples, fresh-process resume and three-hour missing window. No Android lifecycle, physical GATT connection or radio speed benchmark.',
        'fixture_sha256': hashlib.sha256((root/'app/src/test/resources/victron/smartshunt-trends.tsv').read_bytes()).hexdigest(),
        'source_sha256': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources if p.is_relative_to(root)},
        'phases': phases,
    }, indent=2) + '\n')
    run.check_returncode()
