package de.wattpit.app;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import de.wattpit.app.core.EnergyIntegral;

/** One plant replica per display handset. It never imports into that handset's own plant. */
final class ViewerAnalysis {
    private final LanSync lan;
    final Repository repo;
    final SolarPlanning planning;
    private final RollerLedger ledger;
    private final Object identity;
    private Consumer<String> progress;
    private JSONObject metadata=new JSONObject();
    private long progressAt;
    private String epoch="";
    private long rawRevision,downloadedRows,downloadedBlocks;
    ViewerAnalysis(Context context,LanSync lan,String id){
        if(!id.matches("[A-Za-z0-9_-]{22}"))throw new IllegalArgumentException("Ungültige Anlage.");
        this.lan=lan;identity=lan.identity();String prefix="wattpit-view-"+id;
        repo=Repository.analysis(context,prefix);ledger=new RollerLedger(context,prefix+"-roller.db");
        planning=new SolarPlanning(context,repo,value->{},prefix+"-planning.db",ledger);
        // Open with this Context, before any request can switch the selected plant.
        repo.db.getWritableDatabase();planning.getWritableDatabase();ledger.getWritableDatabase();
    }
    private JSONObject get(String method,JSONArray args)throws Exception{
        if(identity!=lan.identity()||!lan.viewer())throw new IOException("Anlage wurde gewechselt.");
        JSONObject value=lan.queryInput(method,args);
        if(identity!=lan.identity()||!lan.viewer())throw new IOException("Anlage wurde gewechselt.");return value;
    }
    private void note(String value){long now=System.currentTimeMillis();if(progress!=null&&now-progressAt>=300){progressAt=now;progress.accept(value);}}
    private void reset(String next)throws Exception{
        HistoryExchange.reset(repo.db);
        for(String table:AnalysisExchange.ALL)AnalysisExchange.database(table,repo,planning,ledger,true).delete(table,"1=1",null);
        SharedPreferences.Editor edit=repo.prefs.edit();for(String key:repo.prefs.getAll().keySet())edit.remove(key);edit.putString("analysis.epoch",next).commit();epoch=next;
    }
    private void raw(long from,long to)throws Exception{
        for(long start=from;start<to;start+=90L*86400000){
            JSONObject manifest=get("analysisManifest",new JSONArray().put(start).put(Math.min(to,start+90L*86400000)));
            String next=manifest.getString("epoch");if(!next.equals(repo.prefs.getString("analysis.epoch","")))reset(next);epoch=next;rawRevision=manifest.getLong("revision");
            JSONArray blocks=manifest.getJSONArray("blocks");
            for(int i=0;i<blocks.length();i++){
                JSONArray b=blocks.getJSONArray(i);long block=b.getLong(0),revision=b.getLong(1),count=b.getLong(2);
                if(HistoryExchange.cachedVersion(repo.db,block)==revision)continue;
                note("Messdaten vom Hauptgerät · Abschnitt "+(i+1)+" von "+blocks.length());
                boolean installed=false;
                for(int attempt=0;attempt<3&&!installed;attempt++){
                    JSONArray rows=new JSONArray();long cursor=0;boolean changed=false;
                    if(count>100000)throw new IOException("Datenabschnitt ist zu groß.");
                    if(count>0)while(true){
                        JSONObject page=get("analysisPage",new JSONArray().put(block).put(revision).put(cursor));
                        if(page.optBoolean("changed")){changed=true;break;}
                        JSONArray values=page.getJSONArray("rows");for(int r=0;r<values.length();r++)rows.put(values.get(r));
                        long nextCursor=page.getLong("cursor");downloadedRows+=values.length();
                        if(page.getBoolean("complete"))break;
                        if(nextCursor<=cursor||rows.length()>count)throw new IOException("Unvollständige Messdatenübertragung.");cursor=nextCursor;note("Messdaten werden abgeglichen …");
                    }
                    if(!changed){HistoryExchange.replace(repo.db,block,revision,count,rows);downloadedBlocks++;installed=true;}
                    else{
                        JSONObject fresh=get("analysisManifest",new JSONArray().put(block*HistoryExchange.BLOCK).put((block+1)*HistoryExchange.BLOCK));
                        if(!epoch.equals(fresh.getString("epoch")))throw new IOException("Der Datenbestand der Zentrale wurde gewechselt. Bitte erneut laden.");
                        JSONArray changedBlocks=fresh.getJSONArray("blocks");if(changedBlocks.length()==0){revision=0;count=0;}else{revision=changedBlocks.getJSONArray(0).getLong(1);count=changedBlocks.getJSONArray(0).getLong(2);}
                    }
                }
                if(!installed)throw new IOException("Messdaten ändern sich gerade. Die letzten Werte bleiben erhalten.");
            }
        }
    }
    private void model(long from,long to)throws Exception{
        from=from/86400000L*86400000L;to=Math.min((to+86399999)/86400000L*86400000L,(System.currentTimeMillis()/86400000L+1)*86400000L);
        for(int retry=0;retry<3;retry++){
            JSONObject m=get("analysisMetadata",new JSONArray().put(from).put(to));
            if(m.optBoolean("changed"))continue;
            if(m.getInt("algorithm")!=SolarPlanning.ALGORITHM)throw new IOException("Bitte Hauptgerät und Anzeige auf dieselbe Watt’n’Pit-Version aktualisieren.");
            JSONObject versions=m.getJSONObject("tables");boolean changed=false;
            for(String table:AnalysisExchange.ALL){
                String version=versions.getString(table),scope=(table.equals("model_weather")||table.equals("device_settings")||table.equals("input_intervals"))?":"+from+":"+to:"";
                String key="analysis.table."+table+scope;boolean incremental=AnalysisExchange.incremental(table);
                if(version.equals(repo.prefs.getString(key,"")))continue;
                long cursor=incremental?repo.prefs.getLong("analysis.cursor."+table,0):0;JSONArray rows=new JSONArray();
                note("Berechnungsgrundlagen werden abgeglichen …");
                while(true){
                    JSONObject page=get("analysisModelPage",new JSONArray().put(table).put(from).put(to).put(version).put(cursor));
                    if(page.optBoolean("changed")){changed=true;break;}
                    JSONArray values=page.getJSONArray("rows");for(int i=0;i<values.length();i++)rows.put(values.get(i));
                    long next=page.getLong("cursor");if(page.getBoolean("complete")){cursor=next;break;}
                    if(next<=cursor||rows.length()>250000)throw new IOException("Unvollständige Berechnungsgrundlagen.");cursor=next;note("Berechnungsgrundlagen werden abgeglichen …");
                }
                if(changed)break;
                AnalysisExchange.install(table,repo,planning,ledger,from,to,rows);
                SharedPreferences.Editor edit=repo.prefs.edit().putString(key,version);if(incremental)edit.putLong("analysis.cursor."+table,cursor);edit.commit();
            }
            if(changed)continue;
            JSONObject settings=m.getJSONObject("settings");SharedPreferences.Editor edit=repo.prefs.edit();
            for(Iterator<String> it=settings.keys();it.hasNext();){String key=it.next();if(key.equals("capacity"))edit.putFloat(key,(float)settings.getDouble(key));else if(key.equals("sampleSeconds"))edit.putInt(key,settings.getInt(key));else edit.putString(key,settings.getString(key));}
            if(!repo.prefs.getBoolean("analysis.seeded",false)){
                JSONObject markers=m.getJSONObject("markers");for(Iterator<String> it=markers.keys();it.hasNext();){String key=it.next();Object value=markers.get(key);if(value instanceof Number){if(key.startsWith("planningEnergyVersion."))edit.putInt(key,((Number)value).intValue());else edit.putLong(key,((Number)value).longValue());}else edit.putString(key,value.toString());}
                edit.putBoolean("analysis.seeded",true);
            }
            edit.putString("analysisTimezone",m.getString("timezone")).commit();metadata=m;return;
        }
        throw new IOException("Die Berechnungsgrundlagen ändern sich gerade. Bitte erneut versuchen.");
    }
    private void requireProtocol()throws Exception{
        if(lan.analysisProtocol()!=1)throw new IOException("Bitte zuerst das Hauptgerät auf Watt’n’Pit 1.7.0 oder neuer aktualisieren.");
    }
    private static long support(long from){return Math.max(0,from/86400000L*86400000L-4L*86400000-EnergyIntegral.LOOKAROUND);}
    private void prepare(long from,long to)throws Exception{
        requireProtocol();long a=support(from),b=Math.min(System.currentTimeMillis()+86400000,to+EnergyIntegral.LOOKAROUND);
        // Establish the database identity before installing model inputs.
        JSONObject head=get("analysisManifest",new JSONArray().put(a).put(Math.min(b,a+86400000)));
        if(!head.getString("epoch").equals(repo.prefs.getString("analysis.epoch","")))reset(head.getString("epoch"));
        model(a,b);raw(a,b);
    }
    private JSONObject stamp(JSONObject result)throws Exception{
        JSONObject current=get("analysisInputState",new JSONArray());if(!current.getString("token").equals(metadata.optString("inputToken")))throw new IOException("Die Grundlagen wurden inzwischen aktualisiert. Die letzten Werte bleiben erhalten; neuer Abgleich folgt.");
        JSONObject state=new JSONObject().put("at",metadata.optLong("at")).put("revision",rawRevision).put("controlRevision",metadata.optLong("controlRevision")).put("algorithm",SolarPlanning.ALGORITHM).put("epoch",epoch).put("local",true);
        result.put("analysis",state);for(String key:new String[]{"solar","consumption","balance","mobile"})if(result.optJSONObject(key)!=null)result.getJSONObject(key).put("analysis",state);return result;
    }
    synchronized JSONObject query(String method,JSONArray args,Consumer<String> progress)throws Exception{
        this.progress=progress;downloadedRows=downloadedBlocks=0;
        try{
            long from=args.getLong(0),to=args.getLong(1),now=System.currentTimeMillis();
            if(from<0||to<=from||to-from>(method.equals("dailyEnergy")?32L:366L)*86400000||(!method.equals("dailyEnergy")&&to>now+86400000))throw new IOException("Ungültiger Zeitraum.");
            prepare(from,Math.max(from+1,Math.min(to,now)));note("Auswertung auf diesem Handy …");JSONObject result;
            switch(method){
                case "history":result=new JSONObject().put("points",planning.history(from,to,args.getString(2),args.getString(3)));break;
                case "dashboardHistory":result=planning.dashboardData(from,to,args.getString(2));break;
                case "energyHistoryWindow":result=planning.energyFlow(from,to,args.getLong(3),args.getLong(4),args.getString(2));break;
                case "energyHistory":result=planning.energyFlow(from,to,args.getLong(3),args.getString(2));break;
                case "dailyEnergy":result=planning.dailyEnergy(from,to,new JSONObject(repo.prefs.getString("weatherCache","{}")).put("analysisTimezone",repo.prefs.getString("analysisTimezone",TimeZone.getDefault().getID())));break;
                default:throw new IllegalArgumentException("Unbekannte Auswertung.");
            }return stamp(result);
        }finally{this.progress=null;}
    }
    synchronized JSONObject knowledge(Consumer<String> progress)throws Exception{
        this.progress=progress;try{
            requireProtocol();long now=System.currentTimeMillis(),since=now-7L*86400000,previous=repo.prefs.getLong("analysis.learningRevision",0);
            JSONObject changes=get("analysisChanges",new JSONArray().put(previous));
            if(!changes.getString("epoch").equals(repo.prefs.getString("analysis.epoch","")))reset(changes.getString("epoch"));
            model(support(since),now);since=planning.learningSince();
            long correction=changes.optLong("changedFrom");if(previous>0&&correction>0)since=Math.min(since,correction);
            long first=metadata.optLong("firstMeasurement");if(previous==0&&!planning.hasObservations()&&first>0)since=Math.min(since,first);
            since=Math.max(LearningWindow.since(now),since);
            // Completed local days and model priors need the preceding day across DST boundaries.
            since=Math.max(LearningWindow.since(now),since-86400000);
            JSONArray presence=null;if(now-support(since)>376L*86400000){for(long a=support(since);a<now;a+=360L*86400000){model(a,Math.min(now,a+360L*86400000));if(presence==null)presence=metadata.getJSONArray("presenceHistory");}}
            else model(support(since),now);
            raw(support(since),now);
            planning.analysisPresence(presence==null?metadata.getJSONArray("presenceHistory"):presence,metadata.getJSONObject("presence"),since);
            note("Verbrauch und Solarvorschau werden auf diesem Handy berechnet …");JSONObject result=planning.calculateKnowledge();
            repo.prefs.edit().putLong("analysis.learningRevision",changes.getLong("revision")).commit();return stamp(result);
        }finally{this.progress=null;}
    }
    synchronized JSONObject diagnostics()throws Exception{return new JSONObject().put("downloadedRows",downloadedRows).put("downloadedBlocks",downloadedBlocks).put("epoch",epoch).put("revision",rawRevision);}
    void close(){planning.shutdown();}
}
