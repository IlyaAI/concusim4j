package com.github.concusim;

import com.github.concusim.testing.ConcurrencySimulator;
import org.jetbrains.annotations.NotNull;

/**
 * Concurrency checkpoint interceptor.
 *
 * This interface is used by {@link ConcurrencySimulator} to simulate threads interleaving.
 */
public interface ICheckpointInterceptor {

    /**
     * Called when executing thread reaches {@code Concurrency.checkpoint(name)}.
     *
     * @param name checkpoint name
     */
    void onCheckpoint(@NotNull String name);
}
