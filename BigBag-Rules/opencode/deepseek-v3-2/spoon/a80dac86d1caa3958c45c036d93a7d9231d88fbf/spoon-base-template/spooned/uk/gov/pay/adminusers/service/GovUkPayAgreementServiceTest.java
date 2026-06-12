package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class GovUkPayAgreementServiceTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao mockedAgreementDao;

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity> argumentCaptor;

    private uk.gov.pay.adminusers.service.GovUkPayAgreementService agreementService;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        agreementService = new uk.gov.pay.adminusers.service.GovUkPayAgreementService(mockedAgreementDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnGovUkPayAgreement() {
        java.lang.String serviceExternalId = "abcd1234";
        java.lang.String email = "someone@example.com";
        java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity entity = new uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity(email, agreementTime);
        org.mockito.Mockito.when(mockedAgreementDao.findByExternalServiceId(serviceExternalId)).thenReturn(java.util.Optional.of(entity));
        java.util.Optional<uk.gov.pay.adminusers.model.GovUkPayAgreement> optionalGovUkPayAgreement = agreementService.findGovUkPayAgreementByServiceId(serviceExternalId);
        org.hamcrest.MatcherAssert.assertThat(optionalGovUkPayAgreement.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalGovUkPayAgreement.get().getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(optionalGovUkPayAgreement.get().getAgreementTime(), org.hamcrest.core.Is.is(agreementTime));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateNewGovUkPayAgreement() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        java.lang.String email = "someone@example.com";
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        uk.gov.pay.adminusers.model.GovUkPayAgreement agreement = agreementService.doCreate(serviceEntity, email, now);
        org.mockito.Mockito.verify(mockedAgreementDao).persist(argumentCaptor.capture());
        org.hamcrest.MatcherAssert.assertThat(argumentCaptor.getValue().getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(agreement.getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(agreement.getAgreementTime(), org.hamcrest.core.Is.is(now));
    }
}
