package com.github.concusim.sample;

import javax.persistence.*;

/**
 * Example of some domain entity.
 */
@Entity
public class MyEntity {
    @Id
    @GeneratedValue
    @Access(AccessType.PROPERTY)
    private long id;

    @Column(length = 32, nullable = false)
    private String name;

    @Column(length = 64)
    private String value;

    @Version
    private int revision;

    protected MyEntity() {
    }

    public long getId() {
        return id;
    }

    protected void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getRevision() {
        return revision;
    }

    public static MyEntity getById(EntityManager entityManager, long id) {
        MyEntity entity = entityManager.find(MyEntity.class, id);
        if (entity == null)
            throw new EntityNotFoundException();

        return entity;
    }

    public static MyEntity newOne(String name, String value) {
        MyEntity entity = new MyEntity();
        entity.name = name;
        entity.value = value;

        return entity;
    }
}
