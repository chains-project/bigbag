package uk.gov.pay.adminusers.queue.event;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class EventMessageHandlerTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.queue.event.EventSubscriberQueue mockEventSubscriberQueue;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.NotificationService mockNotificationService;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.ServiceFinder mockServiceFinder;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.service.UserServices mockUserServices;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.client.ledger.service.LedgerService mockLedgerService;

    @org.mockito.Captor
    org.mockito.ArgumentCaptor<java.util.Set<java.lang.String>> adminEmailsCaptor;

    @org.mockito.Captor
    org.mockito.ArgumentCaptor<java.util.Map<java.lang.String, java.lang.String>> personalisationCaptor;

    @org.mockito.Mock
    private ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> mockLogAppender;

    @org.mockito.Captor
    org.mockito.ArgumentCaptor<ch.qos.logback.classic.spi.ILoggingEvent> loggingEventArgumentCaptor;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final java.lang.String gatewayAccountId = "123";

    private uk.gov.pay.adminusers.queue.event.EventMessageHandler eventMessageHandler;

    private uk.gov.pay.adminusers.model.Service service;

    private uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction transaction;

    private java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> users;

    private uk.gov.pay.adminusers.queue.model.Event disputeEvent;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        eventMessageHandler = new uk.gov.pay.adminusers.queue.event.EventMessageHandler(mockEventSubscriberQueue, mockLedgerService, mockNotificationService, mockServiceFinder, mockUserServices, objectMapper);
        service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        service.setMerchantDetails(new uk.gov.pay.adminusers.model.MerchantDetails("Organisation Name", null, null, null, null, null, null, null, null));
        transaction = uk.gov.pay.adminusers.fixtures.LedgerTransactionFixture.aLedgerTransactionFixture().withTransactionId("456").withReference("tx ref").build();
        users = java.util.Arrays.asList(uk.gov.pay.adminusers.service.UserServicesTest.aUserEntityWithRoleForService(service, true, "admin1"), uk.gov.pay.adminusers.service.UserServicesTest.aUserEntityWithRoleForService(service, true, "admin2"));
        ch.qos.logback.classic.Logger logger = ((ch.qos.logback.classic.Logger) (org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.queue.event.EventMessageHandler.class)));
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.addAppender(mockLogAppender);
    }

    @org.junit.jupiter.api.Test
    void shouldMarkMessageAsProcessed() throws java.lang.Exception {
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("amount", 21000L, "evidence_due_date", "2022-03-07T13:00:00.001Z", "gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").build();
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.of(transaction));
        org.mockito.Mockito.when(mockUserServices.getAdminUsersForService(service.getId())).thenReturn(users);
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockEventSubscriberQueue).markMessageAsProcessed(mockQueueMessage);
    }

    @org.junit.jupiter.api.Test
    void shouldHandleDisputeCreatedEvent() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("amount", 21000L, "evidence_due_date", "2022-03-07T13:00:00.001Z", "gateway_account_id", gatewayAccountId, "reason", "fraudulent"))).withParentResourceExternalId("456").build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockQueueMessage.getMessageId()).thenReturn("queue-message-id");
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.of(transaction));
        org.mockito.Mockito.when(mockUserServices.getAdminUsersForService(service.getId())).thenReturn(users);
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.atMostOnce()).sendStripeDisputeCreatedEmail(adminEmailsCaptor.capture(), personalisationCaptor.capture());
        var emails = adminEmailsCaptor.getValue();
        var personalisation = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(emails.size(), org.hamcrest.Matchers.is(2));
        org.hamcrest.MatcherAssert.assertThat(emails, org.hamcrest.Matchers.hasItems("admin1@service.gov.uk", "admin2@service.gov.uk"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceName"), org.hamcrest.Matchers.is(service.getName()));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("paymentExternalId"), org.hamcrest.Matchers.is("456"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceReference"), org.hamcrest.Matchers.is("tx ref"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("sendEvidenceToPayDueDate"), org.hamcrest.Matchers.is("4 March 2022"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("disputedAmount"), org.hamcrest.Matchers.is("210.00"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("fraudulent"), org.hamcrest.Matchers.is("yes"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("duplicate"), org.hamcrest.Matchers.is("no"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("credit_not_processed"), org.hamcrest.Matchers.is("no"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("product_not_received"), org.hamcrest.Matchers.is("no"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("product_unacceptable"), org.hamcrest.Matchers.is("no"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("subscription_canceled"), org.hamcrest.Matchers.is("no"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("unrecognized"), org.hamcrest.Matchers.is("no"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("paymentAmount"), org.hamcrest.Matchers.is(org.hamcrest.Matchers.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("disputeEvidenceDueDate"), org.hamcrest.Matchers.is(org.hamcrest.Matchers.nullValue()));
        org.mockito.Mockito.verify(mockLogAppender, org.mockito.Mockito.times(2)).doAppend(loggingEventArgumentCaptor.capture());
        java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(0).getFormattedMessage(), org.hamcrest.core.Is.is("Retrieved event queue message with id [queue-message-id] for resource external id [a-resource-external-id]"));
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(1).getFormattedMessage(), org.hamcrest.core.Is.is("Processed notification email for disputed transaction"));
    }

    @org.junit.jupiter.api.Test
    void shouldHandleDisputeLostEvent() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_LOST.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("net_amount", -4000L, "fee", 1500L, "amount", 2500L, "gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").withServiceId(service.getExternalId()).withLive(true).build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockQueueMessage.getMessageId()).thenReturn("queue-message-id");
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.of(transaction));
        org.mockito.Mockito.when(mockUserServices.getAdminUsersForService(service.getId())).thenReturn(users);
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.atMostOnce()).sendStripeDisputeLostEmail(adminEmailsCaptor.capture(), personalisationCaptor.capture());
        var emails = adminEmailsCaptor.getValue();
        var personalisation = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(emails.size(), org.hamcrest.Matchers.is(2));
        org.hamcrest.MatcherAssert.assertThat(emails, org.hamcrest.Matchers.hasItems("admin1@service.gov.uk", "admin2@service.gov.uk"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceName"), org.hamcrest.Matchers.is(service.getName()));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceReference"), org.hamcrest.Matchers.is("tx ref"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("organisationName"), org.hamcrest.Matchers.is(service.getMerchantDetails().getName()));
        org.mockito.Mockito.verify(mockLogAppender, org.mockito.Mockito.times(2)).doAppend(loggingEventArgumentCaptor.capture());
        java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(0).getFormattedMessage(), org.hamcrest.core.Is.is("Retrieved event queue message with id [queue-message-id] for resource external id [a-resource-external-id]"));
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(1).getFormattedMessage(), org.hamcrest.core.Is.is("Processed notification email for disputed transaction"));
    }

    @org.junit.jupiter.api.Test
    void shouldHandleDisputeWonEvent() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_WON.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").withServiceId(service.getExternalId()).withLive(true).build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockQueueMessage.getMessageId()).thenReturn("queue-message-id");
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.of(transaction));
        org.mockito.Mockito.when(mockUserServices.getAdminUsersForService(service.getId())).thenReturn(users);
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.atMostOnce()).sendStripeDisputeWonEmail(adminEmailsCaptor.capture(), personalisationCaptor.capture());
        var emails = adminEmailsCaptor.getValue();
        var personalisation = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(emails.size(), org.hamcrest.Matchers.is(2));
        org.hamcrest.MatcherAssert.assertThat(emails, org.hamcrest.Matchers.hasItems("admin1@service.gov.uk", "admin2@service.gov.uk"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceName"), org.hamcrest.Matchers.is(service.getName()));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceReference"), org.hamcrest.Matchers.is("tx ref"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("organisationName"), org.hamcrest.Matchers.is(service.getMerchantDetails().getName()));
        org.mockito.Mockito.verify(mockLogAppender, org.mockito.Mockito.times(2)).doAppend(loggingEventArgumentCaptor.capture());
        java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(0).getFormattedMessage(), org.hamcrest.core.Is.is("Retrieved event queue message with id [queue-message-id] for resource external id [a-resource-external-id]"));
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(1).getFormattedMessage(), org.hamcrest.core.Is.is("Processed notification email for disputed transaction"));
    }

    @org.junit.jupiter.api.Test
    void shouldHandleDisputeEvidenceSubmittedEvent() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_EVIDENCE_SUBMITTED.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").withServiceId(service.getExternalId()).withLive(true).build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockQueueMessage.getMessageId()).thenReturn("queue-message-id");
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.of(transaction));
        org.mockito.Mockito.when(mockUserServices.getAdminUsersForService(service.getId())).thenReturn(users);
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.atMostOnce()).sendStripeDisputeEvidenceSubmittedEmail(adminEmailsCaptor.capture(), personalisationCaptor.capture());
        var emails = adminEmailsCaptor.getValue();
        var personalisation = personalisationCaptor.getValue();
        org.hamcrest.MatcherAssert.assertThat(emails.size(), org.hamcrest.Matchers.is(2));
        org.hamcrest.MatcherAssert.assertThat(emails, org.hamcrest.Matchers.hasItems("admin1@service.gov.uk", "admin2@service.gov.uk"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceName"), org.hamcrest.Matchers.is(service.getName()));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("serviceReference"), org.hamcrest.Matchers.is("tx ref"));
        org.hamcrest.MatcherAssert.assertThat(personalisation.get("organisationName"), org.hamcrest.Matchers.is(service.getMerchantDetails().getName()));
        org.mockito.Mockito.verify(mockLogAppender, org.mockito.Mockito.times(2)).doAppend(loggingEventArgumentCaptor.capture());
        java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(0).getFormattedMessage(), org.hamcrest.core.Is.is("Retrieved event queue message with id [queue-message-id] for resource external id [a-resource-external-id]"));
        org.hamcrest.MatcherAssert.assertThat(logStatement.get(1).getFormattedMessage(), org.hamcrest.core.Is.is("Processed notification email for disputed transaction"));
    }

    @org.junit.jupiter.api.Test
    void shouldNotCallNotificationServiceWhenServiceDoesNotExist() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("amount", 21000L, "fee", 1500L, "evidence_due_date", "2022-03-07T13:00:00Z", "gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.empty());
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.never()).sendStripeDisputeCreatedEmail(org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyMap());
    }

    @org.junit.jupiter.api.Test
    void shouldNotCallNotificationServiceWhenTransactionDoesNotExist() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("amount", 21000L, "fee", 1500L, "evidence_due_date", "2022-03-07T13:00:00.001Z", "gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.empty());
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.never()).sendStripeDisputeCreatedEmail(org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyMap());
    }

    @org.junit.jupiter.api.Test
    void shouldNotCallNotificationServiceWhenNoAdminUsersExist() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        var mockQueueMessage = org.mockito.Mockito.mock(uk.gov.service.payments.commons.queue.model.QueueMessage.class);
        disputeEvent = uk.gov.pay.adminusers.fixtures.EventFixture.anEventFixture().withEventType(uk.gov.pay.adminusers.queue.model.EventType.DISPUTE_CREATED.name()).withEventDetails(objectMapper.valueToTree(java.util.Map.of("amount", 21000L, "fee", 1500L, "evidence_due_date", "2022-03-07T13:00:00.001Z", "gateway_account_id", gatewayAccountId))).withParentResourceExternalId("456").build();
        var eventMessage = uk.gov.pay.adminusers.queue.model.EventMessage.of(disputeEvent, mockQueueMessage);
        org.mockito.Mockito.when(mockEventSubscriberQueue.retrieveEvents()).thenReturn(java.util.List.of(eventMessage));
        org.mockito.Mockito.when(mockServiceFinder.byGatewayAccountId(gatewayAccountId)).thenReturn(java.util.Optional.of(service));
        org.mockito.Mockito.when(mockLedgerService.getTransaction(transaction.getTransactionId())).thenReturn(java.util.Optional.of(transaction));
        org.mockito.Mockito.when(mockUserServices.getAdminUsersForService(service.getId())).thenReturn(java.util.Collections.emptyList());
        eventMessageHandler.processMessages();
        org.mockito.Mockito.verify(mockNotificationService, org.mockito.Mockito.never()).sendStripeDisputeCreatedEmail(org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyMap());
    }
}
