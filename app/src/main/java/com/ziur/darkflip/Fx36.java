package com.ziur.darkflip;

import android.graphics.ColorFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class Fx36 {
    static final String AGSL = "uniform float uS;\nuniform float uO;\nconst vec3 W = vec3(0.27, 0.53, 0.20);\nvec4 main(vec4 color) {\n    float a = color.a;\n    if (a <= 0.0) {\n        return vec4(0.0);\n    }\n    vec3 c = clamp(color.rgb / a, 0.0, 1.0);\n    float y = dot(c, W);\n    vec3 d = c - vec3(y);\n    float t = 100000.0;\n    float u = 100000.0;\n    if (d.r > 0.0001) { t = min(t, (1.0 - y) / d.r); u = min(u, y / d.r); }\n    if (d.r < -0.0001) { t = min(t, y / -d.r); u = min(u, (1.0 - y) / -d.r); }\n    if (d.g > 0.0001) { t = min(t, (1.0 - y) / d.g); u = min(u, y / d.g); }\n    if (d.g < -0.0001) { t = min(t, y / -d.g); u = min(u, (1.0 - y) / -d.g); }\n    if (d.b > 0.0001) { t = min(t, (1.0 - y) / d.b); u = min(u, y / d.b); }\n    if (d.b < -0.0001) { t = min(t, y / -d.b); u = min(u, (1.0 - y) / -d.b); }\n    float k = (t > 90000.0) ? 0.0 : u / max(t, 0.0001);\n    vec3 r = clamp(vec3(1.0 - y) + k * d, 0.0, 1.0);\n    r = r * uS + uO;\n    return vec4(r * a, a);\n}\n";

    private Fx36() {
    }

    /**
     * {@code new RuntimeColorFilter(AGSL)} with the two uniforms set. RuntimeColorFilter
     * only exists on Android 16 (API 36) and later, so it is created reflectively.
     */
    static ColorFilter create(float scale, float offset) {
        try {
            Class<?> cls = Class.forName("android.graphics.RuntimeColorFilter");
            ColorFilter filter = (ColorFilter) cls.getConstructor(String.class).newInstance(AGSL);
            Method setFloatUniform = cls.getMethod("setFloatUniform", String.class, float.class);
            setFloatUniform.invoke(filter, "uS", scale);
            setFloatUniform.invoke(filter, "uO", offset);
            return filter;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("RuntimeColorFilter is not available", e);
        }
    }
}
