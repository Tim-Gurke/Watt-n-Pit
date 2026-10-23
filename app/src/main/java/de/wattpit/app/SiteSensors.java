package de.wattpit.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.view.Surface;
import org.json.*;
import java.util.function.BiConsumer;

/** Sensors run only during an explicitly opened assistant. GPS never starts with Bluetooth. */
final class SiteSensors implements SensorEventListener,LocationListener {
    private final Activity activity;
    private final Repository repository;
    private final BiConsumer<String,JSONObject> callback;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SensorManager sensors;
    private final LocationManager locations;
    private boolean compass,locating;
    private int accuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;
    private float field=Float.NaN;
    private float[] gravity,magnetic;
    private long lastEmission;
    private Location best;
    private JSONObject compassPlace=new JSONObject();
    private final Runnable locationTimeout=()->finishLocation(best);
    SiteSensors(Activity activity,Repository repository,BiConsumer<String,JSONObject> callback){
        this.activity=activity;this.repository=repository;this.callback=callback;
        sensors=activity.getSystemService(SensorManager.class);locations=activity.getSystemService(LocationManager.class);
    }
    private void emit(String method,JSONObject value){callback.accept(method,value);}
    private void message(String method,String text){try{emit(method,new JSONObject().put("error",text));}catch(JSONException ignored){}}
    void startCompass(JSONObject place){
        stopCompass();
        try{compassPlace=WeatherClient.checkedPlace(place);}catch(Exception e){compassPlace=new JSONObject();}
        if(sensors==null||sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)==null||sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)==null){message("onSolarCompass","Kein Kompasssensor verfügbar. Bitte die Richtung manuell wählen.");return;}
        compass=true;accuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;field=Float.NaN;gravity=null;magnetic=null;
        boolean ok=sensors.registerListener(this,sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_UI)
                &&sensors.registerListener(this,sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_UI);
        if(!ok){stopCompass();message("onSolarCompass","Kompass nicht verfügbar. Bitte die Richtung manuell wählen.");}
    }
    void stopCompass(){compass=false;if(sensors!=null)sensors.unregisterListener(this);}
    @Override public void onSensorChanged(SensorEvent event){
        if(!compass)return;
        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)gravity=event.values.clone();
        if(event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD){magnetic=event.values.clone();field=(float)Math.sqrt(magnetic[0]*magnetic[0]+magnetic[1]*magnetic[1]+magnetic[2]*magnetic[2]);accuracy=event.accuracy;}
        long now=SystemClock.elapsedRealtime();if(gravity==null||magnetic==null||now-lastEmission<180)return;
        float[] matrix=new float[9],mapped=new float[9],angles=new float[3];if(!SensorManager.getRotationMatrix(matrix,null,gravity,magnetic))return;
        int rotation=activity.getWindowManager().getDefaultDisplay().getRotation(),x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        if(rotation==Surface.ROTATION_90){x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;}
        else if(rotation==Surface.ROTATION_180){x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;}
        else if(rotation==Surface.ROTATION_270){x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;}
        if(!SensorManager.remapCoordinateSystem(matrix,x,y,mapped))return;SensorManager.getOrientation(mapped,angles);
        double heading=Math.toDegrees(angles[0]),pitch=Math.toDegrees(angles[1]),roll=Math.toDegrees(angles[2]);boolean corrected=false;
        try{JSONObject p=compassPlace;if(p.has("latitude")&&p.has("longitude")){GeomagneticField model=new GeomagneticField((float)p.getDouble("latitude"),(float)p.getDouble("longitude"),0,System.currentTimeMillis());heading+=model.getDeclination();corrected=true;}}catch(Exception ignored){}
        heading=(heading%360+360)%360;lastEmission=now;
        try{emit("onSolarCompass",new JSONObject().put("heading",heading).put("pitch",pitch).put("roll",roll).put("accuracy",accuracy).put("field",Float.isFinite(field)?field:JSONObject.NULL).put("trueNorth",corrected).put("usable",accuracy>=SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM&&field>=20&&field<=70&&Math.abs(pitch)<15&&Math.abs(roll)<15));}catch(JSONException ignored){}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){if(sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)this.accuracy=accuracy;}
    @SuppressLint("MissingPermission") void locate(){
        cancelLocation();best=null;
        if(locations==null){message("onSolarLocation","Standort nicht verfügbar. Bitte einen Ort suchen.");return;}
        boolean fine=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,coarse=activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(!fine&&!coarse){message("onSolarLocation","Standortberechtigung fehlt. Die manuelle Ortssuche bleibt verfügbar.");return;}
        locating=true;boolean requested=false;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{
            if(provider.equals(LocationManager.GPS_PROVIDER)&&!fine||!locations.isProviderEnabled(provider))continue;
            Location last=locations.getLastKnownLocation(provider);if(fresh(last))best=choose(best,last);
            locations.requestLocationUpdates(provider,1000,0,this,Looper.getMainLooper());requested=true;
        }catch(SecurityException|IllegalArgumentException ignored){}
        if(best!=null&&best.getAccuracy()<=100){finishLocation(best);return;}
        if(!requested){locating=false;message("onSolarLocation","Bitte den Standortdienst einschalten oder einen Ort manuell suchen.");return;}
        handler.postDelayed(locationTimeout,20000);
    }
    private static boolean fresh(Location location){return location!=null&&location.hasAccuracy()&&location.getAccuracy()>0&&location.getAccuracy()<=2000&&SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos()>=0&&SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos()<120000000000L;}
    private static Location choose(Location a,Location b){return a==null||b.getAccuracy()<a.getAccuracy()?b:a;}
    private void finishLocation(Location location){
        if(!locating)return;cancelLocation();
        if(!fresh(location)){message("onSolarLocation","Noch kein ausreichend aktueller Standort. Bitte draußen erneut versuchen oder den Ort suchen.");return;}
        try{emit("onSolarLocation",new JSONObject().put("latitude",location.getLatitude()).put("longitude",location.getLongitude()).put("accuracy",location.getAccuracy()).put("time",location.getTime()));}catch(JSONException ignored){}
    }
    void cancelLocation(){locating=false;handler.removeCallbacks(locationTimeout);if(locations!=null)try{locations.removeUpdates(this);}catch(SecurityException ignored){}}
    @Override public void onLocationChanged(Location location){if(locating&&fresh(location)){best=choose(best,location);if(best.getAccuracy()<=100)finishLocation(best);}}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    void stop(){stopCompass();cancelLocation();}
}
