package com.demo.ntc;

import android.app.Application;
import com.microblink.MicroblinkSDK;
import com.microblink.intent.IntentDataTransferMode;

public class microVerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MicroblinkSDK.setLicenseFile("MB_com.demo.ntc_BlinkID_Android_2019-12-06.mblic", this);
        MicroblinkSDK.setIntentDataTransferMode(IntentDataTransferMode.PERSISTED_OPTIMISED);
    }
}
