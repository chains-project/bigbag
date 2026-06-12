package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceCreatorTest {
    private static final java.lang.String EN_SERVICE_NAME = "en-service-name";

    private static final java.lang.String CY_SERVICE_NAME = "cy-service-name";

    private static final java.lang.String BASE_URL = "http://localhost";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao mockedServiceDao;

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> persistedServiceEntity;

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<java.util.List<java.lang.String>> listArgumentCaptor;

    private uk.gov.pay.adminusers.service.ServiceCreator serviceCreator;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceCreator = new uk.gov.pay.adminusers.service.ServiceCreator(mockedServiceDao, new uk.gov.pay.adminusers.service.LinksBuilder(uk.gov.pay.adminusers.service.ServiceCreatorTest.BASE_URL));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenProvidedWith_noParameters() {
        uk.gov.pay.adminusers.model.Service service = serviceCreator.doCreate(java.util.Collections.emptyList(), java.util.Collections.emptyMap());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.never()).checkIfGatewayAccountsUsed(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).persist(persistedServiceEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(service.getName(), org.hamcrest.core.Is.is("System Generated"));
        org.hamcrest.MatcherAssert.assertThat(service.isRedirectToServiceImmediatelyOnTerminalState(), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(service.isCollectBillingAddress(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(service.getDefaultBillingAddressCountry(), org.hamcrest.core.Is.is("GB"));
        java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> persistedGatewayIds = persistedServiceEntity.getValue().getGatewayAccountIds();
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds.size(), org.hamcrest.core.Is.is(0));
        assertEnServiceNameMap(service, "System Generated");
        assertSelfLink(service);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenProvidedWith_onlyAValidName() {
        uk.gov.pay.adminusers.model.Service service = serviceCreator.doCreate(java.util.Collections.emptyList(), java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME));
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.never()).checkIfGatewayAccountsUsed(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).persist(persistedServiceEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(service.getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME));
        java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> persistedGatewayIds = persistedServiceEntity.getValue().getGatewayAccountIds();
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds.size(), org.hamcrest.core.Is.is(0));
        assertEnServiceNameMap(service, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME);
        assertSelfLink(service);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenProvidedWith_multipleValidNames_andNoGatewayAccountIds() {
        java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> serviceNames = new java.util.HashMap<>();
        serviceNames.put(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME);
        serviceNames.put(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.service.ServiceCreatorTest.CY_SERVICE_NAME);
        uk.gov.pay.adminusers.model.Service service = serviceCreator.doCreate(java.util.Collections.emptyList(), serviceNames);
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.never()).checkIfGatewayAccountsUsed(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).persist(persistedServiceEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(service.getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME));
        java.util.List<uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity> persistedGatewayIds = persistedServiceEntity.getValue().getGatewayAccountIds();
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds.size(), org.hamcrest.core.Is.is(0));
        assertEnServiceNameMap(service, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME);
        org.hamcrest.MatcherAssert.assertThat(service.getServiceNames(), org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH.toString()));
        org.hamcrest.MatcherAssert.assertThat(service.getServiceNames(), org.hamcrest.Matchers.hasValue(uk.gov.pay.adminusers.service.ServiceCreatorTest.CY_SERVICE_NAME));
        assertSelfLink(service);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenProvidedWith_unassignedGatewayId() {
        java.lang.String gatewayAccountId2 = "gatewayAccountId2";
        java.lang.String gatewayAccountId1 = "gatewayAccountId1";
        uk.gov.pay.adminusers.model.Service service = serviceCreator.doCreate(java.util.List.of(gatewayAccountId1, gatewayAccountId2), java.util.Collections.emptyMap());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).checkIfGatewayAccountsUsed(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).persist(persistedServiceEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(service.getName(), org.hamcrest.core.Is.is("System Generated"));
        java.util.List<java.lang.String> persistedGatewayIds = persistedServiceEntity.getValue().getGatewayAccountIds().stream().map(uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity::getGatewayAccountId).collect(java.util.stream.Collectors.toUnmodifiableList());
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds, org.hamcrest.Matchers.hasItems(gatewayAccountId1, gatewayAccountId2));
        org.hamcrest.MatcherAssert.assertThat(service.getGatewayAccountIds(), org.hamcrest.Matchers.hasItems(gatewayAccountId1, gatewayAccountId2));
        assertEnServiceNameMap(service, "System Generated");
        assertSelfLink(service);
        org.mockito.Mockito.verify(mockedServiceDao).checkIfGatewayAccountsUsed(listArgumentCaptor.capture());
        java.util.List<java.lang.String> gatewayAccounts = listArgumentCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(gatewayAccounts.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(gatewayAccounts, org.hamcrest.Matchers.containsInAnyOrder(gatewayAccountId1, gatewayAccountId2));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenProvidedWith_validName_AndUnassignedGatewayId() {
        java.lang.String gatewayAccountId2 = "gatewayAccountId2";
        java.lang.String gatewayAccountId1 = "gatewayAccountId1";
        uk.gov.pay.adminusers.model.Service service = serviceCreator.doCreate(java.util.List.of(gatewayAccountId1, gatewayAccountId2), java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME));
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).checkIfGatewayAccountsUsed(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(mockedServiceDao, org.mockito.Mockito.times(1)).persist(persistedServiceEntity.capture());
        org.hamcrest.MatcherAssert.assertThat(service.getName(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME));
        java.util.List<java.lang.String> persistedGatewayIds = persistedServiceEntity.getValue().getGatewayAccountIds().stream().map(uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity::getGatewayAccountId).collect(java.util.stream.Collectors.toUnmodifiableList());
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(persistedGatewayIds, org.hamcrest.Matchers.hasItems(gatewayAccountId1, gatewayAccountId1));
        org.hamcrest.MatcherAssert.assertThat(service.getGatewayAccountIds(), org.hamcrest.Matchers.hasItems(gatewayAccountId1, gatewayAccountId2));
        assertEnServiceNameMap(service, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME);
        assertSelfLink(service);
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenProvidedAConflictingGatewayID() {
        java.util.List<java.lang.String> gatewayAccountsIds = java.util.List.of("3");
        org.mockito.Mockito.when(mockedServiceDao.checkIfGatewayAccountsUsed(gatewayAccountsIds)).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> serviceCreator.doCreate(gatewayAccountsIds, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.service.ServiceCreatorTest.EN_SERVICE_NAME)));
    }

    private void assertEnServiceNameMap(uk.gov.pay.adminusers.model.Service service, java.lang.String serviceName) {
        org.hamcrest.MatcherAssert.assertThat(service.getServiceNames(), org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString()));
        org.hamcrest.MatcherAssert.assertThat(service.getServiceNames(), org.hamcrest.Matchers.hasValue(serviceName));
    }

    private void assertSelfLink(uk.gov.pay.adminusers.model.Service service) {
        org.hamcrest.MatcherAssert.assertThat(service.getLinks(), org.hamcrest.Matchers.hasSize(1));
        uk.gov.pay.adminusers.model.Link selfLink = service.getLinks().get(0);
        org.hamcrest.MatcherAssert.assertThat(selfLink.getRel(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.Link.Rel.SELF));
        org.hamcrest.MatcherAssert.assertThat(selfLink.getMethod(), org.hamcrest.core.Is.is(javax.ws.rs.HttpMethod.GET));
        org.hamcrest.MatcherAssert.assertThat(selfLink.getHref(), org.hamcrest.core.Is.is((uk.gov.pay.adminusers.service.ServiceCreatorTest.BASE_URL + "/v1/api/services/") + service.getExternalId()));
    }
}
