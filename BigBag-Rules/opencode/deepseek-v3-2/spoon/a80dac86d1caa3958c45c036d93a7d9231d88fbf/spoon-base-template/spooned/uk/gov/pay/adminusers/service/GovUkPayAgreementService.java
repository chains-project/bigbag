package uk.gov.pay.adminusers.service;
public class GovUkPayAgreementService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.GovUkPayAgreementService.class);

    private final uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao agreementDao;

    @com.google.inject.Inject
    public GovUkPayAgreementService(uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao govUkPayAgreementDao) {
        this.agreementDao = govUkPayAgreementDao;
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.GovUkPayAgreement> findGovUkPayAgreementByServiceId(java.lang.String serviceExternalId) {
        return agreementDao.findByExternalServiceId(serviceExternalId).map(uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity::toGovUkPayAgreement);
    }

    public uk.gov.pay.adminusers.model.GovUkPayAgreement doCreate(uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity, java.lang.String email, java.time.ZonedDateTime agreementTime) {
        uk.gov.pay.adminusers.service.GovUkPayAgreementService.LOGGER.info(java.lang.String.format("Creating GOV.UK Pay agreement for service %s", serviceEntity.getExternalId()));
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity agreementEntity = new uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity(email, agreementTime);
        agreementEntity.setService(serviceEntity);
        agreementDao.persist(agreementEntity);
        return agreementEntity.toGovUkPayAgreement();
    }
}
