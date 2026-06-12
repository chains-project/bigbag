package uk.gov.pay.adminusers.queue.model;
public class EventMessage {
    private uk.gov.pay.adminusers.queue.model.Event event;

    private uk.gov.service.payments.commons.queue.model.QueueMessage queueMessage;

    public EventMessage(uk.gov.pay.adminusers.queue.model.Event event, uk.gov.service.payments.commons.queue.model.QueueMessage queueMessage) {
        this.event = event;
        this.queueMessage = queueMessage;
    }

    public static uk.gov.pay.adminusers.queue.model.EventMessage of(uk.gov.pay.adminusers.queue.model.Event event, uk.gov.service.payments.commons.queue.model.QueueMessage queueMessage) {
        return new uk.gov.pay.adminusers.queue.model.EventMessage(event, queueMessage);
    }

    public uk.gov.service.payments.commons.queue.model.QueueMessage getQueueMessage() {
        return queueMessage;
    }

    public uk.gov.pay.adminusers.queue.model.Event getEvent() {
        return event;
    }
}
