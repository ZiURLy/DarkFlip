package com.ziur.darkflip;

import android.media.MediaFormat;
import android.view.Surface;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Diagnostic only: logs where video frames are sent (needs "诊断日志" on). */
final class VideoProbe {
    private VideoProbe() {
    }

    static void install() {
        try {
            XposedBridge.hookAllMethods(android.media.MediaCodec.class, "configure", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam p) {
                    try {
                        if (!Prefs.debug || !L.limited("probe.codec", 12)) {
                            return;
                        }
                        String mime = "?";
                        Surface surface = null;
                        for (Object a : p.args) {
                            if (a instanceof MediaFormat) {
                                mime = String.valueOf(((MediaFormat) a).getString(MediaFormat.KEY_MIME));
                            } else if (a instanceof Surface) {
                                surface = (Surface) a;
                            }
                        }
                        L.i("probe: MediaCodec.configure mime=" + mime + " surface=" + (surface != null) + L.stack(10));
                    } catch (Throwable ignored) {
                    }
                }
            });
            XC_MethodHook surfaceHook = new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam p) {
                    try {
                        if (Prefs.debug && p.args.length > 0 && p.args[0] instanceof Surface && L.limited("probe.setSurface", 12)) {
                            L.i("probe: " + p.method.getDeclaringClass().getSimpleName() + "." + p.method.getName() + " surface" + L.stack(10));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            XposedBridge.hookAllMethods(android.media.MediaPlayer.class, "setSurface", surfaceHook);
            XposedBridge.hookAllMethods(android.media.MediaCodec.class, "setOutputSurface", surfaceHook);
            XposedBridge.hookAllConstructors(android.media.ImageReader.class, new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam p) {
                    try {
                        if (Prefs.debug && L.limited("probe.imgreader", 8)) {
                            L.i("probe: ImageReader created" + L.stack(8));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.hookAllMethods(android.view.SurfaceView.class, "onAttachedToWindow", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam p) {
                    try {
                        if (Prefs.debug && L.limited("probe.sv", 12)) {
                            L.i("probe: SurfaceView attached " + p.thisObject.getClass().getName());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            L.e("probe", t);
        }
    }
}
