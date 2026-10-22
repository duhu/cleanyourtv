package com.gathin.tvhome;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Deactivates the ScreenAdmin device admin; while it is active, pm uninstall refuses to remove the
 * launcher. Over adb:
 *   am broadcast -n com.gathin.tvhome/.ScreenAdminOff -a com.gathin.tvhome.SCREEN_ADMIN_OFF
 */
public class ScreenAdminOff extends BroadcastReceiver {
    static final String ACTION = "com.gathin.tvhome.SCREEN_ADMIN_OFF";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(ctx, ScreenAdmin.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin);
            Log.i(StandbyFeature.TAG, "ScreenAdmin deactivated");
        } else {
            Log.i(StandbyFeature.TAG, "ScreenAdmin was not active");
        }
    }
}
