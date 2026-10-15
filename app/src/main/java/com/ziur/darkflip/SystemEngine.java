package com.ziur.darkflip;

import android.view.SurfaceControl;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Arrays;

final class SystemEngine {
    private SystemEngine() {
    }

    static void init(ClassLoader classLoader) {
        L.setProcess("system");
        Prefs.load();
        L.i("v" + C.VERSION + " system-level engine, packages=" + Arrays.toString(Prefs.sysPkgs) + ", settings file " + Prefs.fileStatus());
        try {
            final Class clsFindClass = XposedHelpers.findClass("com.android.server.wm.WindowState", classLoader);
            L.i("system: setSurfaceControl hooks=" + XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.server.wm.WindowContainer", classLoader), "setSurfaceControl", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        if (methodHookParam.args.length != 0 && (methodHookParam.args[0] instanceof SurfaceControl) && clsFindClass.isInstance(methodHookParam.thisObject)) {
                            SystemEngine.onWindowSurface(methodHookParam.thisObject, (SurfaceControl) methodHookParam.args[0]);
                        }
                    } catch (Throwable th) {
                        L.eLimited("sys.surface", th);
                    }
                }
            }).size());
        } catch (Throwable th) {
            L.e("system: window surface hook", th);
        }
        try {
            Class clsFindClass2 = XposedHelpers.findClass("com.android.server.wm.WindowManagerService", classLoader);
            XC_MethodHook xC_MethodHook = new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        Prefs.reloadIfChanged();
                        if (Prefs.sysPkgs.length == 0) {
                            return;
                        }
                        for (Object obj : methodHookParam.args) {
                            if (obj instanceof WindowManager.LayoutParams) {
                                WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) obj;
                                if (Prefs.fixBars && SystemEngine.shouldInvert(layoutParams)) {
                                    Hidden.setBarAppearance(layoutParams, Hidden.barAppearance(layoutParams) ^ Bars.FLIP);
                                    return;
                                }
                                return;
                            }
                        }
                    } catch (Throwable th2) {
                        L.eLimited("sys.bars", th2);
                    }
                }
            };
            L.i("system: addWindow/relayoutWindow hooks=" + (XposedBridge.hookAllMethods(clsFindClass2, "addWindow", xC_MethodHook).size() + XposedBridge.hookAllMethods(clsFindClass2, "relayoutWindow", xC_MethodHook).size()));
        } catch (Throwable th2) {
            L.e("system: bar hook", th2);
        }
    }

    private static boolean shouldInvert(WindowManager.LayoutParams layoutParams) {
        if (Prefs.userEnabled && layoutParams != null && Prefs.isSystemModePackage(layoutParams.packageName)) {
            return !Util.containsAny(Util.lower(String.valueOf(layoutParams.getTitle())), Prefs.skipKeys);
        }
        return false;
    }

    private static void onWindowSurface(Object obj, SurfaceControl surfaceControl) {
        if (surfaceControl.isValid()) {
            Object objectField = XposedHelpers.getObjectField(obj, "mAttrs");
            if (objectField instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) objectField;
                if (shouldInvert(layoutParams)) {
                    if (Boolean.TRUE.equals(XposedHelpers.callMethod(obj, "isChildWindow", new Object[0]))) {
                        return;
                    }
                    SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                    try {
                        Hidden.setColorTransform(transaction, surfaceControl, Fx.LIN_M, Fx.LIN_T);
                        transaction.apply();
                        transaction.close();
                        if (Prefs.debug && L.limited("sys.win", 40)) {
                            L.i("system: inverted window " + ((Object) layoutParams.getTitle()) + " type=" + layoutParams.type);
                        }
                    } catch (Throwable th) {
                        transaction.close();
                        throw th;
                    }
                }
            }
        }
    }
}
