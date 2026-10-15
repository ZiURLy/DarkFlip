package com.ziur.darkflip;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.Locale;

final class Util {
    private static Handler mainHandler;

    private Util() {
    }

    static boolean containsAny(String str, String[] strArr) {
        if (str != null && !str.isEmpty() && strArr != null) {
            for (String str2 : strArr) {
                if (!str2.isEmpty() && str.contains(str2)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String lower(String str) {
        return str == null ? "" : str.toLowerCase(Locale.ROOT);
    }

    private static Activity activityOf(Context context) {
        for (int i = 0; i < 16 && context != null; i++) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    static Activity ownerActivity(View view) {
        try {
            if ("com.android.internal.policy.DecorView".equals(view.getClass().getName())) {
                Object objectField = XposedHelpers.getObjectField(view, "mWindow");
                if (objectField instanceof Window) {
                    Activity activityActivityOf = activityOf(((Window) objectField).getContext());
                    if (activityActivityOf != null) {
                        return activityActivityOf;
                    }
                }
            }
        } catch (Throwable unused) {
        }
        try {
            Activity activityActivityOf2 = activityOf(view.getContext());
            if (activityActivityOf2 != null) {
                return activityActivityOf2;
            }
            if (!(view instanceof ViewGroup) || ((ViewGroup) view).getChildCount() <= 0) {
                return null;
            }
            return activityOf(((ViewGroup) view).getChildAt(0).getContext());
        } catch (Throwable unused2) {
            return null;
        }
    }

    /** For a Flutter host activity, the page URL its getUrl() reports (without the query), else null. */
    static String flutterPage(Activity activity) {
        try {
            for (Class<?> cls = activity.getClass(); cls != null && cls != Activity.class && cls != Object.class; cls = cls.getSuperclass()) {
                if (cls.getName().toLowerCase(Locale.ROOT).contains("flutter")) {
                    Method method = activity.getClass().getMethod("getUrl", (Class<?>[]) null);
                    if (method.getReturnType() != String.class) {
                        return null;
                    }
                    Object url = method.invoke(activity, (Object[]) null);
                    if (!(url instanceof String)) {
                        return null;
                    }
                    String page = (String) url;
                    int query = page.indexOf('?');
                    if (query >= 0) {
                        page = page.substring(0, query);
                    }
                    return page.length() > 100 ? page.substring(0, 100) : page;
                }
            }
            return null;
        } catch (Throwable unused) {
            return null;
        }
    }

    static void invalidateTree(View view) {
        try {
            invalidateTree(view, 0);
        } catch (Throwable unused) {
        }
    }

    private static void invalidateTree(View view, int i) {
        view.invalidate();
        if (i >= 60 || !(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) view;
        int childCount = viewGroup.getChildCount();
        for (int i2 = 0; i2 < childCount; i2++) {
            View childAt = viewGroup.getChildAt(i2);
            if (childAt != null) {
                invalidateTree(childAt, i + 1);
            }
        }
    }

    static String chain(View view) {
        StringBuilder sb = new StringBuilder(view.getClass().getName());
        ViewParent parent = view.getParent();
        for (int i = 0; i < 7 && parent != null; i++) {
            sb.append(" < ");
            sb.append(parent.getClass().getName());
            parent = parent.getParent();
        }
        return sb.toString();
    }

    static String ownNames(View view, Class<?> cls) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> superclass = view.getClass(); superclass != null && superclass != cls && superclass != View.class && superclass != Object.class; superclass = superclass.getSuperclass()) {
            sb.append(superclass.getName());
            sb.append(' ');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    static String parentNames(View view, int i) {
        StringBuilder sb = new StringBuilder();
        ViewParent parent = view.getParent();
        for (int i2 = 0; i2 < i && parent != null; i2++) {
            for (Class<?> superclass = parent.getClass(); superclass != null && superclass != View.class && superclass != ViewGroup.class && superclass != Object.class; superclass = superclass.getSuperclass()) {
                sb.append(superclass.getName());
                sb.append(' ');
            }
            parent = parent.getParent();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    static Handler handler() {
        Looper looperMyLooper = Looper.myLooper();
        if (looperMyLooper != null && looperMyLooper != Looper.getMainLooper()) {
            return new Handler(looperMyLooper);
        }
        Handler handler = mainHandler;
        if (handler != null) {
            return handler;
        }
        Handler handler2 = new Handler(Looper.getMainLooper());
        mainHandler = handler2;
        return handler2;
    }
}
