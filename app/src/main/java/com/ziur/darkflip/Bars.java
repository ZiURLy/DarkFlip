package com.ziur.darkflip;

import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import com.ziur.darkflip.Roots;

final class Bars {
    static final int APPEARANCE_LIGHT_NAVIGATION_BARS = 16;
    static final int APPEARANCE_LIGHT_STATUS_BARS = 8;
    static final int FLIP = 24;
    private static final ThreadLocal<Object[]> PENDING = new ThreadLocal<>();

    private Bars() {
    }

    static void install() {
        L.d("bars: relayoutWindow hooks=" + XposedBridge.hookAllMethods(Hidden.VIEW_ROOT_IMPL, "relayoutWindow", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Roots.State state;
                try {
                    Bars.PENDING.remove();
                    if (Prefs.fixBars && methodHookParam.args.length != 0) {
                        Object obj = methodHookParam.args[0];
                        if ((obj instanceof WindowManager.LayoutParams) && (state = Roots.get((View) XposedHelpers.getObjectField(methodHookParam.thisObject, "mView"))) != null && state.inverted) {
                            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) obj;
                            int i = Hidden.barAppearance(layoutParams);
                            Hidden.setBarAppearance(layoutParams, i ^ Bars.FLIP);
                            Bars.PENDING.set(new Object[]{layoutParams, Integer.valueOf(i)});
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("bars.before", th);
                }
            }

            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Object[] objArr = Bars.PENDING.get();
                    if (objArr == null) {
                        return;
                    }
                    Bars.PENDING.remove();
                    Hidden.setBarAppearance((WindowManager.LayoutParams) objArr[0], ((Integer) objArr[1]).intValue());
                } catch (Throwable th) {
                    L.eLimited("bars.after", th);
                }
            }
        }).size());
    }

    static void poke(View view) {
        if (Prefs.fixBars) {
            try {
                Object viewRootImpl = Hidden.viewRootImpl(view);
                if (viewRootImpl == null) {
                    return;
                }
                XposedHelpers.setBooleanField(viewRootImpl, "mWindowAttributesChanged", true);
                view.requestLayout();
            } catch (Throwable th) {
                L.eLimited("bars.poke", th);
            }
        }
    }
}
