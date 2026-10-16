package moe.ono.startup;

import android.annotation.SuppressLint;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import moe.ono.BuildConfig;
import moe.ono.loader.hookapi.IHookBridge;
import moe.ono.loader.hookapi.ILoaderService;

@Keep
@SuppressWarnings("unused")
public class UnifiedEntryPoint {

    private static boolean sInitialized = false;

    private UnifiedEntryPoint() {
    }

    @Keep
    public static void entry(
            @NonNull String modulePath,
            @NonNull String hostDataDir,
            @NonNull ILoaderService loaderService,
            @NonNull ClassLoader hostClassLoader,
            @Nullable IHookBridge hookBridge
    ) {
        if (sInitialized) {
            throw new IllegalStateException("UnifiedEntryPoint already initialized");
        }
        sInitialized = true;
        // fix up the class loader
        HybridClassLoader loader = HybridClassLoader.INSTANCE;
        ClassLoader self = UnifiedEntryPoint.class.getClassLoader();
        assert self != null;
        ClassLoader parent = self.getParent();
        HybridClassLoader.setLoaderParentClassLoader(parent);
        injectClassLoader(self, loader);
        callNextStep(modulePath, hostDataDir, loaderService, hostClassLoader, hookBridge);
    }

    private static void callNextStep(
            @NonNull String modulePath,
            @NonNull String hostDataDir,
            @NonNull ILoaderService loaderService,
            @NonNull ClassLoader hostClassLoader,
            @Nullable IHookBridge hookBridge
    ) {
        try {
            Class<?> kStartupAgent = Class.forName("moe.ono.startup.StartupAgent", false, UnifiedEntryPoint.class.getClassLoader());
            kStartupAgent.getMethod("startup", String.class, String.class, ILoaderService.class, ClassLoader.class, IHookBridge.class)
                    .invoke(null, modulePath, hostDataDir, loaderService, hostClassLoader, hookBridge);
        } catch (ReflectiveOperationException e) {
            Throwable cause = getInvocationTargetExceptionCause(e);
            android.util.Log.e(BuildConfig.TAG, "StartupAgent.startup: failed", cause);
            try {
                loaderService.log(cause);
            } catch (Throwable ignored) {
            }
            throw unsafeThrow(cause);
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("DiscouragedPrivateApi")
    private static void injectClassLoader(ClassLoader self, ClassLoader newParent) {
        try {
            Field fParent = ClassLoader.class.getDeclaredField("parent");
            fParent.setAccessible(true);
            fParent.set(self, newParent);
        } catch (Exception e) {
            android.util.Log.e(BuildConfig.TAG, "injectClassLoader: failed", e);
        }
    }

    @NonNull
    private static Throwable getInvocationTargetExceptionCause(@NonNull Throwable e) {
        while (e instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) e).getTargetException();
            if (cause != null) {
                e = cause;
            } else {
                break;
            }
        }
        return e;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static <T extends Throwable> AssertionError unsafeThrow(@NonNull Throwable e) throws T {
        throw (T) e;
    }

}
