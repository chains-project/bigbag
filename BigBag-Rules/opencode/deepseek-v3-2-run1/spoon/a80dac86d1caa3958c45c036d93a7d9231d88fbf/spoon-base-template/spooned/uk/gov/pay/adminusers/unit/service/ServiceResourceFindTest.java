package uk.gov.pay.adminusers.unit.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.junit.jupiter.api.extension.ExtendWith(io.dropwizard.testing.junit5.DropwizardExtensionsSupport.class)
public class ServiceResourceFindTest extends uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest {
    private static uk.gov.pay.adminusers.persistence.dao.ServiceDao mockedServiceDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);

    private static uk.gov.pay.adminusers.persistence.dao.UserDao mockedUserDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);

    private static uk.gov.pay.adminusers.service.ServiceServicesFactory mockedServicesFactory = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.ServiceServicesFactory.class);

    private static uk.gov.pay.adminusers.service.ServiceFinder serviceFinder = new uk.gov.pay.adminusers.service.ServiceFinder(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER);

    private static uk.gov.pay.adminusers.resources.ServiceRequestValidator serviceRequestValidator = new uk.gov.pay.adminusers.resources.ServiceRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations(), null);

    private static uk.gov.pay.adminusers.service.StripeAgreementService stripeAgreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.StripeAgreementService.class);

    private static uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator payAgreementRequestValidator = new uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations());

    private static uk.gov.pay.adminusers.service.GovUkPayAgreementService agreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.GovUkPayAgreementService.class);

    private static uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService sendLiveAccountCreatedEmailService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.class);

    public static final io.dropwizard.testing.junit5.ResourceExtension RESOURCES = io.dropwizard.testing.junit5.ResourceExtension.builder().addResource(new uk.gov.pay.adminusers.resources.ServiceResource(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedUserDao, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.serviceRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServicesFactory, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.stripeAgreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.payAgreementRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.agreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.sendLiveAccountCreatedEmailService)).build();

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServicesFactory.serviceFinder()).willReturn(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.serviceFinder);
    }

    @org.junit.jupiter.api.Test
    public void shouldGet_existingServiceById_withDefaultEnNameVariant() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExternalId(serviceExternalId).build();
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao.findByExternalId(serviceExternalId)).willReturn(java.util.Optional.of(serviceEntity));
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target(java.lang.String.format("/v1/api/services/%s", serviceExternalId)).request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), json);
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("cy")));
        assertMerchantDetails(serviceEntity.getMerchantDetailsEntity(), json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(serviceExternalId, json);
        org.hamcrest.MatcherAssert.assertThat(json.get("redirect_to_service_immediately_on_terminal_state"), org.hamcrest.core.Is.is(serviceEntity.isRedirectToServiceImmediatelyOnTerminalState()));
        org.hamcrest.MatcherAssert.assertThat(json.get("collect_billing_address"), org.hamcrest.core.Is.is(serviceEntity.isCollectBillingAddress()));
        org.hamcrest.MatcherAssert.assertThat(json.get("default_billing_address_country"), org.hamcrest.core.Is.is(serviceEntity.getDefaultBillingAddressCountry()));
        org.hamcrest.MatcherAssert.assertThat(json.get("current_go_live_stage"), org.hamcrest.core.Is.is(java.lang.String.valueOf(serviceEntity.getCurrentGoLiveStage())));
    }

    @org.junit.jupiter.api.Test
    public void shouldGetServiceById_withServiceNameVariantForCy() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExternalId(serviceExternalId).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME).build();
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao.findByExternalId(serviceExternalId)).willReturn(java.util.Optional.of(serviceEntity));
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target(java.lang.String.format("/v1/api/services/%s", serviceExternalId)).request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertCyServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME, json);
        assertMerchantDetails(serviceEntity.getMerchantDetailsEntity(), json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(serviceExternalId, json);
    }

    @org.junit.jupiter.api.Test
    public void shouldGetServiceById_withServiceNameVariantsForEn_andCy() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExternalId(serviceExternalId).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME).build();
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao.findByExternalId(serviceExternalId)).willReturn(java.util.Optional.of(serviceEntity));
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target(java.lang.String.format("/v1/api/services/%s", serviceExternalId)).request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertCyServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME, json);
        assertMerchantDetails(serviceEntity.getMerchantDetailsEntity(), json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(serviceExternalId, json);
    }

    @org.junit.jupiter.api.Test
    public void shouldFind_existingServiceByGatewayAccountId() {
        uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity gatewayAccountIdEntity = new uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity();
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        gatewayAccountIdEntity.setGatewayAccountId(gatewayAccountId);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withGatewayAccounts(java.util.Collections.singletonList(gatewayAccountIdEntity)).withRedirectToServiceImmediatelyOnTerminalState(true).withCreatedDate(java.time.ZonedDateTime.parse("2020-01-31T12:30:00Z")).withWentLiveDate(java.time.ZonedDateTime.parse("2020-02-01T09:00:00Z")).withSector("police").build();
        gatewayAccountIdEntity.setService(serviceEntity);
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao.findByGatewayAccountId(gatewayAccountId)).willReturn(java.util.Optional.of(serviceEntity));
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target("/v1/api/services").queryParam("gatewayAccountId", gatewayAccountId).request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), json);
        assertMerchantDetails(serviceEntity.getMerchantDetailsEntity(), json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(serviceEntity.getExternalId(), json);
        org.hamcrest.MatcherAssert.assertThat(json.get("redirect_to_service_immediately_on_terminal_state"), org.hamcrest.core.Is.is(serviceEntity.isRedirectToServiceImmediatelyOnTerminalState()));
        org.hamcrest.MatcherAssert.assertThat(json.get("collect_billing_address"), org.hamcrest.core.Is.is(serviceEntity.isCollectBillingAddress()));
        org.hamcrest.MatcherAssert.assertThat(json.get("default_billing_address_country"), org.hamcrest.core.Is.is(serviceEntity.getDefaultBillingAddressCountry()));
        org.hamcrest.MatcherAssert.assertThat(json.get("current_go_live_stage"), org.hamcrest.core.Is.is(java.lang.String.valueOf(serviceEntity.getCurrentGoLiveStage())));
        org.hamcrest.MatcherAssert.assertThat(json.get("created_date"), org.hamcrest.core.Is.is("2020-01-31T12:30:00.000Z"));
        org.hamcrest.MatcherAssert.assertThat(json.get("went_live_date"), org.hamcrest.core.Is.is("2020-02-01T09:00:00.000Z"));
        org.hamcrest.MatcherAssert.assertThat(json.get("sector"), org.hamcrest.core.Is.is("police"));
        org.hamcrest.MatcherAssert.assertThat(json.get("internal"), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(json.get("archived"), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenFindByGatewayAccountId_ifNotFound() {
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao.findByGatewayAccountId(gatewayAccountId)).willReturn(java.util.Optional.empty());
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target("/v1/api/services").queryParam("gatewayAccountId", gatewayAccountId).request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(404));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenGetServiceById_ifNotFound() {
        java.lang.String externalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.mockedServiceDao.findByExternalId(externalId)).willReturn(java.util.Optional.empty());
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target(java.lang.String.format("/v1/api/services/%s", externalId)).request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(404));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnBadRequest_whenGetByGatewayAccountId_isMissingQueryParam() {
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceFindTest.RESOURCES.target("/v1/api/services").queryParam("gatewayAccountId", "").request().get();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath jsonPath = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(jsonPath.getList("errors"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.getList("errors").get(0), org.hamcrest.core.Is.is("Find services currently support only by gatewayAccountId"));
    }

    private void assertMerchantDetails(uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails, io.restassured.path.json.JsonPath jsonPath) {
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.address_line1"), org.hamcrest.core.Is.is(merchantDetails.getAddressLine1()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.address_line2"), org.hamcrest.core.Is.is(merchantDetails.getAddressLine2()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.address_country"), org.hamcrest.core.Is.is(merchantDetails.getAddressCountryCode()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.address_postcode"), org.hamcrest.core.Is.is(merchantDetails.getAddressPostcode()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.address_city"), org.hamcrest.core.Is.is(merchantDetails.getAddressCity()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.telephone_number"), org.hamcrest.core.Is.is(merchantDetails.getTelephoneNumber()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.email"), org.hamcrest.core.Is.is(merchantDetails.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(jsonPath.get("merchant_details.name"), org.hamcrest.core.Is.is(merchantDetails.getName()));
    }
}
