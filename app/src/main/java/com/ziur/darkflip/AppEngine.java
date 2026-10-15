package com.ziur.darkflip;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import com.ziur.darkflip.Roots;

final class AppEngine {
    private static final int NIGHT_MASK = 48;
    private static volatile boolean nightFromManager;
    private static volatile boolean uiModeSuspended;
    private static final WeakHashMap<Object, Boolean> FLIPPED = new WeakHashMap<>();
    private static final HashMap<String, long[]> CREATES = new HashMap<>();

    private AppEngine() {
    }

    static void init(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        String str = loadPackageParam.processName == null ? loadPackageParam.packageName : loadPackageParam.processName;
        L.setProcess(str);
        Prefs.load();
        try {
            noteSystemNight(Resources.getSystem().getConfiguration().uiMode);
        } catch (Throwable th) {
            L.e("system night mode", th);
        }
        Fx.rebuild();
        if (Prefs.isSystemModePackage(loadPackageParam.packageName)) {
            L.i("v" + C.VERSION + ": this app is on the system-level list, the in-app engine stays off");
            return;
        }
        StringBuilder sb = new StringBuilder("v" + C.VERSION + " in-app engine, Android ");
        sb.append(Build.VERSION.RELEASE);
        sb.append(" (sdk ");
        sb.append(Build.VERSION.SDK_INT);
        sb.append("), ");
        sb.append(Build.MANUFACTURER);
        sb.append(" ");
        sb.append(Build.MODEL);
        sb.append(", switch=");
        sb.append(Prefs.userEnabled);
        sb.append(" followSystem=");
        sb.append(Prefs.followSystem);
        sb.append(" systemNight=");
        sb.append(Prefs.systemNight);
        sb.append(" -> active=");
        sb.append(Prefs.enabled);
        sb.append(" style=");
        sb.append(Prefs.style);
        sb.append(" filter=");
        sb.append(Fx.exact ? "shader" : "matrix");
        sb.append(" keepImages=");
        sb.append(Prefs.keepImages);
        sb.append(" autoSkip=");
        sb.append(Prefs.autoSkip);
        sb.append(" flutterTexture=");
        sb.append(Prefs.flutterTexture);
        sb.append(" forceLight=");
        sb.append(Prefs.forceLight);
        sb.append(", settings file ");
        sb.append(Prefs.fileStatus());
        L.i(sb.toString());
        step("windows", new Runnable() {
            @Override
            public void run() {
                AppEngine.hookWindows();
            }
        });
        step("frames", new Runnable() {
            @Override
            public void run() {
                AppEngine.hookFrames();
            }
        });
        step("bars", new Runnable() {
            @Override
            public void run() {
                Bars.install();
            }
        });
        step("media", new Runnable() {
            @Override
            public void run() {
                AppEngine.hookMedia();
            }
        });
        step("images", new Runnable() {
            @Override
            public void run() {
                Images.install();
                VideoProbe.install();
                VideoCounter.install();
            }
        });
        step("uiMode", new Runnable() {
            @Override
            public void run() {
                AppEngine.hookUiMode();
            }
        });
        step("forceDark", new Runnable() {
            @Override
            public void run() {
                AppEngine.hookForceDark();
            }
        });
        step("activities", new Runnable() {
            @Override
            public void run() {
                AppEngine.hookActivities();
            }
        });
        step("probes", new Runnable() {
            @Override
            public void run() {
                Diag.installProbes();
            }
        });
        FlutterHooks.tryInstall(loadPackageParam.classLoader);
        Images.tryPowerImage(loadPackageParam.classLoader);
        if (loadPackageParam.packageName.equals(str)) {
            logVendorDarkMode();
        }
    }

