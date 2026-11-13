package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceFinderTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private uk.gov.pay.adminusers.service.ServiceFinder serviceFinder;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        serviceFinder = new uk.gov.pay.adminusers.service.ServiceFinder(serviceDao, new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnService_ifFoundByGatewayAccountId() {
        java.lang.String gatewayAccountId = "1";
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.addGatewayAccountIds(gatewayAccountId);
        org.mockito.Mockito.when(serviceDao.findByGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(serviceEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Service> serviceOptional = serviceFinder.byGatewayAccountId(gatewayAccountId);
        org.hamcrest.MatcherAssert.assertThat(serviceOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(serviceOptional.get().getGatewayAccountIds().get(0), org.hamcrest.core.Is.is(gatewayAccountId));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnService_ifFoundByExternalId() {
        java.lang.String gatewayAccountId = "1";
        java.lang.String externalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = new uk.gov.pay.adminusers.persistence.entity.ServiceEntity();
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.addGatewayAccountIds(gatewayAccountId);
        serviceEntity.setExternalId(externalId);
        org.mockito.Mockito.when(serviceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(serviceEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Service> serviceOptional = serviceFinder.byExternalId(externalId);
        org.hamcrest.MatcherAssert.assertThat(serviceOptional.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(serviceOptional.get().getGatewayAccountIds().get(0), org.hamcrest.core.Is.is(gatewayAccountId));
    }

    @java.lang.SuppressWarnings("unchecked")
    @org.junit.jupiter.api.Test
    public void shouldReturnServices_whenSearchingByServiceName() {
        var serviceEntities = uk.gov.pay.adminusers.service.ServiceFinderTest.generateServiceEntities("serv 1", "serv 2");
        org.mockito.Mockito.when(serviceDao.findByENServiceName("serv")).thenReturn(serviceEntities);
        var searchRequest = new uk.gov.pay.adminusers.model.ServiceSearchRequest("serv", "");
        var results = serviceFinder.bySearchRequest(searchRequest);
        var servicesByName = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (results.get("name_results")));
        var servicesByMerchant = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (serviceFinder.bySearchRequest(searchRequest).get("merchant_results")));
        org.hamcrest.MatcherAssert.assertThat(servicesByName.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(servicesByName.stream().map(uk.gov.pay.adminusers.model.Service::getName).collect(java.util.stream.Collectors.toSet()), org.hamcrest.Matchers.containsInAnyOrder("serv 1", "serv 2"));
        org.hamcrest.MatcherAssert.assertThat(servicesByMerchant, org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).findByServiceMerchantName(org.mockito.ArgumentMatchers.anyString());
    }

    @java.lang.SuppressWarnings("unchecked")
    @org.junit.jupiter.api.Test
    public void shouldReturnServices_whenSearchingByServiceMerchantName() {
        var serviceEntities = uk.gov.pay.adminusers.service.ServiceFinderTest.generateServiceEntities("serv 3", "serv 4");
        org.mockito.Mockito.when(serviceDao.findByServiceMerchantName("merchant name")).thenReturn(serviceEntities);
        var searchRequest = new uk.gov.pay.adminusers.model.ServiceSearchRequest("", "merchant name");
        var results = serviceFinder.bySearchRequest(searchRequest);
        var servicesByName = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (results.get("name_results")));
        var servicesByMerchant = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (serviceFinder.bySearchRequest(searchRequest).get("merchant_results")));
        org.hamcrest.MatcherAssert.assertThat(servicesByMerchant.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(servicesByMerchant.stream().map(uk.gov.pay.adminusers.model.Service::getName).collect(java.util.stream.Collectors.toSet()), org.hamcrest.Matchers.containsInAnyOrder("serv 3", "serv 4"));
        org.hamcrest.MatcherAssert.assertThat(servicesByName, org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).findByENServiceName(org.mockito.ArgumentMatchers.anyString());
    }

    @java.lang.SuppressWarnings("unchecked")
    @org.junit.jupiter.api.Test
    public void shouldReturnServices_whenSearchingByServiceNameAndMerchantName() {
        var serviceEntities1 = uk.gov.pay.adminusers.service.ServiceFinderTest.generateServiceEntities("serv 1", "serv 2");
        var serviceEntities2 = uk.gov.pay.adminusers.service.ServiceFinderTest.generateServiceEntities("serv 3", "serv 4");
        org.mockito.Mockito.when(serviceDao.findByENServiceName("serv")).thenReturn(serviceEntities1);
        org.mockito.Mockito.when(serviceDao.findByServiceMerchantName("merchant name")).thenReturn(serviceEntities2);
        var searchRequest = new uk.gov.pay.adminusers.model.ServiceSearchRequest("serv", "merchant name");
        var results = serviceFinder.bySearchRequest(searchRequest);
        var servicesByName = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (results.get("name_results")));
        var servicesByMerchant = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (serviceFinder.bySearchRequest(searchRequest).get("merchant_results")));
        org.hamcrest.MatcherAssert.assertThat(servicesByName.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(servicesByMerchant.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(servicesByName.stream().map(uk.gov.pay.adminusers.model.Service::getName).collect(java.util.stream.Collectors.toSet()), org.hamcrest.Matchers.containsInAnyOrder("serv 1", "serv 2"));
        org.hamcrest.MatcherAssert.assertThat(servicesByMerchant.stream().map(uk.gov.pay.adminusers.model.Service::getName).collect(java.util.stream.Collectors.toSet()), org.hamcrest.Matchers.containsInAnyOrder("serv 3", "serv 4"));
    }

    @java.lang.SuppressWarnings("unchecked")
    @org.junit.jupiter.api.Test
    public void shouldReturnEmptyLists_whenSearchRequestParamsAreBlank() {
        var searchRequest = new uk.gov.pay.adminusers.model.ServiceSearchRequest("", "");
        var results = serviceFinder.bySearchRequest(searchRequest);
        var servicesByName = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (results.get("name_results")));
        var servicesByMerchant = ((java.util.List<uk.gov.pay.adminusers.model.Service>) (serviceFinder.bySearchRequest(searchRequest).get("merchant_results")));
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).findByENServiceName(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).findByServiceMerchantName(org.mockito.ArgumentMatchers.anyString());
        org.hamcrest.MatcherAssert.assertThat(servicesByName, org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
        org.hamcrest.MatcherAssert.assertThat(servicesByMerchant, org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_ifNotFound() {
        java.lang.String gatewayAccountId = "1";
        org.mockito.Mockito.when(serviceDao.findByGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> serviceOptional = serviceFinder.byGatewayAccountId(gatewayAccountId);
        org.hamcrest.MatcherAssert.assertThat(serviceOptional.isPresent(), org.hamcrest.core.Is.is(false));
    }

    private static java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> generateServiceEntities(java.lang.String... names) {
        var serviceEntities = new java.util.ArrayList<uk.gov.pay.adminusers.persistence.entity.ServiceEntity>();
        for (java.lang.String name : names) {
            serviceEntities.add(uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, name).build());
        }
        return serviceEntities;
    }
}
