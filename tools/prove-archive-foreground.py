#!/usr/bin/env python3
"""Exercise production trigger/Repository with controlled time, SQLite and simulated GATT.
The foreground methods are extracted unchanged; only Android UI/executor APIs are adapted.
Repository wall-clock reads are substituted only in the generated test copy.
"""
import argparse, hashlib, json, os, re, subprocess
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--dependencies',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--mode',choices=['baseline','fixed'],required=True)
p.add_argument('--source-root',type=Path)
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
production=a.source_root.resolve() if a.source_root else root
out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
generated=out/'generated/de/wattpit/app';generated.mkdir(parents=True,exist_ok=True)
main=(production/'app/src/main/java/de/wattpit/app/MainActivity.java').read_text()
def method(signature):
    start=main.index(signature);brace=main.index('{',start);depth=1;end=brace+1
    while depth:
        depth += (main[end]=='{')-(main[end]=='}');end+=1
    return main[start:end]
timer=re.search(r'private final Runnable retryIncompleteArchive=.*?;}};',main).group(0)
methods='\n'.join(method(s) for s in ['private void startAutomaticImport(){','private void startAutomaticImport(boolean retriesOnly){'])
harness='''package de.wattpit.app;
import java.util.concurrent.Executor;
final class ForegroundHarness {
 static final class Lan { boolean viewer(){return false;} }
 final Lan lan=new Lan();
 final Repository repository;
 final Executor files=Runnable::run;
 final Web web=new Web();
 boolean foreground=true,pendingStart,checkingArchive;
 String pendingArchive;
 ForegroundHarness(Repository r){repository=r;}
 boolean isFinishing(){return false;}
 boolean isDestroyed(){return false;}
 boolean repositoryArchiveActive(){return repository.archiveBusy;}
 void runOnUiThread(Runnable action){action.run();}
 void requestStart(){
   if(pendingArchive!=null)try{
     boolean retry=pendingArchive.equals("RETRY");
     int n=repository.automaticArchiveDevices(retry).length();
     for(int i=0;i<n;i++)ArchiveForegroundProof.importData();
   }catch(Exception e){throw new RuntimeException(e);}
   pendingArchive=null;
 }
 void enter(){startAutomaticImport();}
 void tick(){retryIncompleteArchive.run();}
 final class Web { void postDelayed(Runnable action,long delay){ } }
'''+timer+'\n'+methods+'\n}'
(generated/'ForegroundHarness.java').write_text(harness)
repository=production/'app/src/main/java/de/wattpit/app/Repository.java'
(generated/'Repository.java').write_text(repository.read_text().replace('System.currentTimeMillis()','ArchiveForegroundProof.now'))
(generated/'R.java').write_text('package de.wattpit.app; public final class R { public static final class drawable { public static final int ic_bolt=1; } }')
version=re.search(r"versionName '([^']+)'",(production/'app/build.gradle').read_text()).group(1)
(generated/'BuildConfig.java').write_text('package de.wattpit.app; public final class BuildConfig { public static final String VERSION_NAME='+json.dumps(version)+'; }')
classes=out/'classes';classes.mkdir(exist_ok=True)
sources=[x for x in (production/'app/src/main/java').rglob('*.java') if x!=repository]+list((production/'tools/testing').rglob('*.java'))+list(generated.glob('*.java'))+[root/'tools/ArchiveForegroundProof.java']
deps=a.dependencies.resolve()
native_cp=os.pathsep.join(map(str,[deps/'android-api35.jar',*deps.glob('zxing-*.jar')]))
subprocess.run(['java','com.sun.tools.javac.Main','-cp',native_cp,'-d',str(classes),*map(str,sources)],check=True)
cp=os.pathsep.join(map(str,[classes,deps/'android-api35.jar',deps/'sqlite-jdbc-3.46.1.0.jar',deps/'slf4j-api-1.7.36.jar',*deps.glob('zxing-*.jar')]))
database=out/'foreground.db'
for x in out.glob('foreground.db*'):x.unlink()
run=subprocess.run(['java','-cp',cp,'de.wattpit.app.ArchiveForegroundProof',str(database),a.mode],text=True,capture_output=True)
(out/'results.log').write_text(run.stdout+run.stderr)
report={'mode':a.mode,'exit_code':run.returncode,'scope':__doc__,'source_sha256':{str(x.relative_to(production) if x.is_relative_to(production) else x.relative_to(root)):hashlib.sha256(x.read_bytes()).hexdigest() for x in [repository,production/'app/src/main/java/de/wattpit/app/MainActivity.java',root/'tools/ArchiveForegroundProof.java']},'results':[x for x in run.stdout.splitlines() if x.startswith(('PASS ','RESULT '))]}
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
print(run.stdout+run.stderr,end='')
run.check_returncode()

