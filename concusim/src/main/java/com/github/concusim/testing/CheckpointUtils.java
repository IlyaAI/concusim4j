package com.github.concusim.testing;

import org.jetbrains.annotations.NotNull;

public final class CheckpointUtils {
    public static @NotNull String getFullName(@NotNull Class<?> clazz, @NotNull String name) {
        return clazz.getName() + "#" + name;
    }
}
