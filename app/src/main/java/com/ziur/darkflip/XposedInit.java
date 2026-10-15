package com.ziur.darkflip;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedInit implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam != null) {
            try {
                if (loadPackageParam.packageName == null) {
                    return;
                }
                if ("android".equals(loadPackageParam.packageName)) {
                    if ("android".equals(loadPackageParam.processName)) {
                        SystemEngine.init(loadPackageParam.classLoader);
                    }
                } else if (loadPackageParam.isFirstApplication) {
                    if (C.MODULE_PKG.equals(loadPackageParam.packageName)) {
                        XposedHelpers.setStaticBooleanField(XposedHelpers.findClass("com.ziur.darkflip.SettingsActivity", loadPackageParam.classLoader), "moduleActive", true);
                    } else {
                        AppEngine.init(loadPackageParam);
                    }
                }
            } catch (Throwable th) {
                L.e("handleLoadPackage", th);
            }
        }
    }
}
