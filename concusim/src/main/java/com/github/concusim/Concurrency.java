package com.github.concusim;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.github.concusim.testing.CheckpointUtils.getFullName;

/**
 * Concurrency testing helper. Provides facilities to model threads interleaving based on checkpoints.
 *
 * Use Concurrency.checkpoint("name") in production code in "interesting" places and then use ConcurrentRunner
 * in test to model specific interleaving cases.
 *
 * By default method checkpoint does nothing and has minimal overhead (just one check which should be in-lined by JIT).
 */
public final class Concurrency {
    private static final ThreadLocal<ICheckpointInterceptor> interceptor = new ThreadLocal<>();
    private static boolean enabled = false;

    /**
     * Denotes "interesting" place in concurrent code.
     *
     * @param clazz owning class
     * @param name checkpoint name
     */
    public static void checkpoint(@NotNull Class<?> clazz, @NotNull String name) {
        if (!enabled)
            return;

        if (interceptor.get() != null) {
            interceptor.get().onCheckpoint(getFullName(clazz, name));
        }
    }

    public static boolean isCheckpointsEnabled() {
        return enabled;
    }

    public static void enableCheckpoints() {
        enabled = true;
    }

    public static void disableCheckpoints() {
        enabled = false;
    }

    public static void setInterceptor(@Nullable ICheckpointInterceptor interceptor) {
        Concurrency.interceptor.set(interceptor);
    }
}
