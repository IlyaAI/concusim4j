# ConcuSim: Concurrency Simulator Testing Tool

ConcuSim is a \[very\] simple tool for simulate thread interleaving in test cases. 

Latest release: 1.0

## Getting Started

You could find complete example [here](https://github.com/IlyaAI/concusim4j/tree/master/concusim-sample)

#### Step 1. Add Maven or Gradle dependency

```groovy
dependencies {
    compile 'com.github.concusim:concusim4j:1.0'
}
```

#### Step 2. Add concurrency checkpoints in your code

```java
/**
 * This is example of some domain service.
 */
public final class MyService {
    static final String CHECKPOINT_BEFORE_COMMIT = "beforeCommit";

    private final EntityManagerFactory emf;

    public MyService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /**
     * Some method which operates on MyEntity and might be executed concurrently.
     *
     * @param id entity id
     * @param value some input
     * @return modified entity
     */
    public MyEntity serve(long id, String value) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            MyEntity entity = MyEntity.getById(em, id);

            /* some long processing here */

            entity.setValue(value);

            /*
             * We would like to simulate concurrent modification of the same entity by another transaction,
             * so we place checkpoint before commit. This allows to pause one thread here while another one
             * performs a commit first. Then we resume and should get OptimisticLockException.
             */
            Concurrency.checkpoint(getClass(), CHECKPOINT_BEFORE_COMMIT); // <<<===
            em.flush();
            em.getTransaction().commit();

            return entity;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
```

#### Step 3. Write a test

The following test checks that concurrent modification of versioned JPA entity throws an OptimisticLockException.

```java
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
```
