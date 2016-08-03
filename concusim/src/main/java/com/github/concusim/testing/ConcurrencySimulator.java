package com.github.concusim.testing;

import com.github.concusim.Concurrency;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.concusim.testing.CheckpointUtils.getFullName;

/**
 * Concurrency testing helper. Provides facilities to model threads interleaving based on checkpoints.
 *
 * @see com.github.concusim.Concurrency
 */
public final class ConcurrencySimulator {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencySimulator.class);

    @SuppressWarnings("WeakerAccess")
    public final class StepBuilder {
        private final WorkerThread thread;
        private boolean concurrent = false;

        StepBuilder(@NotNull WorkerThread thread) {
            this.thread = thread;
        }

        /**
         * Specifies to run this step concurrently.
         *
         * @return step builder
         */
        public @NotNull StepBuilder parallel() {
            this.concurrent = true;
            return this;
        }

        /**
         * Specifies checkpoint till run step to and finishes step creation.
         *
         * @param checkpoint checkpoint name
         * @return original concurrent runner
         */
        public @NotNull ConcurrencySimulator till(@NotNull Class<?> clazz, @NotNull String checkpoint) {
            if (checkpoint.isEmpty())
                throw new IllegalArgumentException("checkpoint must not be empty");

            if (!Concurrency.isCheckpointsEnabled())
                throw new IllegalStateException("Checkpoints are disabled. Forget to call Concurrency.enableCheckpoints?");

            steps.add(new Step(thread, getFullName(clazz, checkpoint), concurrent));
            return ConcurrencySimulator.this;
        }

        /**
         * Specifies run step till doer's end and finishes step creation.
         *
         * @return original concurrent runner
         */
        public @NotNull ConcurrencySimulator tillEnd() {
            steps.add(new Step(thread, null, concurrent));
            return ConcurrencySimulator.this;
        }
    }

    private final List<WorkerThread> threads = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private long stepTimeout;

    /**
     * Constructs new ConcurrencySimulator with default step timeout (5sec).
     */
    public ConcurrencySimulator() {
        stepTimeout = 5000;
    }

    /**
     * Sets new step timeout in seconds.
     *
     * @param seconds step timeout in seconds
     * @return this
     */
    public @NotNull ConcurrencySimulator withStepTimeoutSec(int seconds) {
        if (seconds < 0)
            throw new IllegalArgumentException("seconds must be non-negative");

        stepTimeout = seconds * 1000;
        return this;
    }

    /**
     * Sets new step timeout in milliseconds.
     *
     * @param milliseconds step timeout in milliseconds
     * @return this
     */
    public @NotNull ConcurrencySimulator withStepTimeoutMillis(long milliseconds) {
        if (milliseconds < 0)
            throw new IllegalArgumentException("milliseconds must be non-negative");

        stepTimeout = milliseconds;
        return this;
    }

    /**
     * Adds new concurrent worker with specified body (as runnable) and name.
     * Each worker will run in separate thread.
     *
     * @param worker worker body
     * @param name worker name
     * @return this
     */
    public @NotNull ConcurrencySimulator withConcurrentWorker(@NotNull Runnable worker, @NotNull String name) {
        NamedWorker namedWorker = new NamedWorker(worker, name);
        threads.add(new WorkerThread(namedWorker, stepTimeout));
        return this;
    }

    /**
     * Adds new concurrent worker with specified body (as runnable).
     * Each worker will run in separate thread.
     *
     * @param worker worker body
     * @return this
     */
    public @NotNull ConcurrencySimulator withConcurrentWorker(@NotNull Runnable worker) {
        return withConcurrentWorker(worker, worker.toString());
    }

    /**
     * Constructs step for specified worker. By default steps are executed sequentially,
     * use StepBuilder.parallel() to override this behavior.
     *
     * @param worker worker to be run
     * @return step builder
     */
    public @NotNull StepBuilder run(@NotNull Runnable worker) {
        return new StepBuilder(getThreadOf(worker));
    }

    /**
     * Starts all worker's threads and pauses them just before entering to worker's body.
     */
    public void start() {
        threads.forEach(Thread::start);
    }

    /**
     * Executes step sequence defined via run() method. Throws an exception if at least one thread is thrown.
     * If method returned normally it is possible to define next sub-sequence and call go() again.
     * If method threw exception then it IS NOT possible to call go() again.
     *
     * @throws AggregatedException if at least one thread failed during it step
     */
    public void go() throws
        InterruptedException, TimeoutException,
        AggregatedException, CheckpointException
    {
        for (int index = 0; index < steps.size(); ) {
            int count = 1;
            for (int i = index + 1; i < steps.size() && steps.get(i).isConcurrent(); i++) {
                count++;
            }

            CountDownLatch latch = new CountDownLatch(count);

            for (; count > 0; index++, count--) {
                steps.get(index).doIt(latch);
            }

            if (!latch.await(stepTimeout, TimeUnit.MILLISECONDS))
                throw new TimeoutException();

            AggregatedException.Builder builder = new AggregatedException.Builder();
            threads.forEach(builder::addCauseIfAny);
            builder.throwIfAny();
        }

        steps.forEach(Step::validate);
        steps.clear();
    }

    /**
     * Signals all worker's threads to interrupt and tries to join.
     */
    public void stop() {
        threads.forEach(Thread::interrupt);

        for (WorkerThread t: threads) {
            try {
                t.join(stepTimeout);
            } catch (InterruptedException e) {
                log.info("{} => JOIN TIMED-OUT", t);
            }
        }
    }

    private @NotNull WorkerThread getThreadOf(@NotNull Runnable worker) {
        for (WorkerThread t: threads) {
            if (t.getNamedWorker().getRunnable() == worker)
                return t;
        }

        throw new IllegalStateException(String.format("No thread bound to '%s'.", worker));
    }
}
