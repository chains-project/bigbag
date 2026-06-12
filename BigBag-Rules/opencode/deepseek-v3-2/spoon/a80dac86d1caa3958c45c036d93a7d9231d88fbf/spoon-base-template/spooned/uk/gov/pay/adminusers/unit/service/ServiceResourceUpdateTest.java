package uk.gov.pay.adminusers.unit.service;
@org.junit.jupiter.api.extension.ExtendWith(io.dropwizard.testing.junit5.DropwizardExtensionsSupport.class)
public class ServiceResourceUpdateTest extends uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest {
    private static final java.lang.String API_PATH = "/v1/api/services/%s";

    private static uk.gov.pay.adminusers.persistence.dao.UserDao mockedUserDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.UserDao.class);

    private static uk.gov.pay.adminusers.service.ServiceServicesFactory mockedServicesFactory = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.ServiceServicesFactory.class);

    private static uk.gov.pay.adminusers.service.ServiceUpdater serviceUpdater = new uk.gov.pay.adminusers.service.ServiceUpdater(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao);

    private static uk.gov.pay.adminusers.validations.RequestValidations requestValidations = new uk.gov.pay.adminusers.validations.RequestValidations();

    private static uk.gov.pay.adminusers.resources.ServiceRequestValidator requestValidator = new uk.gov.pay.adminusers.resources.ServiceRequestValidator(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.requestValidations, new uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.requestValidations));

    private static uk.gov.pay.adminusers.service.StripeAgreementService stripeAgreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.StripeAgreementService.class);

    private static uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator payAgreementRequestValidator = new uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.requestValidations);

    private static uk.gov.pay.adminusers.service.GovUkPayAgreementService agreementService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.GovUkPayAgreementService.class);

    private static uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService sendLiveAccountCreatedEmailService = org.mockito.Mockito.mock(uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService.class);

    public static final io.dropwizard.testing.junit5.ResourceExtension RESOURCES = io.dropwizard.testing.junit5.ResourceExtension.builder().addResource(new uk.gov.pay.adminusers.resources.ServiceResource(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.mockedUserDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao, uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.LINKS_BUILDER, uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.requestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.mockedServicesFactory, uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.stripeAgreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.payAgreementRequestValidator, uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.agreementService, uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.sendLiveAccountCreatedEmailService)).build();

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.mockedServicesFactory.serviceUpdater()).thenReturn(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.serviceUpdater);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateExistingEnServiceNameIncludingLegacyName_inSingleObject() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/single-object-replace-service-name-en.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("new-en-name"));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("new-en-name", json);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateExistingEnServiceNameIncludingLegacyName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-en.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("new-en-name"));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("new-en-name", json);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateExistingEnServiceNameAndNonExistingCyServiceName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-en-replace-service-name-cy.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("new-en-name"));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("new-en-name", json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertCyServiceNameJson("new-cy-name", json);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateExistingCyServiceName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "old-cy-name").build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-cy.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("System Generated"));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("System Generated", json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertCyServiceNameJson("new-cy-name", json);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateExistingEnServiceNameAndExistingCyServiceName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "old-en-name").withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "old-cy-name").build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-en-replace-service-name-cy.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("new-en-name"));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("new-en-name", json);
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertCyServiceNameJson("new-cy-name", json);
    }

    @org.junit.jupiter.api.Test
    public void shouldRemoveCyServiceNameWhenReplacedWithBlank() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "old-en-name").withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "old-cy-name").build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-cy-blank.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("name"), org.hamcrest.core.Is.is("old-en-name"));
        uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.assertEnServiceNameJson("old-en-name", json);
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH.toString())));
    }

    @org.junit.jupiter.api.Test
    public void shouldError404_ifServiceExternalIdDoesNotExist() {
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-en.json");
        java.lang.String externalId = "externalId";
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.empty());
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, externalId)).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(404));
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_ifMandatoryFieldValueMissing() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-en-missing-value.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.containsInAnyOrder("Field [value] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_ifMandatoryFieldPathMissing() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-missing-path.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.containsInAnyOrder("Field [path] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_ifmandatoryFieldOpMissing() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-missing-op-service-name-en.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.containsInAnyOrder("Field [op] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenAddGatewayAccountIds_whereNoGatewayAccountIds() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        org.hamcrest.MatcherAssert.assertThat(thisServiceEntity.getGatewayAccountIds(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-add-gateway-account-ids.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.checkIfGatewayAccountsUsed(java.util.Collections.singletonList("1014748185"))).thenReturn(false);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("gateway_account_ids"), org.hamcrest.Matchers.contains("1014748185"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenAddGatewayAccountIds_whereThereIsGatewayAccountIds() {
        uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity gatewayAccountIdEntity = new uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity();
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        gatewayAccountIdEntity.setGatewayAccountId(gatewayAccountId);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withGatewayAccounts(java.util.Collections.singletonList(gatewayAccountIdEntity)).build();
        gatewayAccountIdEntity.setService(thisServiceEntity);
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-add-gateway-account-ids.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(thisServiceEntity.getExternalId())).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.checkIfGatewayAccountsUsed(java.util.Collections.singletonList("1014748185"))).thenReturn(false);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("gateway_account_ids"), org.hamcrest.Matchers.containsInAnyOrder("1014748185", gatewayAccountId));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn409_whenAddGatewayAccountIds_andGatewayAccountId_isUsedByAnotherService() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-add-gateway-account-ids.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(thisServiceEntity.getExternalId())).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.checkIfGatewayAccountsUsed(java.util.Collections.singletonList("1014748185"))).thenReturn(true);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(409));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(json.getList("errors"), org.hamcrest.Matchers.contains("One or more of the following gateway account ids has already assigned to another service: [1014748185]"));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateRedirect_toTrue() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withRedirectToServiceImmediatelyOnTerminalState(false).build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/replace_redirect_immediately_to_true.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("redirect_to_service_immediately_on_terminal_state"), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldFailUpdateRedirect_whenValueIsNotBoolean() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withRedirectToServiceImmediatelyOnTerminalState(false).build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/replace_redirect_immediately_invalid_value.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("errors"), org.hamcrest.core.Is.is(java.util.Collections.singletonList("Field [value] must be a boolean")));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateCollectBillingAddress_toFalse() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCollectBillingAddress(true).build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/replace_collect_billing_address_to_false.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("collect_billing_address"), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldFailUpdateCollectBillingAddress_whenValueIsNotBoolean() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCollectBillingAddress(true).build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/replace_collect_billing_address_invalid_value.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(400));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("errors"), org.hamcrest.core.Is.is(java.util.Collections.singletonList("Field [value] must be a boolean")));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateExperimentalFeaturesEnabled_toTrue() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExperimentalFeaturesEnabled(false).build();
        java.lang.String externalId = thisServiceEntity.getExternalId();
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/replace_experimental_features_enabled_to_true.json");
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.findByExternalId(externalId)).thenReturn(java.util.Optional.of(thisServiceEntity));
        org.mockito.Mockito.when(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.mockedServiceDao.merge(thisServiceEntity)).thenReturn(thisServiceEntity);
        javax.ws.rs.core.Response response = uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.RESOURCES.target(java.lang.String.format(uk.gov.pay.adminusers.unit.service.ServiceResourceUpdateTest.API_PATH, thisServiceEntity.getExternalId())).request().method("PATCH", javax.ws.rs.client.Entity.json(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        java.lang.String body = response.readEntity(java.lang.String.class);
        io.restassured.path.json.JsonPath json = io.restassured.path.json.JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.get("experimental_features_enabled"), org.hamcrest.core.Is.is(true));
    }
}
