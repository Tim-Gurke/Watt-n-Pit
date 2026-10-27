package de.wattpit.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.client.android.Intents;
import de.wattpit.app.core.PairingQr;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import org.json.JSONObject;

/** Local QR display and camera scanner. Frames are processed on the phone, without a server. */
final class LanQr {
    private LanQr(){}
    static String image(String code)throws Exception{
        Bitmap bitmap=Bitmap.createBitmap(PairingQr.pixels(code),PairingQr.SIZE,PairingQr.SIZE,Bitmap.Config.ARGB_8888);
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes))throw new IllegalStateException();
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }finally{bitmap.recycle();}
    }
    @SuppressWarnings("deprecation")
    static void scan(Activity activity){new IntentIntegrator(activity).setCaptureActivity(LanScanActivity.class).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("QR-Code vollständig im Rahmen halten. Etwas Abstand hilft beim Scharfstellen.").setBeepEnabled(false).setBarcodeImageEnabled(false).setOrientationLocked(false).setTimeout(90000).initiateScan();}
    @SuppressWarnings("deprecation")
    static JSONObject result(int requestCode,int resultCode,Intent data)throws Exception{
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(result==null)return null;
        String code=result.getContents();if(code==null){
            if(data!=null&&data.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION,false))return new JSONObject().put("error","Kamerazugriff fehlt. Bitte in den Android-App-Einstellungen die Kamera für Watt’n’Pit erlauben oder den Textcode einfügen.");
            if(data!=null&&data.getBooleanExtra(Intents.Scan.TIMEOUT,false))return new JSONObject().put("error","Kein QR-Code erkannt. Bitte den ganzen Code im Rahmen halten und etwas mehr Abstand versuchen. Du kannst auch den Textcode einfügen.");
            return new JSONObject().put("cancelled",true);
        }
        if(code.length()>4096)return new JSONObject().put("error","Dieser QR-Code enthält keinen gültigen Watt’n’Pit-Verbindungscode.");
        return new JSONObject().put("code",code);
    }
}
