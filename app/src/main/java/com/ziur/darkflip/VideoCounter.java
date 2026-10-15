package com.ziur.darkflip;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Flutter shows MediaPlayer video through a SurfaceTexture that lives inside the
 * inverted Flutter layer, so the video came out inverted. For those players the
 * frames are routed through a small GL pass that applies the same counter-inversion
 * that photos get, then handed to the original Flutter surface.
 */
final class VideoCounter {
    private static final WeakHashMap<MediaPlayer, Pipe> PIPES = new WeakHashMap<>();

    private static final String VS = "attribute vec4 aPos;\nattribute vec2 aTc;\nuniform mat4 uTm;\nvarying vec2 vTc;\n"
            + "void main() {\n  gl_Position = aPos;\n  vTc = (uTm * vec4(aTc, 0.0, 1.0)).xy;\n}\n";
    private static final String FS = "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
            + "varying vec2 vTc;\nuniform samplerExternalOES uTex;\nconst vec3 W = vec3(0.27, 0.53, 0.20);\n"
            + "void main() {\n"
            + "  vec3 c = clamp(texture2D(uTex, vTc).rgb, 0.0, 1.0);\n"
            + "  float y = dot(c, W);\n  vec3 d = c - vec3(y);\n  float t = 100000.0;\n  float u = 100000.0;\n"
            + "  if (d.r > 0.0001) { t = min(t, (1.0 - y) / d.r); u = min(u, y / d.r); }\n"
            + "  if (d.r < -0.0001) { t = min(t, y / -d.r); u = min(u, (1.0 - y) / -d.r); }\n"
            + "  if (d.g > 0.0001) { t = min(t, (1.0 - y) / d.g); u = min(u, y / d.g); }\n"
            + "  if (d.g < -0.0001) { t = min(t, y / -d.g); u = min(u, (1.0 - y) / -d.g); }\n"
            + "  if (d.b > 0.0001) { t = min(t, (1.0 - y) / d.b); u = min(u, y / d.b); }\n"
            + "  if (d.b < -0.0001) { t = min(t, y / -d.b); u = min(u, (1.0 - y) / -d.b); }\n"
            + "  float k = (t > 90000.0) ? 0.0 : u / max(t, 0.0001);\n"
            + "  gl_FragColor = vec4(clamp(vec3(1.0 - y) + k * d, 0.0, 1.0), 1.0);\n}\n";

    private VideoCounter() {
    }

