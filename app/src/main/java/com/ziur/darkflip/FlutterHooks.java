package com.ziur.darkflip;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class FlutterHooks {
    private static volatile boolean installed;
    private static boolean seenLogged;
    private static int tries;

    private FlutterHooks() {
    }

    static void tryInstall(ClassLoader classLoader) {
        int i;
        if (installed || !Prefs.flutterTexture || (i = tries) >= 60 || classLoader == null) {
            return;
        }
        tries = i + 1;
        try {
            Class clsFindClassIfExists = XposedHelpers.findClassIfExists("io.flutter.embedding.android.FlutterActivityAndFragmentDelegate", classLoader);
            Class clsFindClassIfExists2 = XposedHelpers.findClassIfExists("io.flutter.embedding.android.RenderMode", classLoader);
            if (clsFindClassIfExists != null && clsFindClassIfExists2 != null && clsFindClassIfExists2.isEnum()) {
                install(clsFindClassIfExists, clsFindClassIfExists2);
            }
        } catch (Throwable th) {
            L.eLimited("flutter.tryInstall", th);
        }
    }

    private static void install(Class<?> cls, final Class<?> cls2) throws Throwable {
        Object texture = null;
        for (Object constant : cls2.getEnumConstants()) {
            if ("texture".equals(((Enum<?>) constant).name())) {
                texture = constant;
            }
        }
        final Object obj = texture;
        if (obj == null) {
            installed = true;
            L.i("Flutter RenderMode has no 'texture' constant; leaving the render mode alone");
            return;
        }
        // Find the delegate's host field: an interface type with a no-arg getter returning RenderMode.
        Field hostField = null;
        Method getter = null;
        Class<?> hostType = null;
        for (Field candidate : cls.getDeclaredFields()) {
            Class<?> type = candidate.getType();
            if (type.isInterface()) {
                Method m = renderModeGetter(type, cls2);
                if (m != null) {
                    hostField = candidate;
                    getter = m;
                    hostType = type;
                    break;
                }
            }
        }
        final Field field = hostField;
        final Method methodRenderModeGetter = getter;
        final Class<?> cls3 = hostType;
        if (field == null) {
            installed = true;
            L.i("Flutter delegate has no host field with a RenderMode getter; leaving the render mode alone");
            return;
        }
        field.setAccessible(true);
        methodRenderModeGetter.setAccessible(true);
        if (XposedBridge.hookAllMethods(cls, "onCreateView", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Object obj3 = field.get(methodHookParam.thisObject);
                    if (obj3 != null && Proxy.isProxyClass(obj3.getClass())) {
                        for (int i2 = 0; i2 < methodHookParam.args.length; i2++) {
                            if (Boolean.TRUE.equals(methodHookParam.args[i2])) {
                                methodHookParam.args[i2] = Boolean.FALSE;
                            }
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("flutter.onCreateView", th);
                }
            }
        }).isEmpty()) {
            installed = true;
            L.i("Flutter delegate has no onCreateView by that name; leaving the render mode alone");
        } else {
            XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    final Object obj3;
                    try {
                        if (!Prefs.flutterTexture || !Prefs.enabled || (obj3 = field.get(methodHookParam.thisObject)) == null || Proxy.isProxyClass(obj3.getClass())) {
                            return;
                        }
                        try {
                            if (methodRenderModeGetter.invoke(obj3, (Object[]) null) == obj) {
                                if (L.limited("flutter.asis", 1)) {
                                    L.i("Flutter host already renders into a texture: " + obj3.getClass().getName());
                                    return;
                                }
                                return;
                            }
                        } catch (Throwable unused) {
                        }
                        field.set(methodHookParam.thisObject, Proxy.newProxyInstance(cls3.getClassLoader(), new Class[]{cls3}, new InvocationHandler() {
                            @Override
                            public Object invoke(Object obj4, Method method, Object[] objArr) throws Throwable {
                                if (method.getReturnType() == cls2 && (objArr == null || objArr.length == 0)) {
                                    if (L.limited("flutter.mode", 2)) {
                                        L.i("Flutter render mode -> texture (host " + obj3.getClass().getName() + ")");
                                    }
                                    return obj;
                                }
                                try {
                                    if (!method.isAccessible()) {
                                        method.setAccessible(true);
                                    }
                                    return method.invoke(obj3, objArr);
                                } catch (InvocationTargetException e) {
                                    if (e.getCause() != null) {
                                        throw e.getCause();
                                    }
                                    throw e;
                                }
                            }
                        }));
                    } catch (Throwable th) {
                        L.eLimited("flutter.wrapHost", th);
                    }
                }
            });
            installed = true;
            L.i("Flutter embedding found; render mode will be forced to texture");
        }
    }

    private static Method renderModeGetter(Class<?> cls, Class<?> cls2) {
        for (Method method : cls.getMethods()) {
            if (method.getReturnType() == cls2 && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    static void onFlutterView(View view, boolean z) {
        String str;
        if (seenLogged) {
            return;
        }
        seenLogged = true;
        StringBuilder sb = new StringBuilder("Flutter view seen: ");
        sb.append(z ? "SurfaceView" : "TextureView");
        sb.append(" ");
        sb.append(Util.chain(view));
        if (z) {
            str = installed ? " (render-mode hook installed but this page still uses a surface)" : " (render-mode hook NOT installed: embedding classes not found by name)";
        } else {
            str = "";
        }
        sb.append(str);
        L.i(sb.toString());
    }
}
