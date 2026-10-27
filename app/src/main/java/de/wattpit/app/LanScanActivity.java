package de.wattpit.app;

import android.os.Bundle;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;

/** The dense pairing QR needs continuous focus and room for all four edges. */
public final class LanScanActivity extends CaptureActivity {
    private DecoratedBarcodeView view;
    @Override protected DecoratedBarcodeView initializeContent(){
        view=new DecoratedBarcodeView(this);view.getBarcodeView().setMarginFraction(.05);setContentView(view);return view;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        view.getCameraSettings().setContinuousFocusEnabled(true);
        view.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE),Collections.singletonMap(DecodeHintType.TRY_HARDER,Boolean.TRUE),"UTF-8",0));
    }
}
