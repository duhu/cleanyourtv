package com.gathin.tvhome;

import android.app.admin.DeviceAdminReceiver;

/**
 * Device admin with the force-lock policy only, so the launcher can put the TV to sleep
 * (DevicePolicyManager.lockNow) inside its sleep window (ScreenSchedule). Activated over adb by
 * build-and-install.sh:
 *   dpm set-active-admin --user 0 com.gathin.tvhome/.ScreenAdmin
 * and deactivated by ScreenAdminOff before an uninstall.
 */
public class ScreenAdmin extends DeviceAdminReceiver {
}
