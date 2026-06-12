package uk.gov.pay.adminusers.queue.event;
public class EventSubscriberQueue extends uk.gov.service.payments.commons.queue.sqs.AbstractQueue {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    @javax.inject.Inject
    public EventSubscriberQueue(uk.gov.service.payments.commons.queue.sqs.SqsQueueService sqsQueueService, uk.gov.pay.adminusers.app.config.AdminUsersConfig adminUsersConfig, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        super(sqsQueueService, objectMapper, adminUsersConfig.getSqsConfig().getEventSubscriberQueueUrl(), adminUsersConfig.getEventSubscriberQueueConfig().getFailedMessageRetryDelayInSeconds());
    }

    public java.util.List<uk.gov.pay.adminusers.queue.model.EventMessage> retrieveEvents() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        java.util.List<uk.gov.service.payments.commons.queue.model.QueueMessage> queueMessages = retrieveMessages();
        return queueMessages.stream().map(this::deserializeMessage).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
    }

    private uk.gov.pay.adminusers.queue.model.EventMessage deserializeMessage(uk.gov.service.payments.commons.queue.model.QueueMessage queueMessage) {
        try {
            uk.gov.pay.adminusers.queue.model.SNSMessage snsMessage = objectMapper.readValue(queueMessage.getMessageBody(), uk.gov.pay.adminusers.queue.model.SNSMessage.class);
            uk.gov.pay.adminusers.queue.model.Event event = objectMapper.readValue(snsMessage.getMessage(), uk.gov.pay.adminusers.queue.model.Event.class);
            return uk.gov.pay.adminusers.queue.model.EventMessage.of(event, queueMessage);
        } catch (java.io.IOException e) {
            logger.warn("There was an exception parsing message [messageId={}] into an [{}]", queueMessage.getMessageId(), uk.gov.pay.adminusers.queue.model.EventMessage.class);
            return null;
        }
    }
}
