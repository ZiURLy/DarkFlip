package com.ziur.darkflip;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorSpace;
import android.graphics.Paint;
import android.os.Build;
import java.util.WeakHashMap;

final class Fx {
    static final float LA = 0.72f;
    static final float LC = 0.05f;
    static final float LWB = 0.2f;
    static final float LWG = 0.55f;
    static final float LWR = 0.25f;
    static volatile float O = 0.0f;
    static volatile float S = 0.92f;
    static final float WB = 0.2f;
    static final float WG = 0.53f;
    static final float WR = 0.27f;
    static volatile ColorFilter counterFilter;
    static volatile Paint counterPaint;
    static volatile boolean exact;
    static volatile ColorMatrixColorFilter linCounterFilter;
    static volatile Paint nullPaint;
    static volatile ColorFilter rootFilter;
    static volatile Paint rootPaint;
    static final float[] IDENT_M = {1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f};
    static final float[] ZERO_T = {0.0f, 0.0f, 0.0f};
    static final float[] LIN_M = {0.36f, -0.36f, -0.36f, -0.79200006f, -0.07200002f, -0.79200006f, -0.28800002f, -0.28800002f, 0.43200004f};
    static final float[] LIN_T = {0.67f, 0.67f, 0.67f};
    static volatile int gen = 0;
    static volatile boolean flutterLinear = false;
    private static final WeakHashMap<Bitmap, Boolean> PRE = new WeakHashMap<>();

    private Fx() {
    }

    static synchronized void rebuild() {
        if (Prefs.style == 1) {
            S = 0.82f;
            O = 0.07f;
        } else {
            S = 0.92f;
            O = 0.0f;
        }
        float f = S;
        float f2 = O;
        float f3 = (f + f2) * 255.0f;
        float f4 = (-2.0f) * f;
        float f5 = 0.53f * f4;
        float f6 = 0.2f * f4;
        float f7 = f4 * 0.27f;
        boolean z = false;
        ColorFilter colorMatrixColorFilter = new ColorMatrixColorFilter(new float[]{f * 0.45999998f, f5, f6, 0.0f, f3, f7, f * (-0.059999943f), f6, 0.0f, f3, f7, f5, f * 0.6f, 0.0f, f3, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
        float f8 = ((f2 / f) + 1.0f) * 255.0f;
        float f9 = (-1.06f) / f;
        float f10 = (-0.4f) / f;
        float f11 = (-0.54f) / f;
        ColorFilter colorMatrixColorFilter2 = new ColorMatrixColorFilter(new float[]{0.45999998f / f, f9, f10, 0.0f, f8, f11, (-0.059999943f) / f, f10, 0.0f, f8, f11, f9, 0.6f / f, 0.0f, f8, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
        if (Build.VERSION.SDK_INT >= 36 && Prefs.exactColors) {
            try {
                ColorFilter colorFilterCreate = Fx36.create(f, f2);
                colorMatrixColorFilter2 = Fx36.create(1.0f, 0.0f);
                colorMatrixColorFilter = colorFilterCreate;
                z = true;
            } catch (Throwable th) {
                L.e("shader filter unavailable, using the colour matrix", th);
            }
            rootFilter = colorMatrixColorFilter;
            counterFilter = colorMatrixColorFilter2;
            exact = z;
            linCounterFilter = new ColorMatrixColorFilter(new float[]{0.6944444f, -1.5277778f, -0.5555555f, 0.0f, 237.29167f, -0.6944444f, -0.13888891f, -0.5555555f, 0.0f, 237.29167f, -0.6944444f, -1.5277778f, 0.8333333f, 0.0f, 237.29167f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
            Paint paint = new Paint();
            paint.setColorFilter(rootFilter);
            rootPaint = paint;
            Paint paint2 = new Paint();
            paint2.setColorFilter(counterFilter);
            counterPaint = paint2;
            Paint paint3 = new Paint(2);
            paint3.setColorFilter(counterFilter);
            nullPaint = paint3;
            gen++;
        } else {
            rootFilter = colorMatrixColorFilter;
            counterFilter = colorMatrixColorFilter2;
            exact = z;
            linCounterFilter = new ColorMatrixColorFilter(new float[]{0.6944444f, -1.5277778f, -0.5555555f, 0.0f, 237.29167f, -0.6944444f, -0.13888891f, -0.5555555f, 0.0f, 237.29167f, -0.6944444f, -1.5277778f, 0.8333333f, 0.0f, 237.29167f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
            Paint paint4 = new Paint();
            paint4.setColorFilter(rootFilter);
            rootPaint = paint4;
            Paint paint22 = new Paint();
            paint22.setColorFilter(counterFilter);
            counterPaint = paint22;
            Paint paint32 = new Paint(2);
            paint32.setColorFilter(counterFilter);
            nullPaint = paint32;
            gen++;
        }
    }

    static float undoLightness(float f) {
        return 1.0f - ((f - O) / S);
    }

    static boolean isPreInverted(Bitmap bitmap) {
        boolean zContainsKey;
        WeakHashMap<Bitmap, Boolean> weakHashMap = PRE;
        synchronized (weakHashMap) {
            zContainsKey = weakHashMap.containsKey(bitmap);
        }
        return zContainsKey;
    }

    static Bitmap preInvert(Bitmap bitmap) {
        if (bitmap != null) {
            try {
                if (bitmap.isRecycled() || bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    return null;
                }
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                if (width > 0 && height > 0) {
                    long j = ((long) width) * ((long) height);
                    if (j <= 50331648) {
                        if (flutterLinear && j > 6291456) {
                            return null;
                        }
                        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmapCreateBitmap.setDensity(bitmap.getDensity());
                        Paint paint = new Paint();
                        if (flutterLinear) {
                            Bitmap bitmapCreateBitmap2 = Bitmap.createBitmap(width, height, Bitmap.Config.RGBA_F16, true, ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB));
                            bitmapCreateBitmap2.setDensity(bitmap.getDensity());
                            paint.setColorFilter(linCounterFilter);
                            new Canvas(bitmapCreateBitmap2).drawBitmap(bitmap, 0.0f, 0.0f, paint);
                            new Canvas(bitmapCreateBitmap).drawBitmap(bitmapCreateBitmap2, 0.0f, 0.0f, (Paint) null);
                            bitmapCreateBitmap2.recycle();
                        } else {
                            paint.setColorFilter(counterFilter);
                            new Canvas(bitmapCreateBitmap).drawBitmap(bitmap, 0.0f, 0.0f, paint);
                        }
                        bitmapCreateBitmap.setHasAlpha(bitmap.hasAlpha());
                        WeakHashMap<Bitmap, Boolean> weakHashMap = PRE;
                        synchronized (weakHashMap) {
                            weakHashMap.put(bitmapCreateBitmap, Boolean.TRUE);
                        }
                        return bitmapCreateBitmap;
                    }
                }
                return null;
            } catch (Throwable th) {
                L.eLimited("preInvert", th);
            }
        }
        return null;
    }
}
