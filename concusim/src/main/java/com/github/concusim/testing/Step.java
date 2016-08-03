package com.github.concusim.testing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;

final class Step {
    private final WorkerThread thread;
    private final String checkpoint;
    private final boolean concurrent;
    private volatile boolean checkpointReached;
    private volatile boolean workerFinished;

    Step(@NotNull WorkerThread thread, @Nullable String checkpoint, boolean concurrent) {
        this.thread = thread;
        this.checkpoint = checkpoint;
        this.concurrent = concurrent;
    }

    void doIt(@NotNull CountDownLatch latch) {
        thread.doStep(this, latch);
    }

    boolean hasCheckpoint() {
        return checkpoint != null;
    }

    @Nullable String getCheckpoint() {
        return checkpoint;
    }

    boolean isConcurrent() {
        return concurrent;
    }

    void checkpointReached() {
        checkpointReached = true;
    }

    void workerFinished() {
        workerFinished = true;
    }

    void validate() throws CheckpointException {
        if (checkpoint != null && !checkpointReached)
            throw new CheckpointException(
                String.format("Worker '%s' didn't reach checkpoint '%s' as expected.", thread, checkpoint));

        if (checkpoint == null && !workerFinished)
            throw new CheckpointException(String.format("Worker '%s' didn't finished as expected.", thread));
    }
}
