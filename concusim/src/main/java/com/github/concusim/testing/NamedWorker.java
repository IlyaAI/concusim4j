package com.github.concusim.testing;

import org.jetbrains.annotations.NotNull;

final class NamedWorker implements Runnable {
    private final Runnable target;
    private final String name;

    NamedWorker(@NotNull Runnable target, @NotNull String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public void run() {
        target.run();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return
            this == other ||
            other != null && other.getClass() == getClass() &&
            target == ((NamedWorker) other).target;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @NotNull Runnable getRunnable() {
        return target;
    }

    @NotNull String getName() {
        return name;
    }
}
