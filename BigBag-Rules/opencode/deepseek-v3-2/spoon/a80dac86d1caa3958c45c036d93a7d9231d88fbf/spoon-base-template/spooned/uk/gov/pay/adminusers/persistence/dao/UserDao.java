package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class UserDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.UserEntity> {
    @javax.inject.Inject
    public UserDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.UserEntity.class);
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> findByExternalId(java.lang.String externalId) {
        java.lang.String query = "SELECT u FROM UserEntity u " + "WHERE LOWER(u.externalId) = LOWER(:externalId)";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.UserEntity.class).setParameter("externalId", externalId).getResultList().stream().findFirst();
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> findByExternalIds(java.util.List<java.lang.String> externalIds) {
        java.lang.String query = "SELECT u FROM UserEntity u WHERE LOWER(u.externalId) in :externalIds";
        java.util.List<java.lang.String> lowerCaseExternalIds = externalIds.stream().map(java.lang.String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableList());
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.UserEntity.class).setParameter("externalIds", lowerCaseExternalIds).getResultList();
    }

    public java.util.Map<java.lang.String, java.util.List<java.lang.String>> getAdminUserEmailsForGatewayAccountIds(java.util.List<java.lang.String> gatewayAccountIds) {
        if (gatewayAccountIds.size() > 0) {
            java.lang.String positionalParams = java.util.stream.IntStream.rangeClosed(1, gatewayAccountIds.size()).mapToObj(java.lang.Integer::toString).map(i -> "?" + i).collect(java.util.stream.Collectors.joining(","));
            java.lang.String query = (((((((("SELECT sga.gateway_account_id, users.email FROM service_gateway_accounts sga" + " RIGHT JOIN user_services_roles usr") + " ON usr.service_id = sga.service_id") + " JOIN users ON users.id = usr.user_id") + " JOIN roles ON roles.id = usr.role_id") + " WHERE sga.gateway_account_id in (") + positionalParams) + ")") + " AND roles.name='admin'") + " ORDER by sga.gateway_account_id";
            javax.persistence.Query nativeQuery = entityManager.get().createNativeQuery(query);
            java.util.stream.IntStream.rangeClosed(1, gatewayAccountIds.size()).forEach(i -> nativeQuery.setParameter(i, gatewayAccountIds.get(i - 1)));
            java.util.List<java.lang.Object[]> gatewayAccountIdsToAdminEmails = nativeQuery.getResultList();
            return gatewayAccountIdsToAdminEmails.stream().map(arrayOfObject -> new java.util.AbstractMap.SimpleEntry<>(((java.lang.String) (arrayOfObject[0])), ((java.lang.String) (arrayOfObject[1])))).collect(java.util.stream.Collectors.groupingBy(java.util.AbstractMap.SimpleEntry::getKey)).entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, abstractMap -> abstractMap.getValue().stream().map(java.util.AbstractMap.SimpleEntry::getValue).collect(java.util.stream.Collectors.toUnmodifiableList())));
        } else {
            return java.util.Map.of();
        }
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> findByUsername(java.lang.String username) {
        java.lang.String query = "SELECT u FROM UserEntity u " + "WHERE LOWER(u.username) = LOWER(:username)";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.UserEntity.class).setParameter("username", username).getResultList().stream().findFirst();
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> findByEmail(java.lang.String email) {
        java.lang.String query = "SELECT u FROM UserEntity u " + "WHERE LOWER(u.email) = LOWER(:email)";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.UserEntity.class).setParameter("email", email).getResultList().stream().findFirst();
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> findByServiceId(java.lang.Integer serviceId) {
        java.lang.String query = "SELECT s FROM ServiceRoleEntity s " + "WHERE s.service.id = :serviceId ORDER BY s.user.username";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity.class).setParameter("serviceId", serviceId).getResultList().stream().map(uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity::getUser).collect(java.util.stream.Collectors.toUnmodifiableList());
    }
}
