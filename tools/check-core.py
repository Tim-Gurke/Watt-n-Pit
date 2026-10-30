#!/usr/bin/env python3
"""Compile and run the pure Java core tests without Gradle or an APK build."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--dependencies', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(str(args.dependencies.resolve() / name) for name in [
    'junit-4.13.2.jar', 'hamcrest-core-1.3.jar', 'sqlite-jdbc-3.46.1.0.jar', 'slf4j-api-1.7.36.jar'])
classpath += ''.join(os.pathsep + str(p) for p in args.dependencies.resolve().glob('zxing-*.jar'))
source_dir = root / 'app/src/main/java/de/wattpit/app/core'
test_dir = root / 'app/src/test/java/de/wattpit/app/core'
subprocess.run(['java', 'com.sun.tools.javac.Main', '-cp', classpath, '-d', str(out),
                *map(str, source_dir.glob('*.java')), *map(str, test_dir.glob('*.java'))], check=True)
classes = ['de.wattpit.app.core.' + p.stem for p in sorted(test_dir.glob('*Test.java'))]
run = subprocess.run(['java', '-cp', os.pathsep.join([str(out), str(root/'app/src/test/resources'), classpath]), 'org.junit.runner.JUnitCore', *classes], capture_output=True, text=True)
(out / 'results.log').write_text(run.stdout + run.stderr)
print(run.stdout + run.stderr, end='')
raise SystemExit(run.returncode)
