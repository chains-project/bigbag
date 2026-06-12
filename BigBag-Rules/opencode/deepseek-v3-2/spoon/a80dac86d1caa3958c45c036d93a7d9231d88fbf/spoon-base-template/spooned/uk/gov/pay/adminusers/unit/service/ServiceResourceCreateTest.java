package uk.gov.pay.adminusers.unit.service;
@org.junit.jupiter.api.extension.ExtendWith(io.dropwizard.testing.junit5.DropwizardExtensionsSupport.class)
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceResourceCreateTest extends uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest {
    private static final java.util.Map<java.lang.String, java.lang.Object> PAYLOAD_MAP = new java.util.HashMap<>();

    private static uk.gov.pay.adminusers.persistence.dao.UserDao mockedUserDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);

    private static uk.gov.pay.adminusers.service.ServiceServicesFactory mockedServicesFactory = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.ServiceServicesFactory.class);

    private static uk.gov.pay.adminusers.service.ServiceCreator serviceCreator = new uk.gov.pay.adminusers.service.ServiceCreator(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER);

    private static uk.gov.pay.adminusers.service.ServiceCreator mockedServiceCreator = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.ServiceCreator.class);

    private static uk.gov.pay.adminusers.validations.RequestValidations requestValidations = new uk.gov.pay.adminusers.validations.RequestValidations();

    private static uk.gov.pay.adminusers.resources.ServiceRequestValidator serviceRequestValidator = new uk.gov.pay.adminusers.resources.ServiceRequestValidator(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.requestValidations, new uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.requestValidations));

    private static uk.gov.pay.adminusers.service.StripeAgreementService stripeAgreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.StripeAgreementService.class);

    private static uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator payAgreementRequestValidator = new uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.requestValidations);

    private static uk.gov.pay.adminusers.service.GovUkPayAgreementService agreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.GovUkPayAgreementService.class);

    private static uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService sendLiveAccountCreatedEmailService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.class);

    public static final io.dropwizard.testing.junit5.ResourceExtension RESOURCES = io.dropwizard.testing.junit5.ResourceExtension.builder().addResource(new uk.gov.pay.adminusers.resources.ServiceResource(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedUserDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER, uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.serviceRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServicesFactory, uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.stripeAgreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.payAgreementRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.agreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.sendLiveAccountCreatedEmailService)).build();

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntityArgumentCaptor;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServicesFactory.serviceCreator()).willReturn(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator);
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        org.mockito.Mockito.reset(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao);
        org.mockito.Mockito.reset(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator);
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.clear();
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenCreateAServiceWithoutParameters() {
        uk.gov.pay.adminusers.model.Service service = buildService(java.util.Collections.emptyList(), java.util.Collections.emptyMap());
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator.doCreate(java.util.Collections.emptyList(), java.util.Collections.emptyMap())).willReturn(service);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(201));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("System Generated"));
        org.hamcrest.MatcherAssert.assertThat(json.get("external_id"), org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(json.get("redirect_to_service_immediately_on_terminal_state"), org.hamcrest.core.Is.is(false));
        org.hamcrest.MatcherAssert.assertThat(json.get("collect_billing_address"), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(json.get("default_billing_address_country"), org.hamcrest.core.Is.is("GB"));
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("cy")));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("System Generated", json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(json.get("external_id"), json);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenCreateAServiceWithNameOnly() {
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put("service_name", java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString(), uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        uk.gov.pay.adminusers.model.Service service = buildService(java.util.Collections.emptyList(), java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator.doCreate(java.util.Collections.emptyList(), java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME))).willReturn(service);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(201));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("external_id"), org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, json);
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("cy")));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(json.get("external_id"), json);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenCreateAServiceWithEnglishNameOnly() {
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put("service_name", java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString(), uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        uk.gov.pay.adminusers.model.Service service = buildService(java.util.Collections.emptyList(), java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator.doCreate(java.util.Collections.emptyList(), java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME))).willReturn(service);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(201));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("external_id"), org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, json);
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("cy")));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(json.get("external_id"), json);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenCreateAServiceWithName_andGatewayAccountIds() {
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put("service_name", java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString(), uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        java.lang.String anotherGatewayAccountId = "another-gateway-account-id";
        java.util.List<java.lang.String> gatewayAccounts = java.util.Arrays.asList(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.GATEWAY_ACCOUNT_ID, anotherGatewayAccountId);
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS, gatewayAccounts);
        uk.gov.pay.adminusers.model.Service service = buildService(gatewayAccounts, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator.doCreate(gatewayAccounts, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME))).willReturn(service);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(201));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(json.get("external_id"), org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue()));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, json);
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("cy")));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(json.get("external_id"), json);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenCreateAServiceWithEnglishName_andGatewayAccountIds() {
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put("service_name", java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString(), uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        java.lang.String anotherGatewayAccountId = "another-gateway-account-id";
        java.util.List<java.lang.String> gatewayAccounts = java.util.List.of(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.GATEWAY_ACCOUNT_ID, anotherGatewayAccountId);
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS, gatewayAccounts);
        uk.gov.pay.adminusers.model.Service service = buildService(gatewayAccounts, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator.doCreate(gatewayAccounts, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME))).willReturn(service);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(201));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(json.get("external_id"), org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue()));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, json);
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("cy")));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(json.get("external_id"), json);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenCreateAServiceWithName_andGatewayAccountIds_andServiceNameVariants_englishAndCymru() {
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put(uk.gov.pay.adminusers.resources.ServiceResource.FIELD_NAME, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME);
        java.lang.String anotherGatewayAccountId = "another-gateway-account-id";
        java.util.List<java.lang.String> gatewayAccounts = java.util.List.of(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.GATEWAY_ACCOUNT_ID, anotherGatewayAccountId);
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS, gatewayAccounts);
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put("service_name", java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString(), uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, uk.gov.service.payments.commons.model.SupportedLanguage.WELSH.toString(), uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME));
        java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> serviceName = java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME);
        uk.gov.pay.adminusers.model.Service service = buildService(gatewayAccounts, serviceName);
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServiceCreator.doCreate(gatewayAccounts, serviceName)).willReturn(service);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(201));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(json.get("external_id"), org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue()));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME, json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertCyServiceNameJson(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.CY_SERVICE_NAME, json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertLinks(json.get("external_id"), json);
        org.hamcrest.MatcherAssert.assertThat(json.getList("gateway_account_ids"), org.hamcrest.Matchers.hasSize(2));
        org.hamcrest.MatcherAssert.assertThat(json.getList("gateway_account_ids"), org.hamcrest.Matchers.containsInAnyOrder(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.GATEWAY_ACCOUNT_ID, anotherGatewayAccountId));
    }

    @org.junit.jupiter.api.Test
    public void shouldError409_whenGatewayAccountsAreAlreadyAssignedToAService() {
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put(uk.gov.pay.adminusers.resources.ServiceResource.FIELD_NAME, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.EN_SERVICE_NAME);
        uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP.put(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS, java.util.Collections.singletonList(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.GATEWAY_ACCOUNT_ID));
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.mockedServicesFactory.serviceCreator()).willReturn(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.serviceCreator);
        org.mockito.BDDMockito.given(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.checkIfGatewayAccountsUsed(org.mockito.ArgumentMatchers.anyList())).willReturn(true);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.RESOURCES.target(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE).request(javax.ws.rs.core.MediaType.APPLICATION_JSON).post(javax.ws.rs.client.Entity.json(uk.gov.pay.adminusers.unit.service.ServiceResourceCreateTest.PAYLOAD_MAP), javax.ws.rs.core.Response.class);
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(409));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.containsInAnyOrder("One or more of the following gateway account ids has already assigned to another service: [some-gateway-account-id]"));
        org.mockito.Mockito.verify(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao, org.mockito.Mockito.never()).persist(serviceEntityArgumentCaptor.capture());
    }

    private uk.gov.pay.adminusers.model.Service buildService(java.util.List<java.lang.String> gatewayAccountIds, java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> serviceNames) {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntity.from(uk.gov.pay.adminusers.model.Service.from());
        serviceNames.forEach((language, name) -> serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(language, name)));
        serviceEntity.addGatewayAccountIds(gatewayAccountIds.toArray(new java.lang.String[0]));
        return uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER.decorate(serviceEntity.toService());
    }
}
