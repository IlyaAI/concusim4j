package com.github.concusim.testing;

import org.jetbrains.annotations.NotNull;

public final class CheckpointException extends RuntimeException {
    public CheckpointException(@NotNull String msg) {
        super(msg);
    }
}
