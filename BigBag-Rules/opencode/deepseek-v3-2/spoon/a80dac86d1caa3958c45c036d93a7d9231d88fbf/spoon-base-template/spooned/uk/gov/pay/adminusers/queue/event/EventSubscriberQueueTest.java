package uk.gov.pay.adminusers.queue.event;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class EventSubscriberQueueTest {
    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.AdminUsersConfig adminUsersConfig;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.SqsConfig sqsConfig;

    @org.mockito.Mock
    private uk.gov.pay.adminusers.app.config.EventSubscriberQueueConfig eventSubscriberQueueConfig;

    @org.mockito.Mock
    private uk.gov.service.payments.commons.queue.sqs.SqsQueueService sqsQueueService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private uk.gov.pay.adminusers.queue.event.EventSubscriberQueue eventSubscriberQueue;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.when(sqsConfig.getEventSubscriberQueueUrl()).thenReturn("");
        org.mockito.Mockito.when(eventSubscriberQueueConfig.getFailedMessageRetryDelayInSeconds()).thenReturn(900);
        org.mockito.Mockito.when(adminUsersConfig.getSqsConfig()).thenReturn(sqsConfig);
        org.mockito.Mockito.when(adminUsersConfig.getEventSubscriberQueueConfig()).thenReturn(eventSubscriberQueueConfig);
        eventSubscriberQueue = new uk.gov.pay.adminusers.queue.event.EventSubscriberQueue(sqsQueueService, adminUsersConfig, objectMapper);
    }

    @org.junit.jupiter.api.Test
    void shouldRetrieveEventsForCorrectlyFormattedJSON() throws java.lang.Exception {
        java.lang.String message = uk.gov.pay.adminusers.TestTemplateResourceLoader.load(uk.gov.pay.adminusers.TestTemplateResourceLoader.DISPUTE_CREATED_SNS_MESSAGE);
        var sendMessageResult = org.mockito.Mockito.mock(com.amazonaws.services.sqs.model.SendMessageResult.class);
        java.util.List<uk.gov.service.payments.commons.queue.model.QueueMessage> messages = java.util.List.of(uk.gov.service.payments.commons.queue.model.QueueMessage.of(sendMessageResult, message));
        org.mockito.Mockito.when(sqsQueueService.receiveMessages(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(messages);
        java.util.List<uk.gov.pay.adminusers.queue.model.EventMessage> eventMessages = eventSubscriberQueue.retrieveEvents();
        org.hamcrest.MatcherAssert.assertThat(eventMessages, org.hamcrest.Matchers.hasSize(1));
        uk.gov.pay.adminusers.queue.model.Event event = eventMessages.get(0).getEvent();
        org.hamcrest.MatcherAssert.assertThat(event.getResourceExternalId(), org.hamcrest.Matchers.is("dp_1KfoljHj08j2jFuBkNEd89sd"));
        org.hamcrest.MatcherAssert.assertThat(event.getParentResourceExternalId(), org.hamcrest.Matchers.is("pk8vak8vfiii5hjvqpsa4dsd"));
        org.hamcrest.MatcherAssert.assertThat(event.getEventType(), org.hamcrest.Matchers.is("DISPUTE_CREATED"));
        org.hamcrest.MatcherAssert.assertThat(event.getEventDetails().toString(), org.hamcrest.Matchers.is("{\"fee\":1500,\"evidence_due_date\":1648684799,\"gateway_account_id\":\"528\",\"amount\":1000,\"net_amount\":2500,\"reason\":\"fraudulent\"}"));
    }
}
