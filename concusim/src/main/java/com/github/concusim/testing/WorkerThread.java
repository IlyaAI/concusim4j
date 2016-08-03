package com.github.concusim.testing;

import com.github.concusim.Concurrency;
import com.github.concusim.ICheckpointInterceptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

final class WorkerThread extends Thread implements ICheckpointInterceptor {
    private static final Logger log = LoggerFactory.getLogger(WorkerThread.class);

    private final Trigger trigger = new Trigger();
    private final NamedWorker worker;
    private final long timeout;
    private volatile Step step;
    private volatile CountDownLatch latch;
    private volatile Throwable cause;

    WorkerThread(@NotNull NamedWorker worker, long timeout) {
        this.worker = worker;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        Concurrency.setInterceptor(this);
        log.info("{} => started", worker);

        try {
            await();

            worker.run();

            log.info("{} => finished", worker);
        } catch (InterruptedException e) {
            log.info("{} => interrupted", worker);
        } catch (Throwable t) {
            cause = t;
            log.error("{} => threw '{}'", worker, t.getMessage(), t);
        } finally {
            if (step != null) {
                step.workerFinished();
            }
            Concurrency.setInterceptor(null);
            if (latch != null)
                latch.countDown();
        }
    }

    @Override
    public void onCheckpoint(@NotNull String name) {
        log.info("{} => @{}", worker, name);

        if (isInterrupted())
            throw new RuntimeException("Thread has been interrupted.");

        if (!step.hasCheckpoint() || !name.equals(step.getCheckpoint()))
            return;

        step.checkpointReached();
        step = null;

        latch.countDown();
        try {
            await();
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return worker.getName();
    }

    @NotNull NamedWorker getNamedWorker() {
        return worker;
    }

    void doStep(@NotNull Step step, @NotNull CountDownLatch latch) {
        if (this.step != null)
            throw new IllegalStateException(String.format("Thread of '%s' already in use.", worker));

        this.step = step;
        this.latch = latch;

        fire();
    }

    @Nullable Throwable getCause() {
        return cause;
    }

    private void await() throws InterruptedException, TimeoutException {
        log.info("{} => waiting", worker);

        boolean fired;
        long start = System.currentTimeMillis();

        //noinspection StatementWithEmptyBody
        while (!(fired = trigger.await(timeout)) && (System.currentTimeMillis() - start < timeout));

        if (!fired)
            throw new TimeoutException();

        log.info("{} => resumed", worker);
    }

    private void fire() {
        log.info("{} => fired", worker);
        trigger.fire();
    }
}
