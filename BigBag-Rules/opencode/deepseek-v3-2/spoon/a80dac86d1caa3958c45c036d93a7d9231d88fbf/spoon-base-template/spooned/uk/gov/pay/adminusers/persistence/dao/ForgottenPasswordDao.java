package uk.gov.pay.adminusers.persistence.dao;
@com.google.inject.persist.Transactional
public class ForgottenPasswordDao extends uk.gov.pay.adminusers.persistence.dao.JpaDao<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> {
    private final java.lang.Integer forgottenPasswordExpiryMinutes;

    @com.google.inject.Inject
    protected ForgottenPasswordDao(com.google.inject.Provider<javax.persistence.EntityManager> entityManager, @com.google.inject.name.Named("FORGOTTEN_PASSWORD_EXPIRY_MINUTES")
    java.lang.Integer forgottenPasswordExpiryMinutes) {
        super(entityManager, uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.class);
        this.forgottenPasswordExpiryMinutes = forgottenPasswordExpiryMinutes;
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> findNonExpiredByCode(java.lang.String code) {
        java.lang.String query = "SELECT fp FROM ForgottenPasswordEntity fp " + "WHERE fp.code = :code AND fp.date >= :expiry";
        java.time.ZonedDateTime expiryDateTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(forgottenPasswordExpiryMinutes);
        return entityManager.get().createQuery(query, uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.class).setParameter("code", code).setParameter("expiry", expiryDateTime).getResultList().stream().findFirst();
    }
}
