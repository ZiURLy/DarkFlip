package com.ziur.darkflip;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.PixelCopy;
import android.view.SurfaceControl;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.WeakHashMap;
import com.ziur.darkflip.Roots;

final class Media {
    static final int MEDIA = 2;
    private static final int SH = 24;
    private static final int SW = 24;
    static final int UI = 1;
    static final int UNKNOWN = 0;
    private static Field blastField;
    private static boolean blastLooked;
    private static final String[] MEDIA_KEYS = {"video", "player", "camera", "preview", "ijk", "exo", "media", "scan", "capture", "unity", "cocos", "game", "zxing", "barcode"};
    private static final WeakHashMap<View, M> MAP = new WeakHashMap<>();

    private Media() {
    }

    static final class M {
        Bitmap bmp;
        Object counterPaint;
        final boolean fixed;
        final boolean flutter;
        int hits;
        final boolean isSurface;
        int kind;
        int lastGen;
        boolean lastInv;
        WeakReference<SurfaceControl> lastSc;
        int misses;
        int[] px;
        final WeakReference<View> ref;
        boolean sampling;
        int tries;

        M(View view, boolean z, boolean z2, int i, boolean z3) {
            this.ref = new WeakReference<>(view);
            this.isSurface = z;
            this.flutter = z2;
            this.kind = i;
            this.fixed = z3;
        }
    }

    static void onAttach(View view) {
        M mCreate;
        boolean z;
        WeakHashMap<View, M> weakHashMap = MAP;
        synchronized (weakHashMap) {
            mCreate = weakHashMap.get(view);
            if (mCreate == null) {
                mCreate = create(view);
                weakHashMap.put(view, mCreate);
                z = true;
            } else {
                z = false;
            }
        }
        Roots.State stateOf = Roots.of(view);
        if (z && Prefs.debug) {
            StringBuilder sb = new StringBuilder();
            sb.append(mCreate.isSurface ? "SurfaceView " : "TextureView ");
            sb.append(kindName(mCreate));
            sb.append(": ");
            sb.append(Util.chain(view));
            sb.append(" owner=");
            sb.append(stateOf != null ? stateOf.owner : "?");
            L.i(sb.toString());
        }
        if (mCreate.flutter) {
            Roots.markFlutter(stateOf);
            FlutterHooks.onFlutterView(view, mCreate.isSurface);
        }
        apply(view, mCreate);
        if (mCreate.fixed || mCreate.kind == 1 || mCreate.sampling) {
            return;
        }
        mCreate.sampling = true;
        mCreate.tries = 0;
        view.postDelayed(new Sampler(mCreate), 250L);
    }

    private static M create(View view) {
        boolean z = view instanceof SurfaceView;
        String strOwnNames = Util.ownNames(view, z ? SurfaceView.class : TextureView.class);
        if (strOwnNames.contains("flutter") || Util.parentNames(view, 6).contains("flutter")) {
            return new M(view, z, true, 1, true);
        }
        return Util.containsAny(strOwnNames, MEDIA_KEYS) ? new M(view, z, false, 2, true) : new M(view, z, false, 0, false);
    }

