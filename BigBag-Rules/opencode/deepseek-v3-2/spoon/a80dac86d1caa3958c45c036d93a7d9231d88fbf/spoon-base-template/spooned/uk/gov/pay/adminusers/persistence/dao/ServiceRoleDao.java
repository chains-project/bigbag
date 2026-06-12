package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class ServiceRoleDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> {
    /* default */
    @com.google.inject.Inject
    ServiceRoleDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity.class);
    }
}
