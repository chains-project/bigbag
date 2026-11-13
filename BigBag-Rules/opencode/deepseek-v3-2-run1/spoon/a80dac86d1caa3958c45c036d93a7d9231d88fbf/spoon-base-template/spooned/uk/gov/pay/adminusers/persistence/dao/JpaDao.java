package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public abstract class JpaDao<T> {
    /* default */
    final com.google.inject.Provider<javax.persistence.EntityManager> entityManager;

    private final java.lang.Class<T> persistenceClass;

    /* default */
    JpaDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager, java.lang.Class<T> persistenceClass) {
        this.entityManager = entityManager;
        this.persistenceClass = persistenceClass;
    }

    public void persist(final T object) {
        entityManager.get().persist(object);
    }

    public void remove(T object) {
        if (entityManager.get().contains(object)) {
            entityManager.get().remove(object);
        } else {
            T mergedObject = entityManager.get().merge(object);
            entityManager.get().remove(mergedObject);
        }
    }

    public <ID> java.util.Optional<T> findById(final ID id) {
        return java.util.Optional.ofNullable(entityManager.get().find(persistenceClass, id));
    }

    public T merge(final T object) {
        return entityManager.get().merge(object);
    }
}
