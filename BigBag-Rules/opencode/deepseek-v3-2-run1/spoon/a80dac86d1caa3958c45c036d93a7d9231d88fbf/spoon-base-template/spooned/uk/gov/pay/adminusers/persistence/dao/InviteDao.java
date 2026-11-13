package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class InviteDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.InviteEntity> {
    @com.google.inject.Inject
    protected InviteDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.InviteEntity.class);
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.InviteEntity> findByCode(java.lang.String code) {
        java.lang.String query = "SELECT invite FROM InviteEntity invite " + "WHERE invite.code = :code";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.InviteEntity.class).setParameter("code", code).getResultList().stream().findFirst();
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> findByEmail(java.lang.String email) {
        java.lang.String query = "SELECT invite FROM InviteEntity invite " + "WHERE invite.email = :email";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.InviteEntity.class).setParameter("email", email).getResultList();
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.InviteEntity> findAllByServiceId(java.lang.String serviceId) {
        java.lang.String query = "SELECT invite FROM InviteEntity invite " + "WHERE invite.service.externalId = :serviceId";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.InviteEntity.class).setParameter("serviceId", serviceId).getResultList();
    }
}
