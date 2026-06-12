package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class SendLiveAccountCreatedEmailServiceTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao mockGovUkPayAgreementDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.AdminUsersConfig mockConfig;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService;

    private static final java.lang.String SELFSERVICE_SERVICES_URL = "http://selfservice/services";

    private uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService sendLiveAccountCreatedEmailService;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        uk.gov.pay.adminusers.app.config.LinksConfig mockLinks = org.mockito.Mockito.mock(uk.gov.pay.adminusers.app.config.LinksConfig.class);
        org.mockito.Mockito.when(mockLinks.getSelfserviceServicesUrl()).thenReturn(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailServiceTest.SELFSERVICE_SERVICES_URL);
        org.mockito.Mockito.when(mockConfig.getLinks()).thenReturn(mockLinks);
        sendLiveAccountCreatedEmailService = new uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService(mockGovUkPayAgreementDao, mockNotificationService, mockConfig);
    }

    @org.junit.jupiter.api.Test
    public void shouldSendServiceIsLiveEmail_whenAgreementIsSigned() {
        java.lang.String serviceExternalId = "abc123";
        java.lang.String email = "some-user@example.com";
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity mockAgreement = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity.class);
        org.mockito.Mockito.when(mockAgreement.getEmail()).thenReturn(email);
        org.mockito.Mockito.when(mockGovUkPayAgreementDao.findByExternalServiceId(serviceExternalId)).thenReturn(java.util.Optional.of(mockAgreement));
        org.mockito.ArgumentCaptor<java.lang.String> urlCaptor = org.mockito.ArgumentCaptor.forClass(java.lang.String.class);
        org.mockito.Mockito.when(mockNotificationService.sendLiveAccountCreatedEmail(org.mockito.ArgumentMatchers.eq(email), urlCaptor.capture())).thenReturn("random-notify-id");
        sendLiveAccountCreatedEmailService.sendEmail(serviceExternalId);
        org.hamcrest.MatcherAssert.assertThat(urlCaptor.getValue(), org.hamcrest.core.Is.is(((uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailServiceTest.SELFSERVICE_SERVICES_URL + '/') + serviceExternalId) + "/live-account"));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowException_whenAgreementNotSigned() {
        java.lang.String serviceExternalId = "abc123";
        org.mockito.Mockito.when(mockGovUkPayAgreementDao.findByExternalServiceId(serviceExternalId)).thenReturn(java.util.Optional.empty());
        uk.gov.pay.adminusers.exception.GovUkPayAgreementNotSignedException exception = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.GovUkPayAgreementNotSignedException.class, () -> sendLiveAccountCreatedEmailService.sendEmail(serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("Nobody from this service is on record as having agreed to the legal terms"));
    }
}
