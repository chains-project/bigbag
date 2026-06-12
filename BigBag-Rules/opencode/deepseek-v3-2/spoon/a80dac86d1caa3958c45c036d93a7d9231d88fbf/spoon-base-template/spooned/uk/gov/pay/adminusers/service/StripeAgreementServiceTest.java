package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class StripeAgreementServiceTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.StripeAgreementDao mockedStripeAgreementDao;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao mockedServiceDao;

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity> stripeAgreementEntityArgumentCaptor;

    private uk.gov.pay.adminusers.service.StripeAgreementService stripeAgreementService;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        stripeAgreementService = new uk.gov.pay.adminusers.service.StripeAgreementService(mockedStripeAgreementDao, mockedServiceDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnStripeAgreement() {
        java.lang.String serviceExternalId = "abc123";
        java.lang.String ipAddress = "192.0.2.0";
        java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity mockServiceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity stripeAgreementEntity = new uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity(mockServiceEntity, ipAddress, agreementTime);
        org.mockito.Mockito.when(mockedStripeAgreementDao.findByServiceExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(stripeAgreementEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.StripeAgreement> maybeStripeAgreement = stripeAgreementService.findStripeAgreementByServiceId(serviceExternalId);
        org.junit.jupiter.api.Assertions.assertTrue(maybeStripeAgreement.isPresent());
        org.hamcrest.MatcherAssert.assertThat(maybeStripeAgreement.get().getIpAddress().getHostAddress(), org.hamcrest.core.Is.is(ipAddress));
        org.hamcrest.MatcherAssert.assertThat(maybeStripeAgreement.get().getAgreementTime(), org.hamcrest.core.Is.is(agreementTime));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateNewStripeAgreement() throws java.net.UnknownHostException {
        java.lang.String serviceExternalId = "abc123";
        java.lang.String ipAddress = "192.0.2.0";
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity mockServiceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(mockedServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(mockServiceEntity));
        stripeAgreementService.doCreate(serviceExternalId, java.net.InetAddress.getByName(ipAddress));
        org.mockito.Mockito.verify(mockedStripeAgreementDao, org.mockito.Mockito.times(1)).persist(stripeAgreementEntityArgumentCaptor.capture());
        org.hamcrest.MatcherAssert.assertThat(stripeAgreementEntityArgumentCaptor.getValue().getIpAddress(), org.hamcrest.core.Is.is(ipAddress));
        org.hamcrest.MatcherAssert.assertThat(stripeAgreementEntityArgumentCaptor.getValue().getService(), org.hamcrest.core.Is.is(mockServiceEntity));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowException_whenServiceDoesNotExist() {
        java.lang.String serviceExternalId = "abc123";
        org.mockito.Mockito.when(mockedServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ServiceNotFoundException.class, () -> stripeAgreementService.doCreate(serviceExternalId, java.net.InetAddress.getByName("192.0.2.0")));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowException_whenStripeAgreementAlreadyExists() throws java.net.UnknownHostException {
        java.lang.String serviceExternalId = "abc123";
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity mockServiceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(mockedServiceDao.findByExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(mockServiceEntity));
        uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity mockStripeAgreementEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity.class);
        org.mockito.Mockito.when(mockedStripeAgreementDao.findByServiceExternalId(serviceExternalId)).thenReturn(java.util.Optional.of(mockStripeAgreementEntity));
        uk.gov.pay.adminusers.exception.StripeAgreementExistsException exception = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.StripeAgreementExistsException.class, () -> stripeAgreementService.doCreate(serviceExternalId, java.net.InetAddress.getByName("192.0.2.0")));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("Stripe agreement information is already stored for this service"));
    }
}
