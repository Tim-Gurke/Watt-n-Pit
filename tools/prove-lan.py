"""Exercise production LAN host/client and SQL on the JVM; no Android radio/lifecycle test."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess

p=argparse.ArgumentParser()
p.add_argument('--output',type=Path,required=True)
p.add_argument('--android-jar',type=Path,required=True)
p.add_argument('--sqlite-jar',type=Path,required=True)
p.add_argument('--slf4j-jar',type=Path,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[1];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
generated=out/'generated';package=generated/'de/wattpit/app';package.mkdir(parents=True,exist_ok=True)
(package/'R.java').write_text('package de.wattpit.app; final class R { static final class drawable { static final int ic_bolt=1; } }')
version=re.search(r"versionName '([^']+)'",(root/'app/build.gradle').read_text()).group(1)
(package/'BuildConfig.java').write_text('package de.wattpit.app; final class BuildConfig { static final String VERSION_NAME='+json.dumps(version)+'; }')
android=generated/'android/os';android.mkdir(parents=True,exist_ok=True)
(android/'SystemClock.java').write_text('package android.os; public final class SystemClock { public static long elapsedRealtime(){return System.nanoTime()/1000000;} public static long uptimeMillis(){return elapsedRealtime();} public static long elapsedRealtimeNanos(){return System.nanoTime();} }')
(android/'Build.java').write_text('package android.os; public final class Build { public static final String MODEL="JVM test display"; public static final class VERSION { public static final int SDK_INT=35; } }')
classes=out/'classes';classes.mkdir(exist_ok=True)
sources=list((root/'app/src/main/java').rglob('*.java'))+list((root/'tools/testing').rglob('*.java'))+list(generated.rglob('*.java'))+[root/'tools/LanSyncProof.java']
native_cp=os.pathsep.join(map(str,[a.android_jar.resolve(),*a.android_jar.resolve().parent.glob('zxing-*.jar')]))
subprocess.run(['java','com.sun.tools.javac.Main','-cp',native_cp,'-d',str(classes),*map(str,sources)],check=True)
classpath=os.pathsep.join(map(str,[classes,a.android_jar.resolve(),a.sqlite_jar.resolve(),a.slf4j_jar.resolve(),*a.android_jar.resolve().parent.glob('zxing-*.jar')]))
r=subprocess.run(['java','-cp',classpath,'de.wattpit.app.LanSyncProof',str(out/'devices')],capture_output=True,text=True,timeout=90)
print(r.stdout+r.stderr,end='');(out/'results.log').write_text(r.stdout+r.stderr)
(out/'report.json').write_text(json.dumps({'exit_code':r.returncode,'checks':[line[5:] for line in r.stdout.splitlines() if line.startswith('PASS ')],'scope':'Real production TCP host/client, AEAD and SQLite. Android clock, device model and Context are test adapters; physical Android services, NSD, WLAN and hotspots remain untested.'},indent=2)+'\n')
r.check_returncode()
