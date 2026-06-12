package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class GovUkPayAgreementDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity> {
    @javax.inject.Inject
    public GovUkPayAgreementDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity.class);
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity> findByExternalServiceId(java.lang.String externalServiceId) {
        java.lang.String query = "SELECT agreement FROM GovUkPayAgreementEntity agreement " + "WHERE agreement.service.externalId  = :externalServiceId";
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity.class).setParameter("externalServiceId", externalServiceId).getResultStream().findFirst();
    }
}
