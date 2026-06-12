package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class ServiceDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> {
    @javax.inject.Inject
    public ServiceDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> listAll() {
        java.lang.String query = "SELECT s FROM ServiceEntity as s";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class).getResultList();
    }

    @java.lang.SuppressWarnings("unchecked")
    public java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> findByENServiceName(java.lang.String searchString) {
        java.lang.String query = "SELECT * FROM services s WHERE s.id IN (SELECT service_id FROM service_names sn WHERE to_tsvector('english', sn.name) @@ plainto_tsquery('english', ?) AND sn.language = 'en')";
        return entityManager.get().createNativeQuery(query, uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class).setParameter(1, searchString).getResultList();
    }

    @java.lang.SuppressWarnings("unchecked")
    public java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> findByServiceMerchantName(java.lang.String searchString) {
        java.lang.String query = "SELECT * FROM services s WHERE to_tsvector('english', s.merchant_name) @@ plainto_tsquery('english', ?)";
        return entityManager.get().createNativeQuery(query, uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class).setParameter(1, searchString).getResultList();
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> findByGatewayAccountId(java.lang.String gatewayAccountId) {
        java.lang.String query = "SELECT ga FROM GatewayAccountIdEntity ga " + "WHERE ga.gatewayAccountId = :gatewayAccountId";
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> gatewayAccount = entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity.class).setParameter("gatewayAccountId", gatewayAccountId).getResultList().stream().findFirst();
        return gatewayAccount.map(uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity::getService);
    }

    public java.lang.Long countOfUsersWithRoleForService(java.lang.String serviceExternalId, java.lang.Integer roleId) {
        java.lang.String query = "SELECT count(*) FROM user_services_roles usr WHERE usr.role_id=? AND usr.service_id = (SELECT srv.id FROM services srv WHERE srv.external_id = ?)";
        return ((long) (entityManager.get().createNativeQuery(query).setParameter(1, roleId).setParameter(2, serviceExternalId).getSingleResult()));
    }

    public boolean checkIfGatewayAccountsUsed(java.util.List<java.lang.String> gatewayAccountsIds) {
        java.lang.String query = "SELECT count(*) FROM service_gateway_accounts WHERE gateway_account_id IN (?)";
        long count = ((long) (entityManager.get().createNativeQuery(query).setParameter(1, java.lang.String.join(",", gatewayAccountsIds)).getSingleResult()));
        return count > 0;
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> findByExternalId(java.lang.String serviceExternalId) {
        java.lang.String query = "SELECT s FROM ServiceEntity as s WHERE s.externalId = :externalId";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class).setParameter("externalId", serviceExternalId).getResultList().stream().findFirst();
    }
}
