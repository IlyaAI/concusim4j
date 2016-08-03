package com.github.concusim.sample;

import com.github.concusim.Concurrency;
import com.github.concusim.testing.AggregatedException;
import com.github.concusim.testing.ConcurrencySimulator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class MyServiceTest {
    private EntityManagerFactory emf;

    @Before
    public void setUp() {
        emf = Persistence.createEntityManagerFactory("sample-db");
        Concurrency.enableCheckpoints();
    }

    @After
    public void tearDown() {
        Concurrency.disableCheckpoints();
        if (emf != null) {
            emf.close();
            emf = null;
        }
    }

    @Test
    public void myService_server_should_throw_optimistic_lock_exception_if_concurrent_modification() throws Exception {
        // arrange
        long entityId;

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            MyEntity entity = MyEntity.newOne("key", "value");
            em.persist(entity);
            em.getTransaction().commit();

            entityId = entity.getId();
        } finally {
            em.close();
        }

        MyService svc = new MyService(emf);

        // act concurrently
        Runnable requestA = () -> svc.serve(entityId, "new-value-A");
        Runnable requestB = () -> svc.serve(entityId, "new-value-B");

        ConcurrencySimulator simulator = new ConcurrencySimulator()
            .withStepTimeoutSec(1)
            .withConcurrentWorker(requestA, "A")
            .withConcurrentWorker(requestB, "B");

        simulator.start();
        try {
            simulator
                .run(requestA).till(MyService.class, MyService.CHECKPOINT_BEFORE_COMMIT)
                .run(requestB).tillEnd()
                .go();

            try {
                simulator
                    .run(requestA).tillEnd()
                    .go();

                fail("AggregatedException is expected.");
            } catch (AggregatedException e) {
                // assert
                assertThat(e.getCauseFor(requestA), instanceOf(OptimisticLockException.class));
            }
        } finally {
            simulator.stop();
        }
    }
}
