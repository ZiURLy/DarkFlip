package com.ziur.darkflip;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.WeakHashMap;

final class Images {
    private static final int MAX_SPOTS = 6;
    private static final int N = 14;
    static final int V_CODE = 1;
    static final int V_CUTOUT = 3;
    static final int V_DARK_GLYPH = -3;
    static final int V_FLAT = -1;
    static final int V_HARDWARE = 4;
    static final int V_ICON = -4;
    static final int V_PICTURE = 2;
    static final int V_SPARSE = -5;
    static final int V_TWO_TONE = -2;
    static final int V_UI = 0;
    private static Field hostField;
    private static Field hostViewField;
    private static Field nodeField;
    private static volatile boolean powerImageDone;
    private static int powerImageTries;
    private static Field shaderBitmapField;
    private static boolean shaderFieldLooked;
    private static boolean spotFieldsLooked;
    private static final WeakHashMap<Bitmap, int[]> VERDICTS = new WeakHashMap<>();
    private static final WeakHashMap<Bitmap, Boolean> FRAMES = new WeakHashMap<>();
    private static final WeakHashMap<SurfaceTexture, Boolean> APP_TEXTURES = new WeakHashMap<>();
    private static final WeakHashMap<Surface, Boolean> TEXTURE_SURFACES = new WeakHashMap<>();
    private static final WeakHashMap<Surface, SurfaceTexture> TEXTURE_OF = new WeakHashMap<>();

