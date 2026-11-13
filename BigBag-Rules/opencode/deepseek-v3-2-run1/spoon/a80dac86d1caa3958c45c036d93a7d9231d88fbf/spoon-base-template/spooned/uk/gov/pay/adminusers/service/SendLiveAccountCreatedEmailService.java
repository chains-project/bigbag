package uk.gov.pay.adminusers.service;
public class SendLiveAccountCreatedEmailService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.class);

    private static final java.lang.String SELFSERVICE_LIVE_ACCOUNT_PATH = "live-account";

    private final uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao govUkPayAgreementDao;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final java.lang.String selfserviceServicesUrl;

    @com.google.inject.Inject
    public SendLiveAccountCreatedEmailService(uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao govUkPayAgreementDao, uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.app.config.AdminUsersConfig config) {
        this.govUkPayAgreementDao = govUkPayAgreementDao;
        this.notificationService = notificationService;
        this.selfserviceServicesUrl = config.getLinks().getSelfserviceServicesUrl();
    }

    public void sendEmail(java.lang.String serviceExternalId) {
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity agreement = govUkPayAgreementDao.findByExternalServiceId(serviceExternalId).orElseThrow(uk.gov.pay.adminusers.exception.GovUkPayAgreementNotSignedException::new);
        java.lang.String serviceLiveAccountUrl = javax.ws.rs.core.UriBuilder.fromUri(selfserviceServicesUrl).path(serviceExternalId).path(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.SELFSERVICE_LIVE_ACCOUNT_PATH).build().toString();
        try {
            java.lang.String notificationId = notificationService.sendLiveAccountCreatedEmail(agreement.getEmail(), serviceLiveAccountUrl);
            uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.LOGGER.info("Sent service is live email successfully, notification id [{}]", notificationId);
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.LOGGER.error("Error sending service is live email", e);
        }
    }
}
