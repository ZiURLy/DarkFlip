package com.ziur.darkflip;

import android.os.SystemClock;
import de.robv.android.xposed.XSharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

final class Prefs {
    static volatile boolean autoSkip = true;
    static volatile boolean debug = true;
    static volatile boolean deepScan = false;
    static volatile boolean enabled = true;
    static volatile boolean exactColors = true;
    static volatile boolean fixBars = true;
    static volatile boolean flutterTexture = true;
    static volatile boolean followSystem = true;
    static volatile boolean forceLight = true;
    static volatile boolean keepImages = true;
    private static long lastCheck = 0;
    static volatile boolean noShadow = true;
    static volatile int style = 0;
    static volatile boolean systemNight = true;
    static volatile boolean userEnabled = true;
    private static XSharedPreferences xsp;
    static volatile String[] skipKeys = splitKeys(C.DEFAULT_SKIP_KEYS);
    static volatile String[] sysPkgs = new String[0];

    private Prefs() {
    }

    static synchronized void load() {
        try {
            XSharedPreferences xSharedPreferences = xsp;
            if (xSharedPreferences == null) {
                xsp = new XSharedPreferences(C.MODULE_PKG, C.PREFS);
            } else {
                xSharedPreferences.reload();
            }
            read();
        } catch (Throwable th) {
            L.e("prefs.load", th);
        }
    }

    static synchronized boolean reloadIfChanged() {
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - lastCheck < 1500) {
            return false;
        }
        lastCheck = jUptimeMillis;
        try {
            XSharedPreferences xSharedPreferences = xsp;
            if (xSharedPreferences == null) {
                load();
                return true;
            }
            if (!xSharedPreferences.hasFileChanged()) {
                return false;
            }
            xsp.reload();
            read();
            return true;
        } catch (Throwable th) {
            L.eLimited("prefs.reload", th);
            return false;
        }
    }

    private static void read() {
        XSharedPreferences xSharedPreferences = xsp;
        userEnabled = xSharedPreferences.getBoolean(C.K_ENABLED, true);
        followSystem = xSharedPreferences.getBoolean(C.K_FOLLOW, true);
        recompute();
        style = xSharedPreferences.getInt(C.K_STYLE, 0);
        exactColors = xSharedPreferences.getBoolean(C.K_EXACT, true);
        keepImages = xSharedPreferences.getBoolean(C.K_KEEP_IMAGES, true);
        autoSkip = xSharedPreferences.getBoolean(C.K_AUTO_SKIP, true);
        fixBars = xSharedPreferences.getBoolean(C.K_FIX_BARS, true);
        forceLight = xSharedPreferences.getBoolean(C.K_FORCE_LIGHT, true);
        flutterTexture = xSharedPreferences.getBoolean(C.K_FLUTTER_TEXTURE, true);
        noShadow = xSharedPreferences.getBoolean(C.K_NO_SHADOW, true);
        debug = xSharedPreferences.getBoolean(C.K_DEBUG, true);
        deepScan = xSharedPreferences.getBoolean(C.K_DEEP_SCAN, false);
        skipKeys = splitKeys(xSharedPreferences.getString(C.K_SKIP_KEYS, C.DEFAULT_SKIP_KEYS));
        sysPkgs = splitKeys(xSharedPreferences.getString(C.K_SYS_PKGS, ""));
    }

    static void recompute() {
        enabled = userEnabled && (!followSystem || systemNight);
    }

    static String fileStatus() {
        try {
            XSharedPreferences xSharedPreferences = xsp;
            if (xSharedPreferences == null) {
                return "not loaded";
            }
            File file = xSharedPreferences.getFile();
            if (file == null) {
                return "no file";
            }
            return file.canRead() ? "readable" : "NOT readable (defaults in use)";
        } catch (Throwable th) {
            return "unknown (" + th + ")";
        }
    }

    static boolean isSystemModePackage(String str) {
        if (str == null) {
            return false;
        }
        String[] strArr = sysPkgs;
        String lowerCase = str.toLowerCase(Locale.ROOT);
        for (String str2 : strArr) {
            if (str2.equals(lowerCase)) {
                return true;
            }
        }
        return false;
    }

    static String[] splitKeys(String str) {
        if (str == null) {
            return new String[0];
        }
        ArrayList arrayList = new ArrayList();
        for (String str2 : str.split("[,，;；\\s]+")) {
            String lowerCase = str2.trim().toLowerCase(Locale.ROOT);
            if (!lowerCase.isEmpty()) {
                arrayList.add(lowerCase);
            }
        }
        return (String[]) arrayList.toArray(new String[0]);
    }
}