    static void install() {
        try {
            XposedBridge.hookAllMethods(MediaPlayer.class, "setSurface", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam p) {
                    try {
                        MediaPlayer mp = (MediaPlayer) p.thisObject;
                        drop(mp);
                        Surface s = (Surface) p.args[0];
                        if (s == null || !Prefs.enabled || !Prefs.keepImages || !Fx.exact) {
                            return;
                        }
                        SurfaceTexture st = Images.textureOf(s);
                        if (st == null) {
                            return;
                        }
                        Pipe pipe = new Pipe(mp, s, st);
                        if (pipe.start()) {
                            synchronized (PIPES) {
                                PIPES.put(mp, pipe);
                            }
                            p.args[0] = pipe.inSurface;
                            L.i("video: counter-inverting MediaPlayer video for a Flutter texture");
                        } else {
                            L.i("video: GL pass could not start, leaving the video alone");
                        }
                    } catch (Throwable t) {
                        L.eLimited("video.setSurface", t);
                    }
                }
            });
            XC_MethodHook end = new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam p) {
                    drop((MediaPlayer) p.thisObject);
                }
            };
            XposedBridge.hookAllMethods(MediaPlayer.class, "release", end);
            XposedBridge.hookAllMethods(MediaPlayer.class, "reset", end);
        } catch (Throwable t) {
            L.e("videoCounter", t);
        }
    }

    private static void drop(MediaPlayer mp) {
        Pipe old;
        synchronized (PIPES) {
            old = PIPES.remove(mp);
        }
        if (old != null) {
            old.release();
        }
    }

    private static final class Pipe implements SurfaceTexture.OnFrameAvailableListener {
        private final WeakReference<MediaPlayer> player;
        private final Surface out;
        private final SurfaceTexture outTexture;
        private HandlerThread thread;
        private Handler handler;
        private EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        private EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        private EGLConfig cfg;
        private EGLSurface pbuf = EGL14.EGL_NO_SURFACE;
        private EGLSurface win = EGL14.EGL_NO_SURFACE;
        private int prog;
        private int tex;
        private int aPos;
        private int aTc;
        private int uTm;
        private SurfaceTexture in;
        volatile Surface inSurface;
        private int curW;
        private int curH;
        private volatile boolean dead;
        private final float[] tm = new float[16];
        private FloatBuffer quad;

        Pipe(MediaPlayer mp, Surface out, SurfaceTexture outTexture) {
            this.player = new WeakReference<>(mp);
            this.out = out;
            this.outTexture = outTexture;
        }

        boolean start() {
            thread = new HandlerThread("DarkFlip-video");
            thread.start();
            handler = new Handler(thread.getLooper());
            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] ok = new boolean[1];
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        initGl();
                        ok[0] = true;
                    } catch (Throwable t) {
                        L.e("video.initGl", t);
                    }
                    latch.countDown();
                }
            });
            boolean done = false;
            try {
                done = latch.await(1500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            if (!done || !ok[0]) {
                release();
                return false;
            }
            return true;
        }

        private static int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] st = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0);
            if (st[0] == 0) {
                throw new IllegalStateException("shader: " + GLES20.glGetShaderInfoLog(s));
            }
            return s;
        }

        private void initGl() {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) {
                throw new IllegalStateException("eglInitialize");
            }
            int[] attrs = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] cfgs = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0) || n[0] < 1) {
                throw new IllegalStateException("eglChooseConfig");
            }
            cfg = cfgs[0];
            ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) {
                throw new IllegalStateException("eglCreateContext");
            }
            pbuf = EGL14.eglCreatePbufferSurface(dpy, cfg, new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (!EGL14.eglMakeCurrent(dpy, pbuf, pbuf, ctx)) {
                throw new IllegalStateException("eglMakeCurrent");
            }
            int vs = compile(GLES20.GL_VERTEX_SHADER, VS);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, FS);
            prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vs);
            GLES20.glAttachShader(prog, fs);
            GLES20.glLinkProgram(prog);
            int[] ls = new int[1];
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ls, 0);
            if (ls[0] == 0) {
                throw new IllegalStateException("link: " + GLES20.glGetProgramInfoLog(prog));
            }
            aPos = GLES20.glGetAttribLocation(prog, "aPos");
            aTc = GLES20.glGetAttribLocation(prog, "aTc");
            uTm = GLES20.glGetUniformLocation(prog, "uTm");
            float[] verts = {-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f};
            quad = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quad.put(verts).position(0);
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            tex = t[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            in = new SurfaceTexture(tex);
            in.setOnFrameAvailableListener(this, handler);
            inSurface = new Surface(in);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            if (dead) {
                return;
            }
            try {
                MediaPlayer mp = player.get();
                int w = 0;
                int h = 0;
                if (mp != null) {
                    try {
                        w = mp.getVideoWidth();
                        h = mp.getVideoHeight();
                    } catch (Throwable ignored) {
                    }
                }
                if (w <= 0 || h <= 0) {
                    w = curW;
                    h = curH;
                }
                if (w <= 0 || h <= 0) {
                    EGL14.eglMakeCurrent(dpy, pbuf, pbuf, ctx);
                    in.updateTexImage();
                    return;
                }
                if (w != curW || h != curH || win == EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(dpy, pbuf, pbuf, ctx);
                    if (win != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(dpy, win);
                        win = EGL14.EGL_NO_SURFACE;
                    }
                    outTexture.setDefaultBufferSize(w, h);
                    win = EGL14.eglCreateWindowSurface(dpy, cfg, out, new int[]{EGL14.EGL_NONE}, 0);
                    curW = w;
                    curH = h;
                    if (win == null || win == EGL14.EGL_NO_SURFACE) {
                        win = EGL14.EGL_NO_SURFACE;
                        in.updateTexImage();
                        return;
                    }
                }
                EGL14.eglMakeCurrent(dpy, win, win, ctx);
                in.updateTexImage();
                in.getTransformMatrix(tm);
                GLES20.glViewport(0, 0, w, h);
                GLES20.glUseProgram(prog);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
                GLES20.glUniformMatrix4fv(uTm, 1, false, tm, 0);
                quad.position(0);
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
                GLES20.glEnableVertexAttribArray(aPos);
                quad.position(2);
                GLES20.glVertexAttribPointer(aTc, 2, GLES20.GL_FLOAT, false, 16, quad);
                GLES20.glEnableVertexAttribArray(aTc);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                EGL14.eglSwapBuffers(dpy, win);
            } catch (Throwable t) {
                L.eLimited("video.frame", t);
            }
        }

        void release() {
            dead = true;
            Handler h = handler;
            if (h == null) {
                return;
            }
            h.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (dpy != EGL14.EGL_NO_DISPLAY) {
                            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                            if (win != EGL14.EGL_NO_SURFACE) {
                                EGL14.eglDestroySurface(dpy, win);
                            }
                            if (pbuf != null && pbuf != EGL14.EGL_NO_SURFACE) {
                                EGL14.eglDestroySurface(dpy, pbuf);
                            }
                            if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT) {
                                EGL14.eglDestroyContext(dpy, ctx);
                            }
                            EGL14.eglTerminate(dpy);
                        }
                        if (inSurface != null) {
                            inSurface.release();
                        }
                        if (in != null) {
                            in.release();
                        }
                    } catch (Throwable ignored) {
                    }
                    thread.quitSafely();
                }
            });
        }
    }
}
