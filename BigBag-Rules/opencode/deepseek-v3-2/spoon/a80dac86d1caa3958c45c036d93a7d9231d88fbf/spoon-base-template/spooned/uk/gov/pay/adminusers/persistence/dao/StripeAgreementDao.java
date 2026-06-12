package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class StripeAgreementDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity> {
    @javax.inject.Inject
    public StripeAgreementDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity.class);
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity> findByServiceExternalId(java.lang.String serviceExternalId) {
        java.lang.String query = "SELECT s FROM StripeAgreementEntity s " + "WHERE s.service.externalId = :serviceExternalId";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity.class).setParameter("serviceExternalId", serviceExternalId).getResultStream().findFirst();
    }
}