    private static void logVendorDarkMode() {
        if (Prefs.debug) {
            try {
                Class clsFindClassIfExists = XposedHelpers.findClassIfExists("com.oplus.darkmode.OplusDarkModeManager", (ClassLoader) null);
                if (clsFindClassIfExists == null) {
                    L.i("vendor dark mode manager: none found");
                    return;
                }
                StringBuilder sb = new StringBuilder("vendor dark mode manager: ");
                sb.append(clsFindClassIfExists.getName());
                sb.append(" {");
                Method[] declaredMethods = clsFindClassIfExists.getDeclaredMethods();
                int length = declaredMethods.length;
                int i = 0;
                int i2 = 0;
                while (true) {
                    if (i >= length) {
                        break;
                    }
                    Method method = declaredMethods[i];
                    int i3 = i2 + 1;
                    if (i2 >= 80) {
                        sb.append(" ...");
                        break;
                    }
                    sb.append(' ');
                    sb.append(method.getName());
                    sb.append('/');
                    sb.append(method.getParameterTypes().length);
                    i++;
                    i2 = i3;
                }
                sb.append(" }");
                L.i(sb.toString());
            } catch (Throwable th) {
                L.i("vendor dark mode manager: " + th);
            }
        }
    }

    private static void step(String str, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable th) {
            L.e("install " + str, th);
        }
    }

    private static void hookWindows() {
        if (XposedBridge.hookAllMethods(Hidden.VIEW_ROOT_IMPL, "setView", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (methodHookParam.args.length >= 2 && (methodHookParam.args[0] instanceof View)) {
                        Roots.track((View) methodHookParam.args[0], methodHookParam.args[1] instanceof WindowManager.LayoutParams ? (WindowManager.LayoutParams) methodHookParam.args[1] : null);
                    }
                } catch (Throwable th) {
                    L.eLimited("setView", th);
                }
            }
        }).isEmpty()) {
            L.i("WARNING: ViewRootImpl.setView not found, nothing will be inverted");
        }
        XposedHelpers.findAndHookMethod(View.class, "setLayerType", new Object[]{Integer.TYPE, Paint.class, new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Roots.State state = Roots.get((View) methodHookParam.thisObject);
                    if (state != null && !state.selfCall) {
                        state.appLayerType = ((Integer) methodHookParam.args[0]).intValue();
                        state.appLayerPaint = (Paint) methodHookParam.args[1];
                        if (state.inverted) {
                            if (state.appLayerType != 1) {
                                methodHookParam.args[0] = 2;
                            }
                            methodHookParam.args[1] = Fx.rootPaint;
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("setLayerType", th);
                }
            }
        }});
        XposedHelpers.findAndHookMethod(View.class, "setLayerPaint", new Object[]{Paint.class, new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Roots.State state = Roots.get((View) methodHookParam.thisObject);
                    if (state != null && !state.selfCall) {
                        Object obj = methodHookParam.args[0];
                        if (obj != Fx.rootPaint) {
                            state.appLayerPaint = (Paint) obj;
                        }
                        if (state.inverted) {
                            methodHookParam.args[0] = Fx.rootPaint;
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("setLayerPaint", th);
                }
            }
        }});
    }

    private static void hookFrames() {
        if (XposedBridge.hookAllMethods(XposedHelpers.findClass("android.view.ThreadedRenderer", (ClassLoader) null), "draw", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (methodHookParam.args.length <= 0 || !(methodHookParam.args[0] instanceof View)) {
                        return;
                    }
                    Roots.drawBegin((View) methodHookParam.args[0], methodHookParam.thisObject);
                } catch (Throwable th) {
                    L.eLimited("draw.before", th);
                }
            }

            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Roots.drawEnd();
            }
        }).isEmpty()) {
            L.i("WARNING: ThreadedRenderer.draw not found; photos will not be protected");
        }
    }

    private static void hookMedia() {
        step("media: SurfaceView attach", new Runnable() {
            @Override
            public void run() {
                XposedHelpers.findAndHookMethod(SurfaceView.class, "onAttachedToWindow", new Object[]{new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        try {
                            Media.onAttach((View) methodHookParam.thisObject);
                        } catch (Throwable th) {
                            L.eLimited("sv.attach", th);
                        }
                    }
                }});
            }
        });
        step("media: SurfaceView surface", new Runnable() {
            @Override
            public void run() {
                XposedHelpers.findAndHookMethod(SurfaceView.class, "updateSurface", new Object[]{new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        try {
                            Media.onSurfaceUpdated((SurfaceView) methodHookParam.thisObject);
                        } catch (Throwable th) {
                            L.eLimited("sv.update", th);
                        }
                    }
                }});
            }
        });
        step("media: TextureView attach", new Runnable() {
            @Override
            public void run() {
                XposedHelpers.findAndHookMethod(TextureView.class, "onAttachedToWindow", new Object[]{new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        try {
                            Media.onAttach((View) methodHookParam.thisObject);
                        } catch (Throwable th) {
                            L.eLimited("tv.attach", th);
                        }
                    }
                }});
            }
        });
    }

    private static void hookUiMode() {
        try {
            Class clsFindClass = XposedHelpers.findClass("android.app.ResourcesManager", (ClassLoader) null);
            XC_MethodHook xC_MethodHook = new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        if (methodHookParam.args.length <= 0 || !(methodHookParam.args[0] instanceof Configuration)) {
                            return;
                        }
                        AppEngine.onSystemConfiguration((Configuration) methodHookParam.args[0]);
                    } catch (Throwable th) {
                        L.eLimited("night", th);
                    }
                }
            };
            nightFromManager = XposedBridge.hookAllMethods(clsFindClass, "applyConfigurationToResources", xC_MethodHook).size() + XposedBridge.hookAllMethods(clsFindClass, "applyConfigurationToResourcesLocked", xC_MethodHook).size() > 0;
        } catch (Throwable th) {
            L.e("night hook", th);
        }
        if (!nightFromManager) {
            L.i("note: reading the system's night mode from the system resources instead");
        }
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.content.res.ResourcesImpl", (ClassLoader) null), "updateConfiguration", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int i;
                try {
                    if (methodHookParam.args.length != 0 && (methodHookParam.args[0] instanceof Configuration)) {
                        Configuration configuration = (Configuration) methodHookParam.args[0];
                        if (!AppEngine.nightFromManager && methodHookParam.thisObject == Hidden.resourcesImpl(Resources.getSystem())) {
                            AppEngine.onSystemConfiguration(configuration);
                        }
                        if (!Prefs.forceLight || !Prefs.enabled || AppEngine.uiModeSuspended || (i = configuration.uiMode & 48) == 0) {
                            return;
                        }
                        boolean z = i == 32;
                        synchronized (AppEngine.FLIPPED) {
                            if (z) {
                                AppEngine.FLIPPED.put(methodHookParam.thisObject, Boolean.TRUE);
                            } else {
                                AppEngine.FLIPPED.remove(methodHookParam.thisObject);
                            }
                        }
                        if (z) {
                            Configuration configuration2 = new Configuration(configuration);
                            configuration2.uiMode = (configuration.uiMode & (-49)) | 16;
                            methodHookParam.args[0] = configuration2;
                        }
                    }
                } catch (Throwable th2) {
                    L.eLimited("uiMode", th2);
                }
            }
        });
    }

    private static void onSystemConfiguration(Configuration configuration) {
        if (noteSystemNight(configuration.uiMode)) {
            StringBuilder sb = new StringBuilder("system night mode is now ");
            sb.append(Prefs.systemNight ? "on" : "off");
            sb.append(" -> active=");
            sb.append(Prefs.enabled);
            L.i(sb.toString());
            Roots.reapplyAll();
        }
    }

    private static boolean noteSystemNight(int i) {
        boolean z;
        int i2 = i & 48;
        if (i2 == 32) {
            z = true;
        } else {
            if (i2 != 16) {
                return false;
            }
            z = false;
        }
        if (Prefs.systemNight == z) {
            return false;
        }
        boolean z2 = Prefs.enabled;
        Prefs.systemNight = z;
        Prefs.recompute();
        return z2 != Prefs.enabled;
    }

    private static void noteActivityCreate(String str) {
        ArrayList arrayList;
        if (!uiModeSuspended && Prefs.forceLight && Prefs.enabled) {
            long jUptimeMillis = SystemClock.uptimeMillis();
            HashMap<String, long[]> map = CREATES;
            synchronized (map) {
                long[] jArr = map.get(str);
                if (jArr == null || jUptimeMillis - jArr[0] > 4000) {
                    if (map.size() > 200) {
                        map.clear();
                    }
                    jArr = new long[]{jUptimeMillis, 0};
                    map.put(str, jArr);
                }
                long j = jArr[1] + 1;
                jArr[1] = j;
                if (j < 6) {
                    return;
                }
                uiModeSuspended = true;
                L.i("WARNING: " + str + " was created 6 times within 4 s. Assuming that hiding night mode confuses this app; it sees the real night mode again until it is restarted (the switch for this is the one about ignoring the system's dark mode)");
                WeakHashMap<Object, Boolean> weakHashMap = FLIPPED;
                synchronized (weakHashMap) {
                    arrayList = new ArrayList(weakHashMap.keySet());
                    weakHashMap.clear();
                }
                for (Object obj : arrayList) {
                    if (obj != null) {
                        try {
                            Object objectField = XposedHelpers.getObjectField(obj, "mConfiguration");
                            if (objectField instanceof Configuration) {
                                Configuration configuration = new Configuration((Configuration) objectField);
                                configuration.uiMode = (configuration.uiMode & (-49)) | 32;
                                Method[] declaredMethods = obj.getClass().getDeclaredMethods();
                                int length = declaredMethods.length;
                                int i = 0;
                                while (true) {
                                    if (i < length) {
                                        Method method = declaredMethods[i];
                                        Class<?>[] parameterTypes = method.getParameterTypes();
                                        if ("updateConfiguration".equals(method.getName()) && parameterTypes.length == 3 && parameterTypes[0] == Configuration.class) {
                                            method.setAccessible(true);
                                            method.invoke(obj, configuration, null, null);
                                            break;
                                        }
                                        i++;
                                    }
                                }
                            }
                        } catch (Throwable th) {
                            L.eLimited("uiMode.restore", th);
                        }
                    }
                }
            }
        }
    }

    private static void hookForceDark() {
        L.d("force dark: hooks=" + XposedBridge.hookAllMethods(XposedHelpers.findClass("android.graphics.HardwareRenderer", (ClassLoader) null), "setForceDark", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (Prefs.forceLight && Prefs.enabled && methodHookParam.args.length == 1) {
                        Object obj = methodHookParam.args[0];
                        if (obj instanceof Boolean) {
                            if (((Boolean) obj).booleanValue()) {
                                methodHookParam.args[0] = Boolean.FALSE;
                            }
                        } else if ((obj instanceof Integer) && ((Integer) obj).intValue() != 0) {
                            methodHookParam.args[0] = 0;
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("forceDark", th);
                }
            }
        }).size() + "+" + XposedBridge.hookAllMethods(Hidden.VIEW_ROOT_IMPL, "determineForceDarkType", new XC_MethodHook() {
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (Prefs.forceLight && Prefs.enabled && !methodHookParam.hasThrowable()) {
                        Object result = methodHookParam.getResult();
                        if (!(result instanceof Integer) || ((Integer) result).intValue() == 0) {
                            return;
                        }
                        methodHookParam.setResult(0);
                    }
                } catch (Throwable th) {
                    L.eLimited("forceDarkType", th);
                }
            }
        }).size());
    }

    private static void hookActivities() {
        XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnCreate", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (methodHookParam.args[0] instanceof Activity) {
                        Activity activity = (Activity) methodHookParam.args[0];
                        AppEngine.noteActivityCreate(activity.getClass().getName());
                        ClassLoader classLoader = activity.getClass().getClassLoader();
                        FlutterHooks.tryInstall(classLoader);
                        Images.tryPowerImage(classLoader);
                        Diag.maybeScan(activity);
                        if (Prefs.debug) {
                            if (L.limited("activity:" + activity.getClass().getName(), 1)) {
                                L.i("activity " + activity.getClass().getName());
                            }
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("onCreate", th);
                }
            }
        });
        XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnResume", new XC_MethodHook() {
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (Prefs.reloadIfChanged()) {
                        Fx.rebuild();
                        StringBuilder sb = new StringBuilder("settings reloaded: active=");
                        sb.append(Prefs.enabled);
                        sb.append(" style=");
                        sb.append(Prefs.style);
                        sb.append(" filter=");
                        sb.append(Fx.exact ? "shader" : "matrix");
                        L.i(sb.toString());
                        Roots.reapplyAll();
                    }
                    if (methodHookParam.args.length <= 0 || !(methodHookParam.args[0] instanceof Activity)) {
                        return;
                    }
                    Diag.scheduleTreeDump((Activity) methodHookParam.args[0]);
                } catch (Throwable th) {
                    L.eLimited("onResume", th);
                }
            }
        });
    }
}
