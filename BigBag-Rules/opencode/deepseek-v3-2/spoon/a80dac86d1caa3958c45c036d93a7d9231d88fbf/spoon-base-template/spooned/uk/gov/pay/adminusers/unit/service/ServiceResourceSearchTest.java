package uk.gov.pay.adminusers.unit.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.junit.jupiter.api.extension.ExtendWith(io.dropwizard.testing.junit5.DropwizardExtensionsSupport.class)
public class ServiceResourceSearchTest extends uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest {
    // mocks
    private static final uk.gov.pay.adminusers.persistence.dao.ServiceDao mockServiceDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);

    private static final uk.gov.pay.adminusers.persistence.dao.UserDao mockUserDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);

    private static final uk.gov.pay.adminusers.service.StripeAgreementService mockStripeAgreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.StripeAgreementService.class);

    private static final uk.gov.pay.adminusers.service.GovUkPayAgreementService mockAgreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.GovUkPayAgreementService.class);

    private static final uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService mockSendLiveAccountCreatedEmailService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.class);

    private static final uk.gov.pay.adminusers.service.ServiceServicesFactory mockedServicesFactory = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.ServiceServicesFactory.class);

    private static final uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator mockPayAgreementRequestValidator = org.mockito.Mockito.mock(uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator.class);

    // --
    private static final uk.gov.pay.adminusers.resources.ServiceRequestValidator serviceRequestValidator = new uk.gov.pay.adminusers.resources.ServiceRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations(), null);

    private static final uk.gov.pay.adminusers.service.ServiceFinder serviceFinder = new uk.gov.pay.adminusers.service.ServiceFinder(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER);

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public static final io.dropwizard.testing.junit5.ResourceExtension underTest = io.dropwizard.testing.junit5.ResourceExtension.builder().addResource(new uk.gov.pay.adminusers.resources.ServiceResource(uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockUserDao, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.serviceRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockedServicesFactory, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockStripeAgreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockPayAgreementRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockAgreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockSendLiveAccountCreatedEmailService)).build();

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockedServicesFactory.serviceFinder()).willReturn(uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.serviceFinder);
    }

    @org.junit.jupiter.api.Test
    public void shouldOK_andReturnServices_whenMatches() throws com.fasterxml.jackson.core.JsonProcessingException {
        var payload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/post/service-search-request.json");
        var serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        var merchantDetailsEntity = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder.aMerchantDetailsEntity().withName("Government Bakery Office").build();
        var serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExternalId(serviceExternalId).withMerchantDetailsEntity(merchantDetailsEntity).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "GOV.UK Cake Service").build();
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByENServiceName("cake")).willReturn(java.util.List.of(serviceEntity));
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByServiceMerchantName("bakery")).willReturn(java.util.List.of(serviceEntity));
        var response = uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.underTest.target("/v1/api/services/search").request().post(javax.ws.rs.client.Entity.json(payload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        var json = uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mapper.readTree(response.readEntity(java.lang.String.class));
        org.hamcrest.MatcherAssert.assertThat(json.get("name_results").size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(json.get("merchant_results").size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(json.get("name_results").get(0).get("external_id").asText(), org.hamcrest.core.Is.is(serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(json.get("name_results").get(0).get("name").asText(), org.hamcrest.core.Is.is("GOV.UK Cake Service"));
        org.hamcrest.MatcherAssert.assertThat(json.get("merchant_results").get(0).get("external_id").asText(), org.hamcrest.core.Is.is(serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(json.get("merchant_results").get(0).get("merchant_details").get("name").asText(), org.hamcrest.core.Is.is("Government Bakery Office"));
    }

    @org.junit.jupiter.api.Test
    public void shouldOK_andReturnEmptyResult_whenNoMatches() throws com.fasterxml.jackson.core.JsonProcessingException {
        var payload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/post/service-search-request.json");
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByENServiceName("cake")).willReturn(java.util.Collections.emptyList());
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByServiceMerchantName("bakery")).willReturn(java.util.Collections.emptyList());
        var response = uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.underTest.target("/v1/api/services/search").request().post(javax.ws.rs.client.Entity.json(payload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        var json = uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mapper.readTree(response.readEntity(java.lang.String.class));
        org.hamcrest.MatcherAssert.assertThat(json.get("name_results").size(), org.hamcrest.core.Is.is(0));
        org.hamcrest.MatcherAssert.assertThat(json.get("merchant_results").size(), org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_whenRequestValidationFails() throws com.fasterxml.jackson.core.JsonProcessingException {
        var payload = "{\"service_name\": \"!@£$%^\", \"service_merchant_name\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}";
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.underTest.target("/v1/api/services/search").request().post(javax.ws.rs.client.Entity.json(payload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        com.fasterxml.jackson.databind.JsonNode json = uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mapper.readTree(response.readEntity(java.lang.String.class));
        org.hamcrest.MatcherAssert.assertThat(json.get("errors").size(), org.hamcrest.core.Is.is(2));
        var errors = java.util.List.of(json.get("errors").get(0).asText(), json.get("errors").get(1).asText());
        errors.forEach(err -> org.hamcrest.MatcherAssert.assertThat(err, org.hamcrest.CoreMatchers.anyOf(org.hamcrest.CoreMatchers.equalTo(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_LENGTH_ERR_MSG), org.hamcrest.CoreMatchers.equalTo(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_SPECIAL_CHARS_ERR_MSG))));
        org.mockito.Mockito.verify(uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockServiceDao, org.mockito.Mockito.never()).findByServiceMerchantName(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(uk.gov.pay.adminusers.unit.service.ServiceResourceSearchTest.mockServiceDao, org.mockito.Mockito.never()).findByENServiceName(org.mockito.ArgumentMatchers.anyString());
    }
}
