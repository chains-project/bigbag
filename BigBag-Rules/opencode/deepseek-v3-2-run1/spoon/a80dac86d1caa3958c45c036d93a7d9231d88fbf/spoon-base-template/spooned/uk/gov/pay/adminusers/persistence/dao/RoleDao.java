package uk.gov.pay.adminusers.persistence.dao;
/**
 * Not extending the generic JpaDao purposefully.
 * <p>This is to avoid inheriting persistence methods such as <b>persist, merge, etc</b>
 * which should never be used with ReadOnly RoleEntity.
 * </p>
 *
 * @see uk.gov.pay.adminusers.persistence.entity.RoleEntity
 */
public class RoleDao {
    private final com.google.inject.Provider<javax.persistence.EntityManager> entityManager;

    @com.google.inject.Inject
    public RoleDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.RoleEntity> findByRoleName(java.lang.String roleName) {
        java.lang.String query = "SELECT r FROM RoleEntity r " + "WHERE r.name = :roleName";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.RoleEntity.class).setParameter("roleName", roleName).getResultList().stream().findFirst();
    }
}