    /** The app-created SurfaceTexture behind this Surface, or null. */
    static SurfaceTexture textureOf(Surface surface) {
        synchronized (TEXTURE_OF) {
            return TEXTURE_OF.get(surface);
        }
    }
    private static final WeakHashMap<Canvas, Boolean> TEXTURE_CANVASES = new WeakHashMap<>();
    private static volatile int textureCanvasCount = 0;
    private static final WeakHashMap<Bitmap, Bitmap> HANDED_OVER = new WeakHashMap<>();
    private static final WeakHashMap<View, Spots> SPOTS = new WeakHashMap<>();
    private static final ThreadLocal<Object[]> SCRATCH = new ThreadLocal<Object[]>() {
        @Override
        protected Object[] initialValue() {
            return new Object[]{new RectF(), new Matrix()};
        }
    };
    private static final ThreadLocal<int[]> BUF = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[4];
        }
    };
    private static final ThreadLocal<Work> WORK = new ThreadLocal<Work>() {
        @Override
        protected Work initialValue() {
            return new Work();
        }
    };

    static /* synthetic */ int access$1108() {
        int i = textureCanvasCount;
        textureCanvasCount = i + 1;
        return i;
    }

    private Images() {
    }

    private static final class Spots {
        int frame;
        int n;
        final float[] r;

        private Spots() {
            this.frame = -1;
            this.r = new float[24];
        }
    }

    private static final class Work {
        final int[] cnt;
        final int[] cols;
        final boolean[] op;
        final int[] pct;
        final int[] sb;
        final int[] sg;
        final int[] sr;

        private Work() {
            this.op = new boolean[196];
            this.cols = new int[196];
            this.cnt = new int[512];
            this.sr = new int[512];
            this.sg = new int[512];
            this.sb = new int[512];
            this.pct = new int[6];
        }
    }

    static boolean isPhoto(Bitmap bitmap) {
        return verdict(bitmap) > 0;
    }

    static int verdict(Bitmap bitmap) {
        if (bitmap != null) {
            try {
                if (!bitmap.isRecycled()) {
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    if (width >= 40 && height >= 40) {
                        int generationId = bitmap.getGenerationId();
                        WeakHashMap<Bitmap, int[]> weakHashMap = VERDICTS;
                        synchronized (weakHashMap) {
                            int[] iArr = weakHashMap.get(bitmap);
                            if (iArr != null && iArr[0] == generationId) {
                                return iArr[1];
                            }
                            int iClassify = classify(bitmap, width, height);
                            synchronized (weakHashMap) {
                                weakHashMap.put(bitmap, new int[]{generationId, iClassify});
                            }
                            if (Prefs.debug && width >= 240 && height >= 240) {
                                if (L.limited(iClassify > 0 ? "verdict.keep" : "verdict.ui", iClassify > 0 ? 6 : 16)) {
                                    L.i("image " + describe(iClassify, width, height));
                                }
                            }
                            return iClassify;
                        }
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return 0;
    }

    static String describe(int i, int i2, int i3) {
        int[] iArr = WORK.get().pct;
        return i2 + "x" + i3 + " -> " + name(i) + " (opaque " + iArr[0] + "%, detail " + iArr[1] + "%, edges " + iArr[2] + "%, colour " + iArr[3] + "%, dark " + iArr[4] + "%, two tones " + iArr[5] + "%)";
    }

    static String name(int i) {
        switch (i) {
            case -5:
                return "ui: sparse";
            case -4:
                return "ui: icon";
            case -3:
                return "ui: dark glyph";
            case -2:
                return "ui: light two-tone";
            case -1:
                return "ui: flat";
            case 0:
            default:
                return "ui";
            case 1:
                return "keep: code";
            case 2:
                return "keep: picture";
            case 3:
                return "keep: cut-out";
            case 4:
                return "keep: hardware bitmap";
        }
    }

    private static int classify(Bitmap bitmap, int i, int i2) {
        int i3;
        Work work = WORK.get();
        Arrays.fill(work.pct, 0);
        Bitmap.Config config = bitmap.getConfig();
        if (config == null || config == Bitmap.Config.ALPHA_8) {
            return 0;
        }
        if (Fx.isPreInverted(bitmap)) {
            return 0;
        }
        if (config == Bitmap.Config.HARDWARE) {
            WeakHashMap<Bitmap, Boolean> weakHashMap = FRAMES;
            synchronized (weakHashMap) {
                if (weakHashMap.containsKey(bitmap)) {
                    return 0;
                }
                return (bitmap.hasAlpha() || i < 160 || i2 < 160) ? 0 : 4;
            }
        }
        int[] iArr = BUF.get();
        boolean[] zArr = work.op;
        int[] iArr2 = work.cols;
        int i4 = 0;
        int i5 = 0;
        int i6 = 0;
        int i7 = 0;
        int i8 = 0;
        int i9 = 0;
        int i10 = 0;
        while (i10 < 14) {
            float f = 14.0f;
            int iMin = Math.min(i2 - 2, (int) (((i10 + 0.5f) * i2) / 14.0f));
            int i11 = i4;
            int i12 = i5;
            int i13 = i6;
            int i14 = i7;
            int i15 = i8;
            int i16 = i9;
            int i17 = 0;
            for (int i18 = 14; i17 < i18; i18 = 14) {
                int i19 = i17;
                int i20 = i10;
                int[] iArr3 = iArr2;
                bitmap.getPixels(iArr, 0, 2, Math.min(i - 2, (int) (((i17 + 0.5f) * i) / f)), iMin, 2, 2);
                int i21 = iArr[0];
                if ((i21 >>> 24) < 200) {
                    zArr[(i20 * 14) + i19] = false;
                } else {
                    zArr[(i20 * 14) + i19] = true;
                    int i22 = i11 + 1;
                    iArr3[i11] = 16777215 & i21;
                    int i23 = (i21 >> 16) & 255;
                    int i24 = (i21 >> 8) & 255;
                    int i25 = i21 & 255;
                    int iMax = Math.max(i23, Math.max(i24, i25));
                    int iMin2 = Math.min(i23, Math.min(i24, i25));
                    if (iMax >= 64 && (iMax - iMin2) * 100 >= iMax * 22) {
                        i12++;
                    }
                    int iMax2 = Math.max(diff(i21, iArr[1]), diff(i21, iArr[2]));
                    if (iMax2 >= 6 && iMax2 <= 110) {
                        i14++;
                    } else if (iMax2 > 110) {
                        i13++;
                    }
                    int i26 = (((i23 * 77) + (i24 * 150)) + (i25 * 29)) >> 8;
                    if (i26 < 64) {
                        i15++;
                    } else {
                        if (i26 > 200 && iMax - iMin2 < 24) {
                        }
                        i11 = i22;
                    }
                    i16++;
                    i11 = i22;
                }
                i17 = i19 + 1;
                iArr2 = iArr3;
                i10 = i20;
                f = 14.0f;
            }
            i10++;
            i4 = i11;
            i5 = i12;
            i6 = i13;
            i7 = i14;
            i8 = i15;
            i9 = i16;
        }
        int[] iArr4 = iArr2;
        if (i4 == 0) {
            return -5;
        }
        int i27 = i4;
        int i28 = 0;
        int firstRemoved = 0;
        int i29 = 0;
        while (i29 < 2 && i27 > 0) {
            int[] iArr5 = work.cnt;
            int[] iArr6 = work.sr;
            int[] iArr7 = work.sg;
            int[] iArr8 = work.sb;
            Arrays.fill(iArr5, 0);
            Arrays.fill(iArr6, 0);
            Arrays.fill(iArr7, 0);
            Arrays.fill(iArr8, 0);
            int i30 = 0;
            while (i30 < i27) {
                int i31 = i9;
                int i32 = iArr4[i30];
                int i33 = i8;
                int i34 = (i32 >> 16) & 255;
                int i35 = i5;
                int i36 = (i32 >> 8) & 255;
                int i37 = i32 & 255;
                int i38 = ((i34 >> 5) << 6) | ((i36 >> 5) << 3) | (i37 >> 5);
                iArr5[i38] = iArr5[i38] + 1;
                iArr6[i38] = iArr6[i38] + i34;
                iArr7[i38] = iArr7[i38] + i36;
                iArr8[i38] = iArr8[i38] + i37;
                i30++;
                i9 = i31;
                i8 = i33;
                i5 = i35;
            }
            int i39 = i5;
            int i40 = i8;
            int i41 = i9;
            int i42 = -1;
            for (int i43 = 0; i43 < i27; i43++) {
                int i44 = iArr4[i43];
                int i45 = ((i44 & 255) >> 5) | ((((i44 >> 16) & 255) >> 5) << 6) | ((((i44 >> 8) & 255) >> 5) << 3);
                if (i42 < 0 || iArr5[i45] > iArr5[i42]) {
                    i42 = i45;
                }
            }
            int i46 = iArr6[i42];
            int i47 = iArr5[i42];
            int i48 = i46 / i47;
            int i49 = iArr7[i42] / i47;
            int i50 = iArr8[i42] / i47;
            int i51 = 0;
            for (int i52 = 0; i52 < i27; i52++) {
                int i53 = iArr4[i52];
                if (Math.abs(((i53 >> 16) & 255) - i48) + Math.abs(((i53 >> 8) & 255) - i49) + Math.abs((i53 & 255) - i50) > 48) {
                    iArr4[i51] = i53;
                    i51++;
                }
            }
            i28 += i27 - i51;
            if (i29 == 0) {
                firstRemoved = i27 - i51;
            }
            i29++;
            i27 = i51;
            i9 = i41;
            i8 = i40;
            i5 = i39;
        }
        int i54 = i5;
        int i55 = i8;
        int i56 = i9;
        int i57 = i4 * 100;
        boolean z = i57 >= 11760;
        for (int i58 = 4; z && i58 < 10; i58++) {
            for (int i59 = 4; i59 < 10; i59++) {
                if (!zArr[(i58 * 14) + i59]) {
                    z = false;
                }
            }
        }
        int i60 = 0;
        while (z) {
            if (i60 >= 14) {
                break;
            }
            int i61 = 0;
            int i62 = 0;
            boolean z2 = false;
            for (int i63 = 14; i61 < i63; i63 = 14) {
                boolean z3 = zArr[(i60 * 14) + i61];
                if (z3 && !z2) {
                    i62++;
                }
                i61++;
                z2 = z3;
            }
            if (i62 > 1) {
                z = false;
            }
            i60++;
        }
        for (int i64 = 0; z && i64 < 14; i64++) {
            int i65 = 0;
            int i66 = 0;
            boolean z4 = false;
            while (i65 < 14) {
                boolean z5 = zArr[(i65 * 14) + i64];
                if (z5 && !z4) {
                    i66++;
                }
                i65++;
                z4 = z5;
            }
            if (i66 > 1) {
                z = false;
            }
        }
        int[] iArr9 = work.pct;
        iArr9[0] = i57 / 196;
        int i67 = i7 * 100;
        iArr9[1] = i67 / i4;
        int i68 = i6 * 100;
        iArr9[2] = i68 / i4;
        int i69 = i54 * 100;
        iArr9[3] = i69 / i4;
        int i70 = i55 * 100;
        iArr9[4] = i70 / i4;
        int i71 = i28 * 100;
        iArr9[5] = i71 / i4;
        if (i57 < 2352) {
            return -5;
        }
        if (i57 >= 19012 || z) {
            if (i56 * 100 >= i4 * 92 && i70 >= i4 * 20 && i70 <= i4 * 80 && looksLikeCode(bitmap, i, i2)) {
                return 1;
            }
            // Large opaque bitmaps. The sampling above compares neighbouring pixels, which
            // are almost identical in a big smooth photo, so photos on white/plain
            // backgrounds used to be taken for flat UI and inverted.
            if (Math.min(i, i2) >= 200 && Math.max(i, i2) >= 240) {
                long area = ((long) i) * ((long) i2);
                boolean screenLike = area >= 1500000 && Math.max(i, i2) >= Math.min(i, i2) * 19 / 10;
                if (!screenLike) {
                    if (iArr9[4] >= 90) {
                        // dark art: a wide dark banner is a header that UI text sits on, so it
                        // is inverted with the page; any other dark picture stays as it is
                        return Math.max(i, i2) >= Math.min(i, i2) * 2 ? -3 : 2;
                    }
                    if (i4 - firstRemoved >= 2 || iArr9[3] >= 50) {
                        return 2;
                    }
                }
            }
            boolean z6 = i70 >= i4 * 10 && i70 <= i4 * 90;
            if (i67 >= i4 * 2 || i68 >= i4 * 4 || z6) {
                return (i71 < i4 * 90 || i67 >= (i3 = i4 * 12) || i70 >= i3) ? 2 : -2;
            }
            return -1;
        }
        boolean z7 = Math.max(i, i2) < 256;
        if (i70 < i4 * 25 || (!z7 && i67 >= i4 * 8)) {
            return (i67 >= i4 * 12 || i69 >= i4 * 40) ? 3 : -4;
        }
        return -3;
    }

    private static int diff(int i, int i2) {
        if ((i2 >>> 24) < 200) {
            return 0;
        }
        return Math.abs(((i >> 16) & 255) - ((i2 >> 16) & 255)) + Math.abs(((i >> 8) & 255) - ((i2 >> 8) & 255)) + Math.abs((i & 255) - (i2 & 255));
    }

    private static boolean looksLikeCode(Bitmap bitmap, int i, int i2) {
        int i3;
        if (i > 4096) {
            return false;
        }
        int[] iArr = new int[i];
        int[] iArr2 = {(i2 * 3) / 10, i2 / 2, (i2 * 7) / 10};
        for (int i4 = 0; i4 < 3; i4++) {
            bitmap.getPixels(iArr, 0, i, 0, iArr2[i4], i, 1);
            byte b = -1;
            int i5 = 0;
            int i6 = 0;
            int i7 = 0;
            for (int i8 = 0; i8 < i; i8++) {
                int i9 = iArr[i8];
                if ((i9 >>> 24) >= 200) {
                    int i10 = (((((i9 >> 16) & 255) * 77) + (((i9 >> 8) & 255) * 150)) + ((i9 & 255) * 29)) >> 8;
                    byte b2 = i10 < 96 ? (byte) 0 : i10 > 160 ? (byte) 1 : b;
                    if (b2 >= 0) {
                        i5++;
                        if (b2 == 0) {
                            i7++;
                        }
                        if (b >= 0 && b2 != b) {
                            i6++;
                        }
                        b = b2;
                    }
                }
            }
            if (i5 < i / 2 || i6 < 10 || (i3 = i7 * 100) < i5 * 15 || i3 > i5 * 85) {
                return false;
            }
        }
        return true;
    }

    static void install() {
        Class clsFindClass = XposedHelpers.findClass("android.graphics.BaseRecordingCanvas", (ClassLoader) null);
        Set setHookAllMethods = XposedBridge.hookAllMethods(clsFindClass, "drawBitmap", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int[] iArrDrawState;
                int i;
                try {
                    if (Prefs.enabled && (methodHookParam.args[0] instanceof Bitmap) && (i = (iArrDrawState = Roots.drawState())[0]) != 0) {
                        boolean z = i == 2 && Prefs.keepImages;
                        boolean z2 = iArrDrawState[2] != 0 && Prefs.keepImages;
                        if (z || z2) {
                            int length = methodHookParam.args.length - 1;
                            Object obj = methodHookParam.args[length];
                            if (!(obj instanceof Paint) || ((Paint) obj).getColorFilter() == null) {
                                Bitmap bitmap = (Bitmap) methodHookParam.args[0];
                                if (Images.isPhoto(bitmap)) {
                                    if (z) {
                                        if (obj == null) {
                                            methodHookParam.args[length] = Fx.nullPaint;
                                        } else if (obj instanceof Paint) {
                                            ((Paint) obj).setColorFilter(Fx.counterFilter);
                                        }
                                    }
                                    if (z2) {
                                        RectF rectF = (RectF) ((Object[]) Images.SCRATCH.get())[0];
                                        if (Images.bitmapRect(methodHookParam.args, bitmap, rectF)) {
                                            Images.noteSpot(methodHookParam.thisObject, rectF, iArrDrawState[1]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("img.rec.before", th);
                }
            }

            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Images.removeCounterFilter(methodHookParam);
            }
        });
        XC_MethodHook xC_MethodHook = new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Bitmap bitmapBitmapOf;
                try {
                    int[] iArrDrawState = Roots.drawState();
                    int i = iArrDrawState[0];
                    if (i != 0 && Prefs.enabled) {
                        boolean z = i == 2 && Prefs.keepImages;
                        boolean z2 = iArrDrawState[2] != 0 && Prefs.keepImages;
                        if (z || z2) {
                            Object obj = methodHookParam.args[methodHookParam.args.length - 1];
                            if (obj instanceof Paint) {
                                Paint paint = (Paint) obj;
                                Shader shader = paint.getShader();
                                if ((shader instanceof BitmapShader) && paint.getColorFilter() == null && (bitmapBitmapOf = Images.bitmapOf((BitmapShader) shader)) != null && Images.isPhoto(bitmapBitmapOf)) {
                                    if (z) {
                                        paint.setColorFilter(Fx.counterFilter);
                                    }
                                    if (z2) {
                                        RectF rectF = (RectF) ((Object[]) Images.SCRATCH.get())[0];
                                        if (Images.shapeRect(methodHookParam.args, rectF)) {
                                            Images.noteSpot(methodHookParam.thisObject, rectF, iArrDrawState[1]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("img.shader.before", th);
                }
            }

            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Images.removeCounterFilter(methodHookParam);
            }
        };
        String[] strArr = {"drawRoundRect", "drawCircle", "drawPath"};
        int size = 0;
        for (int i = 0; i < 3; i++) {
            size += XposedBridge.hookAllMethods(clsFindClass, strArr[i], xC_MethodHook).size();
        }
        XposedBridge.hookAllMethods(Bitmap.class, "wrapHardwareBuffer", new XC_MethodHook() {
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object result = methodHookParam.getResult();
                if (result instanceof Bitmap) {
                    synchronized (Images.FRAMES) {
                        Images.FRAMES.put((Bitmap) result, Boolean.TRUE);
                    }
                }
            }
        });
        XC_MethodHook xC_MethodHook2 = new XC_MethodHook() {
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                synchronized (Images.APP_TEXTURES) {
                    Images.APP_TEXTURES.put((SurfaceTexture) methodHookParam.thisObject, Boolean.TRUE);
                }
            }
        };
        try {
            XposedHelpers.findAndHookConstructor(SurfaceTexture.class, new Object[]{Integer.TYPE, xC_MethodHook2});
            XposedHelpers.findAndHookConstructor(SurfaceTexture.class, new Object[]{Integer.TYPE, Boolean.TYPE, xC_MethodHook2});
        } catch (Throwable th) {
            L.e("img.surfaceTexture", th);
        }
        XposedHelpers.findAndHookConstructor(Surface.class, new Object[]{SurfaceTexture.class, new XC_MethodHook() {
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean zContainsKey;
                Object obj = methodHookParam.args[0];
                if (obj == null) {
                    return;
                }
                synchronized (Images.APP_TEXTURES) {
                    zContainsKey = Images.APP_TEXTURES.containsKey((SurfaceTexture) obj);
                }
                if (zContainsKey) {
                    synchronized (Images.TEXTURE_SURFACES) {
                        Images.TEXTURE_SURFACES.put((Surface) methodHookParam.thisObject, Boolean.TRUE);
                    }
                    synchronized (Images.TEXTURE_OF) {
                        Images.TEXTURE_OF.put((Surface) methodHookParam.thisObject, (SurfaceTexture) obj);
                    }
                }
            }
        }});
        XposedBridge.hookAllMethods(Surface.class, "lockCanvas", new XC_MethodHook() {
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean zContainsKey;
                try {
                    Object result = methodHookParam.getResult();
                    if (result instanceof Canvas) {
                        synchronized (Images.TEXTURE_SURFACES) {
                            zContainsKey = Images.TEXTURE_SURFACES.containsKey((Surface) methodHookParam.thisObject);
                        }
                        if (zContainsKey) {
                            synchronized (Images.TEXTURE_CANVASES) {
                                if (Images.TEXTURE_CANVASES.put((Canvas) result, Boolean.TRUE) == null) {
                                    Images.access$1108();
                                }
                            }
                            if (Prefs.debug && L.limited("lockCanvas", 3)) {
                                L.i("bitmap -> texture path in use (Surface.lockCanvas on an app SurfaceTexture)" + L.stack(8));
                            }
                        }
                    }
                } catch (Throwable th2) {
                    L.eLimited("img.lock", th2);
                }
            }
        });
        L.d("images: drawBitmap hooks=" + setHookAllMethods.size() + " shader hooks=" + size + " software hooks=" + XposedBridge.hookAllMethods(Canvas.class, "drawBitmap", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Bitmap bitmapPreInvert;
                try {
                    if (Images.textureCanvasCount != 0 && Prefs.keepImages && Prefs.enabled && (methodHookParam.args[0] instanceof Bitmap) && Images.isTextureCanvas(methodHookParam.thisObject)) {
                        Bitmap bitmap = (Bitmap) methodHookParam.args[0];
                        if (!Fx.flutterLinear) {
                            Images.addCounterFilter(methodHookParam, bitmap);
                        } else if (Images.isPhoto(bitmap) && (bitmapPreInvert = Fx.preInvert(bitmap)) != null) {
                            methodHookParam.args[0] = bitmapPreInvert;
                        }
                        if (Prefs.debug && L.limited("texture.draw", 3)) {
                            L.i("texture image " + bitmap.getWidth() + "x" + bitmap.getHeight() + " photo=" + Images.isPhoto(bitmap));
                        }
                    }
                } catch (Throwable th2) {
                    L.eLimited("img.sw.before", th2);
                }
            }

            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Images.textureCanvasCount != 0) {
                    Images.removeCounterFilter(methodHookParam);
                }
            }
        }).size());
    }

    private static boolean isTextureCanvas(Object obj) {
        boolean zContainsKey;
        if (textureCanvasCount == 0) {
            return false;
        }
        WeakHashMap<Canvas, Boolean> weakHashMap = TEXTURE_CANVASES;
        synchronized (weakHashMap) {
            zContainsKey = weakHashMap.containsKey((Canvas) obj);
        }
        return zContainsKey;
    }

    private static void addCounterFilter(XC_MethodHook.MethodHookParam methodHookParam, Bitmap bitmap) {
        if (isPhoto(bitmap)) {
            int length = methodHookParam.args.length - 1;
            Object obj = methodHookParam.args[length];
            if (obj == null) {
                methodHookParam.args[length] = Fx.nullPaint;
            } else if (obj instanceof Paint) {
                Paint paint = (Paint) obj;
                if (paint.getColorFilter() == null) {
                    paint.setColorFilter(Fx.counterFilter);
                }
            }
        }
    }

    private static void removeCounterFilter(XC_MethodHook.MethodHookParam methodHookParam) {
        try {
            Object obj = methodHookParam.args[methodHookParam.args.length - 1];
            if (!(obj instanceof Paint) || obj == Fx.nullPaint) {
                return;
            }
            Paint paint = (Paint) obj;
            if (paint.getColorFilter() == Fx.counterFilter) {
                paint.setColorFilter(null);
            }
        } catch (Throwable th) {
            L.eLimited("img.after", th);
        }
    }

    private static boolean bitmapRect(Object[] objArr, Bitmap bitmap, RectF rectF) {
        if (objArr.length == 4) {
            Object obj = objArr[1];
            if ((obj instanceof Float) && (objArr[2] instanceof Float)) {
                float fFloatValue = ((Float) obj).floatValue();
                float fFloatValue2 = ((Float) objArr[2]).floatValue();
                rectF.set(fFloatValue, fFloatValue2, bitmap.getWidth() + fFloatValue, bitmap.getHeight() + fFloatValue2);
                return true;
            }
        }
        if (objArr.length == 3 && (objArr[1] instanceof Matrix)) {
            rectF.set(0.0f, 0.0f, bitmap.getWidth(), bitmap.getHeight());
            ((Matrix) objArr[1]).mapRect(rectF);
            return true;
        }
        if (objArr.length == 4) {
            Object obj2 = objArr[2];
            if (obj2 instanceof Rect) {
                rectF.set((Rect) obj2);
                return true;
            }
        }
        if (objArr.length != 4) {
            return false;
        }
        Object obj3 = objArr[2];
        if (!(obj3 instanceof RectF)) {
            return false;
        }
        rectF.set((RectF) obj3);
        return true;
    }

    private static boolean shapeRect(Object[] objArr, RectF rectF) {
        if (objArr.length >= 2) {
            Object obj = objArr[0];
            if (obj instanceof RectF) {
                rectF.set((RectF) obj);
                return true;
            }
        }
        if (objArr.length >= 2) {
            Object obj2 = objArr[0];
            if (obj2 instanceof Path) {
                ((Path) obj2).computeBounds(rectF, true);
                return true;
            }
        }
        if (objArr.length == 7) {
            Object obj3 = objArr[0];
            if ((obj3 instanceof Float) && (objArr[1] instanceof Float) && (objArr[2] instanceof Float) && (objArr[3] instanceof Float)) {
                rectF.set(((Float) obj3).floatValue(), ((Float) objArr[1]).floatValue(), ((Float) objArr[2]).floatValue(), ((Float) objArr[3]).floatValue());
                return true;
            }
        }
        if (objArr.length == 4) {
            Object obj4 = objArr[0];
            if ((obj4 instanceof Float) && (objArr[1] instanceof Float) && (objArr[2] instanceof Float)) {
                float fFloatValue = ((Float) obj4).floatValue();
                float fFloatValue2 = ((Float) objArr[1]).floatValue();
                float fFloatValue3 = ((Float) objArr[2]).floatValue();
                rectF.set(fFloatValue - fFloatValue3, fFloatValue2 - fFloatValue3, fFloatValue + fFloatValue3, fFloatValue2 + fFloatValue3);
                return true;
            }
        }
        return false;
    }

    private static View viewBeingRecorded(Object obj) {
        Object obj2;
        Object obj3;
        try {
            if (!spotFieldsLooked) {
                spotFieldsLooked = true;
                Field declaredField = XposedHelpers.findClass("android.graphics.RecordingCanvas", (ClassLoader) null).getDeclaredField("mNode");
                declaredField.setAccessible(true);
                Field declaredField2 = RenderNode.class.getDeclaredField("mAnimationHost");
                declaredField2.setAccessible(true);
                hostField = declaredField2;
                nodeField = declaredField;
            }
            Field field = nodeField;
            Field field2 = hostField;
            if (field != null && field2 != null && field.getDeclaringClass().isInstance(obj) && (obj2 = field.get(obj)) != null && (obj3 = field2.get(obj2)) != null && "android.view.ViewAnimationHostBridge".equals(obj3.getClass().getName())) {
                Field declaredField3 = hostViewField;
                if (declaredField3 == null) {
                    declaredField3 = obj3.getClass().getDeclaredField("mView");
                    declaredField3.setAccessible(true);
                    hostViewField = declaredField3;
                }
                Object obj4 = declaredField3.get(obj3);
                if (obj4 instanceof View) {
                    return (View) obj4;
                }
                return null;
            }
            return null;
        } catch (Throwable th) {
            L.eLimited("img.viewOf", th);
            return null;
        }
    }

    private static void noteSpot(Object obj, RectF rectF, int i) {
        try {
            View viewViewBeingRecorded = viewBeingRecorded(obj);
            if (viewViewBeingRecorded == null) {
                return;
            }
            Matrix matrix = (Matrix) SCRATCH.get()[1];
            ((Canvas) obj).getMatrix(matrix);
            matrix.mapRect(rectF);
            WeakHashMap<View, Spots> weakHashMap = SPOTS;
            synchronized (weakHashMap) {
                Spots spots = weakHashMap.get(viewViewBeingRecorded);
                if (spots == null) {
                    spots = new Spots();
                    weakHashMap.put(viewViewBeingRecorded, spots);
                }
                if (spots.frame != i) {
                    spots.frame = i;
                    spots.n = 0;
                }
                if (spots.n < 6) {
                    int i2 = spots.n;
                    spots.n = i2 + 1;
                    int i3 = i2 * 4;
                    spots.r[i3] = rectF.left;
                    spots.r[i3 + 1] = rectF.top;
                    spots.r[i3 + 2] = rectF.right;
                    spots.r[i3 + 3] = rectF.bottom;
                }
            }
        } catch (Throwable th) {
            L.eLimited("img.spot", th);
        }
    }

    static boolean markSpots(View root, boolean[] mask, int cols, int rows) {
        ArrayList<View> views;
        synchronized (SPOTS) {
            if (SPOTS.isEmpty()) {
                return false;
            }
            views = new ArrayList<>(SPOTS.keySet());
        }
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        Rect rect = new Rect();
        int[] loc = new int[2];
        float[] r = new float[MAX_SPOTS * 4];
        boolean marked = false;
        for (View view : views) {
            if (view == null) {
                continue;
            }
            try {
                if (!view.isAttachedToWindow() || !view.isShown() || view.getRootView() != root) {
                    continue;
                }
                if ((view instanceof ImageView) && ((ImageView) view).getDrawable() == null) {
                    continue;
                }
                int n;
                synchronized (SPOTS) {
                    Spots spots = SPOTS.get(view);
                    if (spots == null || spots.n == 0) {
                        continue;
                    }
                    n = spots.n;
                    System.arraycopy(spots.r, 0, r, 0, n * 4);
                }
                if (!view.getGlobalVisibleRect(rect)) {
                    continue;
                }
                view.getLocationInWindow(loc);
                for (int k = 0; k < n; k++) {
                    int b = k * 4;
                    marked |= markRect(mask, cols, rows, width, height,
                            Math.max(loc[0] + r[b], rect.left), Math.max(loc[1] + r[b + 1], rect.top),
                            Math.min(loc[0] + r[b + 2], rect.right), Math.min(loc[1] + r[b + 3], rect.bottom));
                }
            } catch (Throwable ignored) {
            }
        }
        return marked;
    }

    static boolean markRect(boolean[] zArr, int i, int i2, int i3, int i4, float f, float f2, float f3, float f4) {
        if (f3 - f >= 1.0f && f4 - f2 >= 1.0f) {
            float f5 = i;
            float f6 = i3;
            int iMax = Math.max(0, (int) Math.ceil(((f * f5) / f6) - 0.5f));
            int iMin = Math.min(i - 1, (int) Math.floor(((f3 * f5) / f6) - 0.5f));
            float f7 = i2;
            float f8 = i4;
            int iMax2 = Math.max(0, (int) Math.ceil(((f2 * f7) / f8) - 0.5f));
            int iMin2 = Math.min(i2 - 1, (int) Math.floor(((f4 * f7) / f8) - 0.5f));
            if (iMax <= iMin && iMax2 <= iMin2) {
                while (iMax2 <= iMin2) {
                    for (int i5 = iMax; i5 <= iMin; i5++) {
                        zArr[(iMax2 * i) + i5] = true;
                    }
                    iMax2++;
                }
                return true;
            }
        }
        return false;
    }

    private static Bitmap bitmapOf(BitmapShader bitmapShader) {
        try {
            if (!shaderFieldLooked) {
                shaderFieldLooked = true;
                Field declaredField = BitmapShader.class.getDeclaredField("mBitmap");
                declaredField.setAccessible(true);
                shaderBitmapField = declaredField;
            }
            Field field = shaderBitmapField;
            if (field == null) {
                return null;
            }
            return (Bitmap) field.get(bitmapShader);
        } catch (Throwable unused) {
            return null;
        }
    }

    static void tryPowerImage(ClassLoader classLoader) {
        if (powerImageDone || powerImageTries >= 40 || !Prefs.keepImages) {
            return;
        }
        powerImageTries++;
        try {
            Class clsFindClassIfExists = XposedHelpers.findClassIfExists("com.taobao.power_image.request.PowerImageExternalRequest", classLoader);
            if (clsFindClassIfExists == null) {
                return;
            }
            powerImageDone = true;
            L.i("PowerImage found: getBitmapPixelsPtr hooks=" + XposedBridge.hookAllMethods(clsFindClassIfExists, "getBitmapPixelsPtr", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Bitmap bitmapPreInvert;
                    try {
                        if (Prefs.keepImages && Prefs.enabled) {
                            for (int i = 0; i < methodHookParam.args.length; i++) {
                                if (methodHookParam.args[i] instanceof Bitmap) {
                                    Bitmap bitmap = (Bitmap) methodHookParam.args[i];
                                    if (!Fx.isPreInverted(bitmap) && Images.isPhoto(bitmap) && bitmap.getConfig() == Bitmap.Config.ARGB_8888 && bitmap.isPremultiplied() && (bitmapPreInvert = Fx.preInvert(bitmap)) != null) {
                                        if (bitmapPreInvert.getWidth() == bitmap.getWidth() && bitmapPreInvert.getHeight() == bitmap.getHeight() && bitmapPreInvert.getRowBytes() == bitmap.getRowBytes() && bitmapPreInvert.getByteCount() == bitmap.getByteCount()) {
                                            synchronized (Images.HANDED_OVER) {
                                                Images.HANDED_OVER.put(bitmap, bitmapPreInvert);
                                            }
                                            methodHookParam.args[i] = bitmapPreInvert;
                                            if (Prefs.debug && L.limited("pi.swap", 3)) {
                                                L.i("PowerImage: pre-inverted a " + bitmap.getWidth() + "x" + bitmap.getHeight() + " photo");
                                                return;
                                            }
                                            return;
                                        }
                                        bitmapPreInvert.recycle();
                                        return;
                                    }
                                    return;
                                }
                            }
                        }
                    } catch (Throwable th) {
                        L.eLimited("pi.get", th);
                    }
                }
            }).size() + " releaseBitmapPixels hooks=" + XposedBridge.hookAllMethods(clsFindClassIfExists, "releaseBitmapPixels", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Bitmap bitmap;
                    for (int i = 0; i < methodHookParam.args.length; i++) {
                        try {
                            if (methodHookParam.args[i] instanceof Bitmap) {
                                synchronized (Images.HANDED_OVER) {
                                    bitmap = (Bitmap) Images.HANDED_OVER.remove((Bitmap) methodHookParam.args[i]);
                                }
                                if (bitmap != null) {
                                    methodHookParam.args[i] = bitmap;
                                    return;
                                }
                                return;
                            }
                        } catch (Throwable th) {
                            L.eLimited("pi.release", th);
                            return;
                        }
                    }
                }
            }).size());
        } catch (Throwable th) {
            L.e("powerImage", th);
        }
    }
}
