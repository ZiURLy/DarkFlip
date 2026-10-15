package com.ziur.darkflip;

import android.util.Log;
import de.robv.android.xposed.XposedBridge;
import java.util.HashMap;

final class L {
    private static final HashMap<String, int[]> COUNTS = new HashMap<>();
    private static String prefix = "[DarkFlip] ";

    private L() {
    }

    static void setProcess(String str) {
        prefix = "[DarkFlip][" + str + "] ";
    }

    static void i(String str) {
        while (true) {
            int i = 1500;
            if (str.length() > 1500) {
                int iLastIndexOf = str.lastIndexOf(10, 1500);
                if (iLastIndexOf >= 200) {
                    i = iLastIndexOf;
                }
                one(str.substring(0, i));
                str = "  ..." + str.substring(i);
            } else {
                one(str);
                return;
            }
        }
    }

    private static void one(String str) {
        try {
            XposedBridge.log(prefix + str);
        } catch (Throwable unused) {
            Log.i("DarkFlip", str);
        }
    }

    static void d(String str) {
        if (Prefs.debug) {
            i(str);
        }
    }

    static void e(String str, Throwable th) {
        i("ERROR " + str + ": " + th);
        if (Prefs.debug) {
            try {
                XposedBridge.log(th);
            } catch (Throwable unused) {
            }
        }
    }

    static boolean limited(String str, int i) {
        HashMap<String, int[]> map = COUNTS;
        synchronized (map) {
            int[] iArr = map.get(str);
            if (iArr == null) {
                iArr = new int[1];
                map.put(str, iArr);
            }
            int i2 = iArr[0];
            if (i2 >= i) {
                return false;
            }
            iArr[0] = i2 + 1;
            return true;
        }
    }

    static void eLimited(String str, Throwable th) {
        if (limited("e:" + str, 3)) {
            e(str, th);
        }
    }

    static String stack(int i) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        for (StackTraceElement stackTraceElement : stackTrace) {
            String className = stackTraceElement.getClassName();
            if (!className.startsWith("com.ziur.darkflip.") && !className.startsWith("de.robv.") && !className.startsWith("org.lsposed.") && !className.startsWith("LSPHooker") && !className.startsWith("java.lang.reflect.")) {
                sb.append("\n      at ");
                sb.append(className);
                sb.append('.');
                sb.append(stackTraceElement.getMethodName());
                i2++;
                if (i2 >= i) {
                    break;
                }
            }
        }
        return sb.toString();
    }
}
