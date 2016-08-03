package com.github.concusim.sample;

import com.github.concusim.Concurrency;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

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
            Concurrency.checkpoint(getClass(), CHECKPOINT_BEFORE_COMMIT);
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
