package com.ziur.darkflip;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class Diag {
    private static final String[] NAME_KEYS = {"power_image", "powerimage", "flutterimage", "flutter/embedding/android/", "flutterboost", "flutter_boost", "pexode", "externaltexture", "textureregistry"};
    private static boolean started;
    private static int treeDumps;

    private Diag() {
    }

    static void installProbes() {
        if (Prefs.debug) {
            Class clsFindClassIfExists = XposedHelpers.findClassIfExists("android.opengl.GLUtils", (ClassLoader) null);
            if (clsFindClassIfExists != null) {
                XC_MethodHook xC_MethodHookProbe = probe("GLUtils texture upload");
                XposedBridge.hookAllMethods(clsFindClassIfExists, "texImage2D", xC_MethodHookProbe);
                XposedBridge.hookAllMethods(clsFindClassIfExists, "texSubImage2D", xC_MethodHookProbe);
            }
            XposedBridge.hookAllMethods(Bitmap.class, "copyPixelsToBuffer", probe("Bitmap.copyPixelsToBuffer"));
        }
    }

    private static XC_MethodHook probe(final String str) {
        return new XC_MethodHook() {
            private int calls;
            private int hits;

            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (this.hits < 3 && this.calls < 80 && Prefs.debug) {
                        this.calls++;
                        Bitmap bitmap = methodHookParam.thisObject instanceof Bitmap ? (Bitmap) methodHookParam.thisObject : null;
                        for (int i = 0; bitmap == null && i < methodHookParam.args.length; i++) {
                            if (methodHookParam.args[i] instanceof Bitmap) {
                                bitmap = (Bitmap) methodHookParam.args[i];
                            }
                        }
                        if (bitmap != null && Images.isPhoto(bitmap)) {
                            this.hits++;
                            L.i("probe: " + str + " with a " + bitmap.getWidth() + "x" + bitmap.getHeight() + " photo" + L.stack(7));
                        }
                    }
                } catch (Throwable th) {
                    L.eLimited("probe", th);
                }
            }
        };
    }

    static void scheduleTreeDump(Activity activity) {
        if (!Prefs.debug || treeDumps >= 6) {
            return;
        }
        final String name = activity.getClass().getName();
        if (L.limited("tree:" + name, 1)) {
            treeDumps++;
            try {
                final View decorView = activity.getWindow().getDecorView();
                decorView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (decorView.isAttachedToWindow()) {
                                StringBuilder sb = new StringBuilder("view tree of " + name);
                                int[] iArr = {110};
                                Diag.dump(decorView, 0, sb, iArr);
                                if (iArr[0] <= 0) {
                                    sb.append("\n  ... (truncated)");
                                }
                                L.i(sb.toString());
                            }
                        } catch (Throwable th) {
                            L.eLimited("tree dump", th);
                        }
                    }
                }, 2500L);
            } catch (Throwable th) {
                L.eLimited("tree dump schedule", th);
            }
        }
    }

    private static void dump(View view, int i, StringBuilder sb, int[] iArr) {
        Drawable drawable;
        if (iArr[0] <= 0 || view.getVisibility() != 0) {
            return;
        }
        iArr[0] = iArr[0] - 1;
        sb.append('\n');
        for (int i2 = 0; i2 <= i; i2++) {
            sb.append("  ");
        }
        String name = view.getClass().getName();
        if (name.startsWith("android.widget.") || name.startsWith("android.view.")) {
            name = name.substring(name.lastIndexOf(46) + 1);
        }
        sb.append(name);
        sb.append(' ');
        sb.append(view.getWidth());
        sb.append('x');
        sb.append(view.getHeight());
        sb.append('@');
        sb.append(view.getLeft());
        sb.append(',');
        sb.append(view.getTop());
        Drawable background = view.getBackground();
        if (background != null) {
            sb.append(" bg=");
            sb.append(describe(background));
        }
        if ((view instanceof ImageView) && (drawable = ((ImageView) view).getDrawable()) != null) {
            sb.append(" img=");
            sb.append(describe(drawable));
        }
        if (view.getLayerType() != 0) {
            sb.append(" layer=");
            sb.append(view.getLayerType());
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            int childCount = viewGroup.getChildCount();
            if (i >= 9) {
                if (childCount > 0) {
                    sb.append(" (+");
                    sb.append(childCount);
                    sb.append(" children)");
                    return;
                }
                return;
            }
            for (int i3 = 0; i3 < childCount && iArr[0] > 0; i3++) {
                View childAt = viewGroup.getChildAt(i3);
                if (childAt != null) {
                    dump(childAt, i + 1, sb, iArr);
                }
            }
        }
    }

    private static String describe(Drawable drawable) {
        try {
            if (drawable instanceof ColorDrawable) {
                return String.format("#%08X", Integer.valueOf(((ColorDrawable) drawable).getColor()));
            }
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                return "bitmap" + (bitmap == null ? "" : "(" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
            }
        } catch (Throwable ignored) {
            // fall back to the class name
        }
        String name = drawable.getClass().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    static synchronized void maybeScan(Activity activity) {
        if (started || !Prefs.deepScan) {
            return;
        }
        started = true;
        try {
            final ApplicationInfo applicationInfo = activity.getApplicationInfo();
            long longVersionCode;
            try {
                longVersionCode = activity.getPackageManager().getPackageInfo(applicationInfo.packageName, 0).getLongVersionCode();
            } catch (Throwable unused) {
                longVersionCode = 0;
            }
            final File file = new File(activity.getCacheDir(), "darkflip_scan_" + longVersionCode + "_" + C.VERSION);
            if (file.exists()) {
                L.i("deep scan: already done for this app version (delete " + file.getName() + " in the app cache, or clear its cache, to run it again)");
                return;
            }
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Diag.scan(applicationInfo);
                        file.createNewFile();
                    } catch (Throwable th2) {
                        L.e("deep scan", th2);
                    }
                }
            }, "darkflip-scan");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        } catch (Throwable th) {
            L.e("deep scan start", th);
        }
    }

    private static void scan(ApplicationInfo applicationInfo) throws Exception {
        ArrayList arrayList = new ArrayList();
        if (applicationInfo.sourceDir != null) {
            arrayList.add(applicationInfo.sourceDir);
        }
        int i = 0;
        if (applicationInfo.splitSourceDirs != null) {
            for (String str : applicationInfo.splitSourceDirs) {
                arrayList.add(str);
            }
        }
        int[] iArr = {220, 120};
        long jCurrentTimeMillis = System.currentTimeMillis();
        L.i("deep scan: start, " + arrayList.size() + " apk(s)");
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            ZipFile zipFile = new ZipFile((String) it.next());
            try {
                Enumeration<? extends ZipEntry> enumerationEntries = zipFile.entries();
                while (enumerationEntries.hasMoreElements()) {
                    ZipEntry zipEntryNextElement = enumerationEntries.nextElement();
                    String name = zipEntryNextElement.getName();
                    if (name.startsWith("classes") && name.endsWith(".dex") && name.indexOf(47) < 0 && zipEntryNextElement.getSize() <= 67108864) {
                        i++;
                        try {
                            scanDex(name, ByteBuffer.wrap(readAll(zipFile.getInputStream(zipEntryNextElement), (int) Math.max(zipEntryNextElement.getSize(), 1024L))).order(ByteOrder.LITTLE_ENDIAN), iArr);
                        } catch (Throwable th) {
                            L.i("deep scan: " + name + " could not be parsed: " + th);
                        }
                    }
                }
            } finally {
                zipFile.close();
            }
        }
        L.i("deep scan: done, " + i + " dex file(s) in " + (System.currentTimeMillis() - jCurrentTimeMillis) + " ms");
    }

    private static byte[] readAll(InputStream inputStream, int i) throws Exception {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(i);
            byte[] bArr = new byte[65536];
            while (true) {
                int i2 = inputStream.read(bArr);
                if (i2 <= 0) {
                    return byteArrayOutputStream.toByteArray();
                }
                byteArrayOutputStream.write(bArr, 0, i2);
            }
        } finally {
            inputStream.close();
        }
    }

    /**
     * Walks one dex file and logs (a) classes whose name contains one of NAME_KEYS and
     * (b) native methods that take a Bitmap. {@code budget[0]} and {@code budget[1]} cap
     * how many of each are logged across all dex files.
     */
    private static void scanDex(String dexName, ByteBuffer dex, int[] budget) {
        if (dex.limit() < 112 || dex.get(0) != 'd' || dex.get(1) != 'e' || dex.get(2) != 'x') {
            return;
        }
        int stringIdsSize = dex.getInt(56);
        int stringIdsOff = dex.getInt(60);
        int typeIdsSize = dex.getInt(64);
        int typeIdsOff = dex.getInt(68);
        int protoIdsOff = dex.getInt(76);
        int methodIdsSize = dex.getInt(88);
        int methodIdsOff = dex.getInt(92);
        int classDefsSize = dex.getInt(96);
        int classDefsOff = dex.getInt(100);

        int bitmapType = -1;
        for (int t = 0; t < typeIdsSize; t++) {
            if (stringEquals(dex, stringIdsOff, stringIdsSize, dex.getInt(typeIdsOff + t * 4), "Landroid/graphics/Bitmap;")) {
                bitmapType = t;
                break;
            }
        }

        for (int c = 0; c < classDefsSize; c++) {
            int def = classDefsOff + c * 32;
            int classIdx = dex.getInt(def);
            int classFlags = dex.getInt(def + 4);
            int classDataOff = dex.getInt(def + 24);
            String className = null;

            if (budget[0] > 0) {
                className = typeName(dex, stringIdsOff, stringIdsSize, typeIdsOff, typeIdsSize, classIdx);
                if (Util.containsAny(className.toLowerCase(), NAME_KEYS)) {
                    budget[0]--;
                    int superIdx = dex.getInt(def + 8);
                    String superName = (superIdx < 0 || superIdx >= typeIdsSize) ? "-"
                            : typeName(dex, stringIdsOff, stringIdsSize, typeIdsOff, typeIdsSize, superIdx);
                    L.i("scan class " + className + " extends " + superName
                            + ((classFlags & 0x4000) != 0 ? " [enum]" : "")
                            + ((classFlags & 0x200) != 0 ? " [interface]" : ""));
                }
            }

            if (bitmapType < 0 || budget[1] <= 0 || classDataOff == 0) {
                continue;
            }
            int[] pos = {classDataOff};
            int staticFields = uleb(dex, pos);
            int instanceFields = uleb(dex, pos);
            int directMethods = uleb(dex, pos);
            int virtualMethods = uleb(dex, pos);
            for (int f = 0; f < staticFields + instanceFields; f++) {
                uleb(dex, pos);
                uleb(dex, pos);
            }
            for (int pass = 0; pass < 2; pass++) {
                int count = pass == 0 ? directMethods : virtualMethods;
                int methodIdx = 0;
                for (int m = 0; m < count; m++) {
                    methodIdx += uleb(dex, pos);
                    int accessFlags = uleb(dex, pos);
                    uleb(dex, pos); // code_off
                    if ((accessFlags & 0x100) == 0 || methodIdx < 0 || methodIdx >= methodIdsSize) {
                        continue;
                    }
                    int methodId = methodIdsOff + methodIdx * 8;
                    int protoIdx = dex.getShort(methodId + 2) & 0xFFFF;
                    int nameIdx = dex.getInt(methodId + 4);
                    int proto = protoIdsOff + protoIdx * 12;
                    int returnType = dex.getInt(proto + 4);
                    int params = dex.getInt(proto + 8);
                    if (params == 0) {
                        continue;
                    }
                    int paramCount = dex.getInt(params);
                    boolean takesBitmap = false;
                    for (int p = 0; p < paramCount && p < 32; p++) {
                        if ((dex.getShort(params + 4 + p * 2) & 0xFFFF) == bitmapType) {
                            takesBitmap = true;
                        }
                    }
                    if (!takesBitmap) {
                        continue;
                    }
                    if (className == null) {
                        className = typeName(dex, stringIdsOff, stringIdsSize, typeIdsOff, typeIdsSize, classIdx);
                    }
                    StringBuilder sig = new StringBuilder("(");
                    for (int p = 0; p < paramCount && p < 32; p++) {
                        sig.append(typeName(dex, stringIdsOff, stringIdsSize, typeIdsOff, typeIdsSize, dex.getShort(params + 4 + p * 2) & 0xFFFF));
                    }
                    sig.append(')').append(typeName(dex, stringIdsOff, stringIdsSize, typeIdsOff, typeIdsSize, returnType));
                    budget[1]--;
                    L.i("scan native " + className + "->" + string(dex, stringIdsOff, stringIdsSize, nameIdx) + sig
                            + ((accessFlags & 0x8) != 0 ? " [static]" : ""));
                    if (budget[1] <= 0) {
                        break;
                    }
                }
            }
        }
    }

    private static int uleb(ByteBuffer byteBuffer, int[] iArr) {
        int i = 0;
        int i2 = 0;
        do {
            int i3 = iArr[0];
            iArr[0] = i3 + 1;
            byte b = byteBuffer.get(i3);
            i |= (b & 127) << i2;
            i2 += 7;
            if ((b & 128) == 0) {
                break;
            }
        } while (i2 < 35);
        return i;
    }

    private static String typeName(ByteBuffer byteBuffer, int i, int i2, int i3, int i4, int i5) {
        if (i5 < 0 || i5 >= i4) {
            return "?";
        }
        return string(byteBuffer, i, i2, byteBuffer.getInt(i3 + (i5 * 4)));
    }

    private static String string(ByteBuffer byteBuffer, int i, int i2, int i3) {
        if (i3 < 0 || i3 >= i2) {
            return "?";
        }
        int[] iArr = {byteBuffer.getInt(i + (i3 * 4))};
        uleb(byteBuffer, iArr);
        int i4 = iArr[0];
        int i5 = i4;
        while (i5 < byteBuffer.limit() && byteBuffer.get(i5) != 0 && i5 - i4 < 512) {
            i5++;
        }
        int i6 = i5 - i4;
        byte[] bArr = new byte[i6];
        for (int i7 = 0; i7 < i6; i7++) {
            bArr[i7] = byteBuffer.get(i4 + i7);
        }
        return new String(bArr, StandardCharsets.ISO_8859_1);
    }

    private static boolean stringEquals(ByteBuffer byteBuffer, int i, int i2, int i3, String str) {
        if (i3 < 0 || i3 >= i2) {
            return false;
        }
        int[] iArr = {byteBuffer.getInt(i + (i3 * 4))};
        if (uleb(byteBuffer, iArr) != str.length()) {
            return false;
        }
        int i4 = iArr[0];
        for (int i5 = 0; i5 < str.length(); i5++) {
            if (byteBuffer.get(i4 + i5) != ((byte) str.charAt(i5))) {
                return false;
            }
        }
        return byteBuffer.get(i4 + str.length()) == 0;
    }
}
