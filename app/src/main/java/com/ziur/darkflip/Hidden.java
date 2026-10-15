package com.ziur.darkflip;

import android.content.res.Resources;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hidden framework APIs that DarkFlip needs, reached through reflection so the
 * project builds against the public Android SDK. Each member is looked up once.
 * If a member is missing on some ROM, using it throws the same
 * NoSuchMethodError / NoSuchFieldError a direct call would have thrown.
 */
final class Hidden {
    static final Class<?> VIEW_ROOT_IMPL = findClass("android.view.ViewRootImpl");

    private static final Method VIEW_GET_VIEW_ROOT_IMPL = findMethod(View.class, "getViewRootImpl");
    private static final Field VIEW_ROOT_IMPL_SURFACE = findField(VIEW_ROOT_IMPL, "mSurface");
    private static final Method RESOURCES_GET_IMPL = findMethod(Resources.class, "getImpl");
    private static final Field LAYOUT_PARAMS_INSETS_FLAGS = findField(WindowManager.LayoutParams.class, "insetsFlags");
    private static final Field INSETS_FLAGS_APPEARANCE = findField(findClass("android.view.InsetsFlags"), "appearance");
    private static final Method TRANSACTION_SET_COLOR_TRANSFORM = findMethod(SurfaceControl.Transaction.class,
            "setColorTransform", SurfaceControl.class, float[].class, float[].class);

    private Hidden() {
    }

    /** {@code view.getViewRootImpl()}; null when the view is not attached. */
    static Object viewRootImpl(View view) {
        return invoke(VIEW_GET_VIEW_ROOT_IMPL, "View.getViewRootImpl", view);
    }

    /** {@code viewRootImpl.mSurface}. */
    static Surface surface(Object viewRootImpl) {
        return (Surface) get(VIEW_ROOT_IMPL_SURFACE, "ViewRootImpl.mSurface", viewRootImpl);
    }

    /** {@code resources.getImpl()}. */
    static Object resourcesImpl(Resources resources) {
        return invoke(RESOURCES_GET_IMPL, "Resources.getImpl", resources);
    }

    /** {@code params.insetsFlags.appearance}. */
    static int barAppearance(WindowManager.LayoutParams params) {
        Object flags = get(LAYOUT_PARAMS_INSETS_FLAGS, "LayoutParams.insetsFlags", params);
        return (Integer) get(INSETS_FLAGS_APPEARANCE, "InsetsFlags.appearance", flags);
    }

    /** {@code params.insetsFlags.appearance = appearance}. */
    static void setBarAppearance(WindowManager.LayoutParams params, int appearance) {
        Object flags = get(LAYOUT_PARAMS_INSETS_FLAGS, "LayoutParams.insetsFlags", params);
        if (INSETS_FLAGS_APPEARANCE == null) {
            throw new NoSuchFieldError("InsetsFlags.appearance");
        }
        try {
            INSETS_FLAGS_APPEARANCE.setInt(flags, appearance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code transaction.setColorTransform(surface, matrix, translation)}. */
    static void setColorTransform(SurfaceControl.Transaction transaction, SurfaceControl surface, float[] matrix, float[] translation) {
        invoke(TRANSACTION_SET_COLOR_TRANSFORM, "Transaction.setColorTransform", transaction, surface, matrix, translation);
    }

    private static Object invoke(Method method, String name, Object target, Object... args) {
        if (method == null) {
            throw new NoSuchMethodError(name);
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object get(Field field, String name, Object target) {
        if (field == null) {
            throw new NoSuchFieldError(name);
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, View.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... types) {
        if (owner == null) {
            return null;
        }
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }
}
