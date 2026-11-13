package uk.gov.pay.adminusers.pact.queuemessage;
public class DisputeCreatedEventQueueConsumerIT {
    @org.junit.Rule
    public au.com.dius.pact.consumer.MessagePactProviderRule mockProvider = new au.com.dius.pact.consumer.MessagePactProviderRule(this);

    @org.junit.Rule
    public uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule adminusersApp = new uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule(io.dropwizard.testing.ConfigOverride.config("eventSubscriberQueue.eventSubscriberQueueEnabled", "true"));

    @org.junit.Rule
    public com.github.tomakehurst.wiremock.junit.WireMockRule wireMockRule = new com.github.tomakehurst.wiremock.junit.WireMockRule(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().port(adminusersApp.getWireMockPort()));

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private byte[] currentMessage;

    private final long amount = 6500L;

    private final java.lang.String reason = "duplicate";

    private final java.lang.String gatewayAccountId = "a-gateway-account-id";

    private final java.lang.String resourceExternalId = "dispute-id";

    private final java.lang.String parentResourceExternalId = "payment-id";

    private final java.lang.String reference = "REF123";

    private final java.lang.String serviceId = "service-id";

    private final java.lang.String serviceName = "A service";

    private final java.lang.String organisationName = "organisation name";

    private final java.lang.String adminUserEmail = "user@example.com";

    private java.lang.String evidenceDueDate = "2022-02-14T23:59:59.000Z";

    private uk.gov.pay.adminusers.fixtures.EventFixture eventFixture;

    private uk.gov.pay.adminusers.infra.LedgerStub ledgerStub;

    private uk.gov.pay.adminusers.infra.NotifyStub notifyStub;

    @au.com.dius.pact.consumer.Pact(provider = "connector", consumer = "adminusers")
    public au.com.dius.pact.model.v3.messaging.MessagePact createDisputeCreatedEventPact(au.com.dius.pact.consumer.MessagePactBuilder builder) {
        com.fasterxml.jackson.databind.JsonNode eventDetails = uk.gov.pay.adminusers.pact.queuemessage.DisputeCreatedEventQueueConsumerIT.objectMapper.valueToTree(java.util.Map.of("gateway_account_id", gatewayAccountId, "amount", amount, "reason", reason, "evidence_due_date", evidenceDueDate));
        eventFixture = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withLive(true).withResourceExternalId(resourceExternalId).withParentResourceExternalId(parentResourceExternalId).withServiceId(serviceId).withEventType("DISPUTE_CREATED").withEventDetails(eventDetails);
        java.util.Map<java.lang.String, java.lang.String> metadata = new java.util.HashMap<>();
        metadata.put("contentType", "application/json");
        return builder.expectsToReceive("a dispute created event").withMetadata(metadata).withContent(eventFixture.getAsPact()).toPact();
    }

    @org.junit.Before
    public void setUp() throws java.lang.Exception {
        adminusersApp.getDatabaseTestHelper().truncateAllData();
        ledgerStub = new uk.gov.pay.adminusers.infra.LedgerStub(wireMockRule);
        notifyStub = new uk.gov.pay.adminusers.infra.NotifyStub(wireMockRule);
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(adminusersApp.getDatabaseTestHelper()).withGatewayAccountIds(gatewayAccountId).withName(serviceName).withMerchantDetails(new uk.gov.pay.adminusers.model.MerchantDetails(organisationName, "number", "line1", null, "city", "postcode", "country", "dd-merchant@example.com", "https://merchant.example.org")).insertService();
        uk.gov.pay.adminusers.model.Role adminRole = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(adminusersApp.getDatabaseTestHelper()).insertAdmin();
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(adminusersApp.getDatabaseTestHelper()).withEmail(adminUserEmail).withServiceRole(service.getId(), adminRole.getId()).insertUser();
        uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction ledgerTransaction = uk.gov.pay.adminusers.fixtures.LedgerTransactionFixture.aLedgerTransactionFixture().withTransactionId(parentResourceExternalId).withReference(reference).build();
        ledgerStub.returnLedgerTransaction(parentResourceExternalId, ledgerTransaction);
        notifyStub.stubSendEmail();
    }

    @org.junit.Test
    @au.com.dius.pact.consumer.PactVerification({ "connector" })
    public void test() throws java.lang.Exception {
        java.lang.String messageContents = new java.lang.String(currentMessage);
        java.lang.String snsMessage = new com.google.gson.GsonBuilder().create().toJson(java.util.Map.of("Message", messageContents));
        adminusersApp.getSqsClient().sendMessage(uk.gov.pay.adminusers.infra.SqsTestDocker.getQueueUrl("event-queue"), snsMessage);
        org.testcontainers.shaded.org.awaitility.Awaitility.await().atMost(2, java.util.concurrent.TimeUnit.SECONDS).until(() -> !wireMockRule.findAll(com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern().withUrl("/v2/notifications/email")).isEmpty());
        wireMockRule.verify(1, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/v2/notifications/email")).withHeader(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, com.github.tomakehurst.wiremock.client.WireMock.equalTo(javax.ws.rs.core.MediaType.APPLICATION_JSON)).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.email_address", com.github.tomakehurst.wiremock.client.WireMock.equalTo(adminUserEmail))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.template_id", com.github.tomakehurst.wiremock.client.WireMock.equalTo("pay-notify-stripe-dispute-created-email-template-id"))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.email_reply_to_id", com.github.tomakehurst.wiremock.client.WireMock.equalTo("pay-notify-email-reply-to-support-id"))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.personalisation.disputeType", com.github.tomakehurst.wiremock.client.WireMock.equalTo(reason))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.personalisation.sendEvidenceToPayDueDate", com.github.tomakehurst.wiremock.client.WireMock.equalTo("11 February 2022"))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.personalisation.paymentExternalId", com.github.tomakehurst.wiremock.client.WireMock.equalTo(parentResourceExternalId))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.personalisation.disputedAmount", com.github.tomakehurst.wiremock.client.WireMock.equalTo("65.00"))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.personalisation.serviceName", com.github.tomakehurst.wiremock.client.WireMock.equalTo(serviceName))).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.personalisation.organisationName", com.github.tomakehurst.wiremock.client.WireMock.equalTo(organisationName))));
    }

    public void setMessage(byte[] messageContents) {
        currentMessage = messageContents;
    }
}
