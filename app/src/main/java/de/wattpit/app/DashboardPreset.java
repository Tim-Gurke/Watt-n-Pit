package de.wattpit.app;

import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** The explicitly requested layout rollout runs once per installation, on every role. */
final class DashboardPreset {
    static final String MARKER="dashboard-preset-163";
    static String read(Context context)throws IOException{
        try(InputStream in=context.getAssets().open("dashboard-preset.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
            while((n=in.read(block))!=-1){if(bytes.size()+n>128000)throw new IOException();bytes.write(block,0,n);}
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    static void install(SharedPreferences prefs,String json)throws Exception{
        if(prefs.getBoolean(MARKER,false))return;
        JSONObject board=new JSONObject(json);JSONArray tiles=board.getJSONArray("tiles");
        if(board.getInt("version")!=1||tiles.length()>64)throw new IllegalArgumentException("Ungültige Layoutvorlage.");
        for(int i=0;i<tiles.length();i++){JSONObject t=tiles.getJSONObject(i);t.put("fontSize",Math.max(18,Math.min(20,t.optDouble("fontSize",20))));}
        if(!prefs.edit().putString("dashboard-v1",board.toString()).putBoolean(MARKER,true).commit())throw new IOException("Die Übersicht konnte nicht gespeichert werden.");
    }
    private DashboardPreset(){}
}
