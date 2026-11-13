package uk.gov.pay.adminusers.service;
public class StripeAgreementService {
    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.StripeAgreementService.class);

    private final uk.gov.pay.adminusers.persistence.dao.StripeAgreementDao stripeAgreementDao;

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    @com.google.inject.Inject
    public StripeAgreementService(uk.gov.pay.adminusers.persistence.dao.StripeAgreementDao stripeAgreementDao, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao) {
        this.stripeAgreementDao = stripeAgreementDao;
        this.serviceDao = serviceDao;
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.StripeAgreement> findStripeAgreementByServiceId(java.lang.String serviceExternalId) {
        return stripeAgreementDao.findByServiceExternalId(serviceExternalId).map(uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity::toStripeAgreement);
    }

    public void doCreate(java.lang.String serviceExternalId, java.net.InetAddress ipAddress) {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = serviceDao.findByExternalId(serviceExternalId).orElseThrow(() -> new uk.gov.pay.adminusers.exception.ServiceNotFoundException(serviceExternalId));
        if (stripeAgreementDao.findByServiceExternalId(serviceExternalId).isPresent()) {
            throw new uk.gov.pay.adminusers.exception.StripeAgreementExistsException();
        }
        uk.gov.pay.adminusers.service.StripeAgreementService.logger.info(java.lang.String.format("Creating stripe agreement for service %s", serviceExternalId));
        java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        stripeAgreementDao.persist(new uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity(serviceEntity, ipAddress.getHostAddress(), agreementTime));
    }
}