    private static String kindName(M m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.flutter ? "[flutter]" : m.kind == 1 ? "[ui]" : m.kind == 2 ? "[media]" : "[unknown]");
        sb.append(m.fixed ? "" : "~");
        return sb.toString();
    }

    static void onSurfaceUpdated(SurfaceView surfaceView) {
        M m;
        WeakHashMap<View, M> weakHashMap = MAP;
        synchronized (weakHashMap) {
            m = weakHashMap.get(surfaceView);
        }
        if (m != null) {
            apply(surfaceView, m);
        }
    }

    static void onRootState(View root) {
        ArrayList<View> views;
        synchronized (MAP) {
            if (MAP.isEmpty()) {
                return;
            }
            views = new ArrayList<>(MAP.keySet());
        }
        for (View view : views) {
            if (view == null) {
                continue;
            }
            try {
                if (!view.isAttachedToWindow() || view.getRootView() != root) {
                    continue;
                }
                M m;
                synchronized (MAP) {
                    m = MAP.get(view);
                }
                if (m != null) {
                    apply(view, m);
                }
            } catch (Throwable th) {
                L.eLimited("media.onRootState", th);
            }
        }
    }

    static boolean hasBigMedia(View root) {
        ArrayList<View> views;
        synchronized (MAP) {
            if (MAP.isEmpty()) {
                return false;
            }
            views = new ArrayList<>(MAP.keySet());
        }
        long rootArea = ((long) root.getWidth()) * ((long) root.getHeight());
        if (rootArea <= 0) {
            return false;
        }
        for (View view : views) {
            if (view == null) {
                continue;
            }
            try {
                if (!view.isAttachedToWindow() || view.getRootView() != root || !view.isShown()) {
                    continue;
                }
                M m;
                synchronized (MAP) {
                    m = MAP.get(view);
                }
                if (m != null && m.kind == 2 && ((long) view.getWidth()) * ((long) view.getHeight()) * 100 >= 40 * rootArea) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    static boolean markViews(View root, boolean[] mask, int cols, int rows) {
        ArrayList<View> views;
        synchronized (MAP) {
            if (MAP.isEmpty()) {
                return false;
            }
            views = new ArrayList<>(MAP.keySet());
        }
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        boolean marked = false;
        Rect rect = new Rect();
        for (View view : views) {
            if (view == null) {
                continue;
            }
            try {
                if (!view.isAttachedToWindow() || !view.isShown() || view.getRootView() != root) {
                    continue;
                }
                M m;
                synchronized (MAP) {
                    m = MAP.get(view);
                }
                if (m != null && m.kind != 1 && view.getGlobalVisibleRect(rect)) {
                    marked |= Images.markRect(mask, cols, rows, width, height, rect.left, rect.top, rect.right, rect.bottom);
                }
            } catch (Throwable ignored) {
            }
        }
        return marked;
    }

    private static void apply(View view, M m) {
        try {
            Roots.State stateOf = Roots.of(view);
            boolean z = false;
            boolean z2 = stateOf != null && stateOf.inverted;
            if (m.isSurface) {
                SurfaceView surfaceView = (SurfaceView) view;
                if (z2 && m.kind == 1) {
                    z = true;
                }
                applySurface(surfaceView, m, z);
                return;
            }
            Paint paint = (!z2 || m.kind == 1) ? null : Fx.counterPaint;
            if (paint != m.counterPaint) {
                ((TextureView) view).setLayerPaint(paint);
                m.counterPaint = paint;
            }
        } catch (Throwable th) {
            L.eLimited("media.apply", th);
        }
    }

    private static void applySurface(SurfaceView surfaceView, M m, boolean z) {
        SurfaceControl surfaceControlSurfaceControlOf = surfaceControlOf(surfaceView);
        if (surfaceControlSurfaceControlOf == null || !surfaceControlSurfaceControlOf.isValid()) {
            return;
        }
        SurfaceControl surfaceControl = m.lastSc == null ? null : m.lastSc.get();
        if (surfaceControl == surfaceControlSurfaceControlOf && m.lastInv == z && (!z || m.lastGen == Fx.gen)) {
            return;
        }
        if (surfaceControl != surfaceControlSurfaceControlOf && !z) {
            m.lastSc = new WeakReference<>(surfaceControlSurfaceControlOf);
            m.lastInv = false;
            return;
        }
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        try {
            Hidden.setColorTransform(transaction, surfaceControlSurfaceControlOf, z ? Fx.LIN_M : Fx.IDENT_M, z ? Fx.LIN_T : Fx.ZERO_T);
            transaction.apply();
            transaction.close();
            m.lastSc = new WeakReference<>(surfaceControlSurfaceControlOf);
            m.lastInv = z;
            m.lastGen = Fx.gen;
            if (z && m.flutter && !Fx.flutterLinear) {
                Fx.flutterLinear = true;
                L.i("Flutter is drawing into a SurfaceView; using the SurfaceFlinger colour transform (lower quality)");
            }
            if (Prefs.debug && L.limited("sv.transform", 12)) {
                StringBuilder sb = new StringBuilder("SurfaceView colour transform ");
                sb.append(z ? "ON" : "off");
                sb.append(": ");
                sb.append(surfaceView.getClass().getName());
                L.i(sb.toString());
            }
        } catch (Throwable th) {
            transaction.close();
            throw th;
        }
    }

    private static SurfaceControl surfaceControlOf(SurfaceView surfaceView) {
        try {
            if (!blastLooked) {
                blastLooked = true;
                try {
                    Field declaredField = SurfaceView.class.getDeclaredField("mBlastSurfaceControl");
                    declaredField.setAccessible(true);
                    blastField = declaredField;
                } catch (Throwable unused) {
                }
            }
            Field field = blastField;
            if (field != null) {
                Object obj = field.get(surfaceView);
                if ((obj instanceof SurfaceControl) && ((SurfaceControl) obj).isValid()) {
                    return (SurfaceControl) obj;
                }
            }
        } catch (Throwable unused2) {
        }
        return surfaceView.getSurfaceControl();
    }

    private static final class Sampler implements Runnable {
        final M m;

        Sampler(M m) {
            this.m = m;
        }

        @Override
        public void run() {
            long j;
            View view = this.m.ref.get();
            if (view == null || !view.isAttachedToWindow() || (this.m.kind == 1 && this.m.fixed) || !Prefs.enabled) {
                this.m.sampling = false;
                return;
            }
            this.m.tries++;
            try {
                if (view.isShown() && view.getWidth() > 8 && view.getHeight() > 8) {
                    sample(view);
                }
            } catch (Throwable th) {
                L.eLimited("media.sample", th);
            }
            if (this.m.tries < 16) {
                if (this.m.tries < 4) {
                    j = 250;
                } else {
                    j = this.m.tries < 9 ? 900 : 3000;
                }
                view.postDelayed(this, j);
                return;
            }
            this.m.sampling = false;
        }

        private void sample(final View view) {
            if (this.m.bmp == null) {
                this.m.bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
                this.m.px = new int[576];
            }
            if (this.m.isSurface) {
                SurfaceView surfaceView = (SurfaceView) view;
                if (surfaceView.getHolder() == null || surfaceView.getHolder().getSurface() == null || !surfaceView.getHolder().getSurface().isValid()) {
                    return;
                }
                PixelCopy.request(surfaceView, this.m.bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                    @Override
                    public void onPixelCopyFinished(int i) {
                        if (i == 0) {
                            Sampler.this.onPixels(view);
                        }
                    }
                }, Util.handler());
                return;
            }
            TextureView textureView = (TextureView) view;
            if (textureView.isAvailable()) {
                textureView.getBitmap(this.m.bmp);
                onPixels(view);
            }
        }

        private void onPixels(View view) {
            try {
                this.m.bmp.getPixels(this.m.px, 0, 24, 0, 0, 24, 24);
                int i = 0;
                int i2 = 0;
                for (int i3 : this.m.px) {
                    if ((i3 >>> 24) >= 128) {
                        i++;
                        if (Math.min((i3 >> 16) & 255, Math.min((i3 >> 8) & 255, i3 & 255)) >= 205) {
                            i2++;
                        }
                    }
                }
                if (i * 3 < this.m.px.length) {
                    return;
                }
                if (i2 * 100 >= i * 93) {
                    this.m.misses = 0;
                    M m = this.m;
                    int i4 = m.hits + 1;
                    m.hits = i4;
                    if (i4 < 4 || this.m.kind == 1) {
                        return;
                    }
                    this.m.kind = 1;
                    L.d("media view looks like a light UI surface, inverting: " + view.getClass().getName());
                    Media.apply(view, this.m);
                    return;
                }
                this.m.hits = 0;
                M m2 = this.m;
                int i5 = m2.misses + 1;
                m2.misses = i5;
                if (i5 < 3 || this.m.kind == 2 || this.m.fixed) {
                    return;
                }
                this.m.kind = 2;
                L.d("media view looks like video/camera content: " + view.getClass().getName());
                Media.apply(view, this.m);
            } catch (Throwable th) {
                L.eLimited("media.onPixels", th);
            }
        }
    }
}
