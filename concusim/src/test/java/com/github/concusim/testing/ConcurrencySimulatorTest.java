package com.github.concusim.testing;

import com.github.concusim.Concurrency;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ConcurrencySimulatorTest {

    private static class StepByStepWorker implements Runnable {
        private final String name;
        private volatile String currentStep = "init";

        StepByStepWorker(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            currentStep = "foo";
            Concurrency.checkpoint(getClass(), "foo");

            currentStep = "bar";
            Concurrency.checkpoint(getClass(), "bar");

            currentStep = "end";
        }

        @Override
        public String toString() {
            return name;
        }

        String getCurrentStep() {
            return currentStep;
        }
    }

    @Before
    public void setUp() throws Exception {
        Concurrency.enableCheckpoints();
    }

    @After
    public void tearDown() throws Exception {
        Concurrency.disableCheckpoints();
    }

    @Test
    public void concurrencySimulator_should_control_thread_execution() throws Exception {
        StepByStepWorker workerA = new StepByStepWorker("A");
        StepByStepWorker workerB = new StepByStepWorker("B");

        ConcurrencySimulator simulator = new ConcurrencySimulator()
            .withConcurrentWorker(workerA)
            .withConcurrentWorker(workerB);

        simulator.start();
        try {
            assertThat(workerA.getCurrentStep(), is("init"));
            assertThat(workerB.getCurrentStep(), is("init"));

            simulator
                .run(workerA).till(StepByStepWorker.class, "foo")
                .go();

            assertThat(workerA.getCurrentStep(), is("foo"));
            assertThat(workerB.getCurrentStep(), is("init"));

            simulator
                .run(workerB).till(StepByStepWorker.class, "foo")
                .go();

            assertThat(workerA.getCurrentStep(), is("foo"));
            assertThat(workerB.getCurrentStep(), is("foo"));

            simulator
                .run(workerA).till(StepByStepWorker.class, "bar")
                .run(workerB).tillEnd()
                .go();

            assertThat(workerA.getCurrentStep(), is("bar"));
            assertThat(workerB.getCurrentStep(), is("end"));

            simulator
                .run(workerA).tillEnd()
                .go();

            assertThat(workerA.getCurrentStep(), is("end"));
            assertThat(workerB.getCurrentStep(), is("end"));
        } finally {
            simulator.stop();
        }
    }

    @Test
    public void concurrencySimulator_go_should_throw_if_worker_threw() throws Exception {
        Runnable worker = () -> {
            throw new RuntimeException("Error");
        };

        ConcurrencySimulator simulator = new ConcurrencySimulator()
            .withConcurrentWorker(worker, "worker");

        simulator.start();
        try {
            simulator
                .run(worker).tillEnd()
                .go();

            fail("AggregatedException is expected.");
        } catch (AggregatedException e) {
            assertThat(e.getCauseFor(worker), instanceOf(RuntimeException.class));
        } finally {
            simulator.stop();
        }
    }

    @Test
    public void concurrencySimulator_go_should_throw_if_non_visited_checkpoint_exists() throws Exception {
        Runnable worker = new Runnable() {
            @Override
            public void run() {
                Concurrency.checkpoint(getClass(), "foo");
            }
        };

        ConcurrencySimulator simulator = new ConcurrencySimulator()
            .withConcurrentWorker(worker, "fooWorker");

        simulator.start();
        try {
            simulator
                .run(worker).till(worker.getClass(), "bar")
                .go();

            fail("CheckpointException is expected.");
        } catch (CheckpointException e) {
            assertThat(e.getMessage(), containsString("bar"));
            assertThat(e.getMessage(), containsString("fooWorker"));
        } finally {
            simulator.stop();
        }
    }
}
