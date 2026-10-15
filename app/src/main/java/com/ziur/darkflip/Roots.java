package com.ziur.darkflip;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.WeakHashMap;

final class Roots {
    static final int MODE_INVERTED = 2;
    static final int MODE_NONE = 0;
    static final int MODE_PLAIN = 1;
    private static final int SH = 40;
    private static final int SW = 24;
    private static final int TYPE_PRESENTATION = 2037;
    private static final int TYPE_PRIVATE_PRESENTATION = 2030;
    private static final WeakHashMap<View, State> MAP = new WeakHashMap<>();
    private static final HashMap<String, Boolean> REMEMBER = new HashMap<>();
    private static final ThreadLocal<int[]> DRAWING = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[3];
        }
    };

    private Roots() {
    }

    static final class State {
        Paint appLayerPaint;
        boolean applied;
        boolean autoOff;
        Bitmap bmp;
        boolean copying;
        int darkStreak;
        boolean dirty;
        long flipWindowStart;
        int flips;
        boolean flutter;
        final boolean forcedOff;
        long frozenUntil;
        int gen;
        volatile boolean inverted;
        final String key;
        boolean[] mask;
        final String owner;
        int[] px;
        long quietUntil;
        final WeakReference<View> ref;
        boolean sampleScheduled;
        int samples;
        boolean selfCall;
        boolean shadowsOff;
        int appLayerType = 0;
        boolean appForceDarkAllowed = true;
        long freezeLength = 20000;
        float lightOn = 1.0f;
        float lightOff = 1.0f;
        float vetoLight = -1.0f;

        State(View view, String str, String str2, boolean z) {
            this.ref = new WeakReference<>(view);
            this.key = str;
            this.owner = str2;
            this.forcedOff = z;
        }
    }

    static State get(View view) {
        State state;
        if (view == null) {
            return null;
        }
        WeakHashMap<View, State> weakHashMap = MAP;
        synchronized (weakHashMap) {
            state = weakHashMap.get(view);
        }
        return state;
    }

    static State of(View view) {
        if (view == null) {
            return null;
        }
        try {
            return get(view.getRootView());
        } catch (Throwable unused) {
            return null;
        }
    }

    static int[] drawState() {
        return DRAWING.get();
    }

    static void track(View view, WindowManager.LayoutParams layoutParams) {
        if (view == null) {
            return;
        }
        synchronized (MAP) {
            if (MAP.containsKey(view)) {
                return;
            }
        }
        Activity activity = Util.ownerActivity(view);
        String name = activity == null ? "" : activity.getClass().getName();
        String flutterPage = activity == null ? null : Util.flutterPage(activity);
        String owner = flutterPage == null ? name : name + "#" + flutterPage;
        int type = layoutParams != null ? layoutParams.type : 0;
        boolean presentation = type == 2030 || type == 2037; // TYPE_PRIVATE_PRESENTATION, TYPE_PRESENTATION
        State state = new State(view, type + "|" + view.getClass().getName() + "|" + owner, owner,
                presentation || Util.containsAny(Util.lower(name), Prefs.skipKeys));
        try {
            state.appForceDarkAllowed = view.isForceDarkAllowed();
            int layerType = view.getLayerType();
            if (layerType != 0) {
                state.appLayerType = layerType;
                Object paint = XposedHelpers.getObjectField(view, "mLayerPaint");
                if (paint instanceof Paint) {
                    state.appLayerPaint = (Paint) paint;
                }
            }
        } catch (Throwable ignored) {
        }
        synchronized (MAP) {
            if (MAP.containsKey(view)) {
                return;
            }
            MAP.put(view, state);
        }
        boolean invert = Prefs.enabled && !state.forcedOff;
        if (invert && Prefs.autoSkip) {
            Boolean remembered;
            synchronized (REMEMBER) {
                remembered = REMEMBER.get(state.key);
            }
            if (remembered != null) {
                invert = remembered.booleanValue();
            }
            state.autoOff = !invert;
        }
        if (Prefs.debug) {
            L.i("window + " + view.getClass().getName() + " type=" + type
                    + " title=" + (layoutParams != null ? layoutParams.getTitle() : "?")
                    + " owner=" + state.owner
                    + (presentation ? " [presentation]" : state.forcedOff ? " [skipped by keyword]" : "")
                    + " -> " + (invert ? "invert" : "leave"));
        }
        setInverted(state, invert, "init");
    }

    static void setInverted(State state, boolean z, String str) {
        View view = state.ref.get();
        if (view == null) {
            return;
        }
        if (state.applied && state.inverted == z && state.gen == Fx.gen) {
            return;
        }
        int i = 1;
        boolean z2 = (state.applied && state.inverted == z) ? false : true;
        boolean z3 = !state.applied;
        state.inverted = z;
        state.applied = true;
        state.gen = Fx.gen;
        state.quietUntil = SystemClock.uptimeMillis() + 180;
        state.selfCall = true;
        try {
            if (z) {
                if (state.appLayerType != 1) {
                    i = 2;
                }
                if (view.getLayerType() != i) {
                    view.setLayerType(i, Fx.rootPaint);
                } else {
                    view.setLayerPaint(Fx.rootPaint);
                }
            } else if (!z3) {
                view.setLayerType(state.appLayerType, state.appLayerPaint);
            }
            if (Prefs.forceLight) {
                if (z) {
                    view.setForceDarkAllowed(false);
                } else if (!z3) {
                    view.setForceDarkAllowed(state.appForceDarkAllowed);
                }
            }
        } catch (Throwable th) {
            L.e("setInverted", th);
        } finally {
            state.selfCall = false;
        }
        if (!z3 || z) {
            if (!z3 && z2 && Prefs.debug) {
                StringBuilder sb = new StringBuilder("window ");
                sb.append(z ? "ON " : "OFF");
                sb.append(" (");
                sb.append(str);
                sb.append(") owner=");
                sb.append(state.owner);
                L.i(sb.toString());
            }
            try {
                Media.onRootState(view);
                if (!z3 && z2 && view.isAttachedToWindow()) {
                    Bars.poke(view);
                    if (Prefs.keepImages) {
                        Util.invalidateTree(view);
                    }
                }
            } catch (Throwable th) {
                L.e("setInverted.after", th);
            }
        }
    }

    static void markFlutter(State state) {
        if (state == null || state.flutter) {
            return;
        }
        state.flutter = true;
        if (!Prefs.enabled || state.forcedOff) {
            return;
        }
        setInverted(state, true, "flutter");
    }

    static void reapplyAll() {
        ArrayList<State> arrayList;
        WeakHashMap<View, State> weakHashMap = MAP;
        synchronized (weakHashMap) {
            arrayList = new ArrayList(weakHashMap.values());
        }
        for (final State state : arrayList) {
            View view = state.ref.get();
            if (view != null) {
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            state.frozenUntil = 0L;
                            state.flips = 0;
                            state.vetoLight = -1.0f;
                            if (!Prefs.enabled || state.forcedOff) {
                                Roots.setInverted(state, false, "settings");
                            } else if (!state.autoOff || state.flutter || !Prefs.autoSkip) {
                                Roots.setInverted(state, true, "settings");
                            }
                            View view2 = state.ref.get();
                            if (view2 != null) {
                                Bars.poke(view2);
                                Util.invalidateTree(view2);
                            }
                        } catch (Throwable th) {
                            L.e("reapply", th);
                        }
                    }
                });
            }
        }
    }

    static void drawBegin(View view, Object obj) {
        State state = get(view);
        int[] iArr = DRAWING.get();
        iArr[1] = iArr[1] + 1;
        if (state == null) {
            iArr[0] = 0;
            iArr[2] = 0;
            return;
        }
        int i = (!Prefs.autoSkip || !Prefs.enabled || state.flutter || state.forcedOff) ? 0 : 1;
        iArr[0] = state.inverted ? 2 : i != 0 ? 1 : 0;
        iArr[2] = i;
        state.dirty = true;
        boolean z = state.inverted && Prefs.noShadow;
        if (state.shadowsOff != z) {
            state.shadowsOff = z;
            try {
                HardwareRenderer hardwareRenderer = (HardwareRenderer) obj;
                float f = 0.0f;
                float f2 = z ? 0.0f : 0.039f;
                if (!z) {
                    f = 0.19f;
                }
                hardwareRenderer.setLightSourceAlpha(f2, f);
            } catch (Throwable th) {
                L.eLimited("shadow", th);
            }
        }
        if (state.sampleScheduled || i == 0) {
            return;
        }
        scheduleSample(state, view);
    }

    static void drawEnd() {
        int[] iArr = DRAWING.get();
        iArr[0] = 0;
        iArr[2] = 0;
    }

    private static void scheduleSample(final State state, View view) {
        long j;
        if (state.samples == 0) {
            j = 50;
        } else if (state.samples == 1) {
            j = 160;
        } else if (state.samples == 2) {
            j = 350;
        } else {
            j = state.samples <= 6 ? 800L : 2000L;
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis + j < state.quietUntil) {
            j = state.quietUntil - jUptimeMillis;
        }
        state.sampleScheduled = true;
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                state.sampleScheduled = false;
                try {
                    Roots.sample(state);
                } catch (Throwable th) {
                    state.copying = false;
                    L.eLimited("sample", th);
                }
            }
        }, j);
    }

    private static void sample(final State state) {
        Object viewRootImpl;
        Surface surface;
        View view = state.ref.get();
        if (view == null || !view.isAttachedToWindow() || !Prefs.autoSkip || !Prefs.enabled || state.flutter || state.forcedOff || !state.dirty || state.copying || SystemClock.uptimeMillis() < state.frozenUntil) {
            return;
        }
        state.dirty = false;
        state.samples++;
        if (view.getWindowVisibility() != 0 || view.getWidth() <= 0 || view.getHeight() <= 0 || (viewRootImpl = Hidden.viewRootImpl(view)) == null || (surface = Hidden.surface(viewRootImpl)) == null || !surface.isValid()) {
            return;
        }
        if (state.bmp == null) {
            state.bmp = Bitmap.createBitmap(24, 40, Bitmap.Config.ARGB_8888);
            state.px = new int[960];
            state.mask = new boolean[960];
        }
        final boolean z = state.inverted;
        state.copying = true;
        PixelCopy.request(surface, state.bmp, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int i) {
                state.copying = false;
                if (i == 0) {
                    try {
                        Roots.analyze(state, z);
                    } catch (Throwable th) {
                        L.eLimited("analyze", th);
                    }
                }
            }
        }, Util.handler());
    }

    private static void analyze(State state, boolean z) {
        boolean zMarkViews;
        boolean zEquals;
        if (z == state.inverted && SystemClock.uptimeMillis() >= state.quietUntil) {
            state.bmp.getPixels(state.px, 0, 24, 0, 0, 24, 40);
            boolean[] zArr = state.mask;
            View view = state.ref.get();
            if (view == null || zArr == null) {
                zMarkViews = false;
            } else {
                Arrays.fill(zArr, false);
                try {
                    zMarkViews = Media.markViews(view, zArr, 24, 40) | Images.markSpots(view, zArr, 24, 40);
                } catch (Throwable th) {
                    L.eLimited("mask", th);
                    zMarkViews = false;
                }
            }
            int i = 0;
            int i2 = 0;
            int i3 = 0;
            for (int i4 = 0; i4 < state.px.length; i4++) {
                if (!zMarkViews || !zArr[i4]) {
                    int px = state.px[i4];
                    if ((px >>> 24) >= 128) {
                        float fUndoLightness = (((((px >> 16) & 255) * 0.27f) + (((px >> 8) & 255) * 0.53f)) + ((px & 255) * 0.2f)) / 255.0f;
                        if (z) {
                            fUndoLightness = Fx.undoLightness(fUndoLightness);
                        }
                        i++;
                        if (fUndoLightness > 0.62f) {
                            i2++;
                        } else if (fUndoLightness < 0.3f) {
                            i3++;
                        }
                    }
                }
            }
            boolean z2 = view != null && Media.hasBigMedia(view);
            if ((z2 ? 10 : 4) * i < state.px.length) {
                if (z2) {
                    decide(state, false, "video/camera page");
                    return;
                }
                return;
            }
            float f = i;
            float f2 = i2 / f;
            float f3 = i3 / f;
            if (z) {
                state.lightOn = f2;
            } else {
                state.lightOff = f2;
            }
            if (f2 >= 0.42f) {
                state.darkStreak = 0;
                if (!z && state.vetoLight >= 0.0f) {
                    if (Math.abs(f2 - state.vetoLight) <= 0.12f) {
                        return;
                    } else {
                        state.vetoLight = -1.0f;
                    }
                }
                decide(state, true, "light page " + Math.round(f2 * 100.0f) + "%");
                return;
            }
            if (f3 >= 0.7f && f2 <= 0.1f) {
                state.darkStreak++;
                HashMap<String, Boolean> map = REMEMBER;
                synchronized (map) {
                    zEquals = Boolean.TRUE.equals(map.get(state.key));
                }
                int i5 = state.darkStreak;
                if (!zEquals ? i5 >= 2 || f3 >= 0.88f : i5 >= 3) {
                    decide(state, false, "dark page " + Math.round(f3 * 100.0f) + "%");
                    return;
                }
                View view2 = state.ref.get();
                if (view2 == null || state.sampleScheduled || !state.inverted) {
                    return;
                }
                state.dirty = true;
                scheduleSample(state, view2);
                return;
            }
            state.darkStreak = 0;
        }
    }

    private static void decide(State state, boolean z, String str) {
        if (state.inverted == z) {
            return;
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis < state.frozenUntil) {
            return;
        }
        if (jUptimeMillis - state.flipWindowStart > 6000) {
            state.flipWindowStart = jUptimeMillis;
            state.flips = 0;
        }
        int i = state.flips + 1;
        state.flips = i;
        if (i >= 3) {
            z = Math.min(state.lightOn, state.lightOff) >= 0.25f;
            state.frozenUntil = jUptimeMillis + state.freezeLength;
            state.freezeLength = Math.min(state.freezeLength * 3, 600000L);
            state.flips = 0;
            state.vetoLight = z ? -1.0f : state.lightOff;
            StringBuilder sb = new StringBuilder("window keeps flipping, settling on ");
            sb.append(z ? "ON" : "OFF");
            sb.append(" owner=");
            sb.append(state.owner);
            L.d(sb.toString());
            if (state.inverted == z) {
                return;
            } else {
                str = "settled";
            }
        }
        HashMap<String, Boolean> map = REMEMBER;
        synchronized (map) {
            if (map.size() > 300) {
                map.clear();
            }
            map.put(state.key, Boolean.valueOf(z));
        }
        state.samples = 1;
        state.autoOff = !z;
        setInverted(state, z, str);
    }
}
