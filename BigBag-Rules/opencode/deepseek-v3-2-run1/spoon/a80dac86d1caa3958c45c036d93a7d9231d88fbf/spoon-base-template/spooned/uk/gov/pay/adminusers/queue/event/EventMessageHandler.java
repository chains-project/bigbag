package uk.gov.pay.adminusers.queue.event;
public class EventMessageHandler {
    private static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy");// 9 March 2022


    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    private final uk.gov.pay.adminusers.queue.event.EventSubscriberQueue eventSubscriberQueue;

    private final uk.gov.pay.adminusers.client.ledger.service.LedgerService ledgerService;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final uk.gov.pay.adminusers.service.ServiceFinder serviceFinder;

    private final uk.gov.pay.adminusers.service.UserServices userServices;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final java.lang.String NO = "no";

    private final java.lang.String YES = "yes";

    private final java.util.Map<java.lang.String, java.lang.String> disputeDefinitionDisplay = java.util.Map.of("credit_not_processed", NO, "duplicate", NO, "fraudulent", NO, "product_not_received", NO, "product_unacceptable", NO, "subscription_canceled", NO, "unrecognized", NO);

    @javax.inject.Inject
    public EventMessageHandler(uk.gov.pay.adminusers.queue.event.EventSubscriberQueue eventSubscriberQueue, uk.gov.pay.adminusers.client.ledger.service.LedgerService ledgerService, uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.service.ServiceFinder serviceFinder, uk.gov.pay.adminusers.service.UserServices userServices, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.eventSubscriberQueue = eventSubscriberQueue;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
        this.serviceFinder = serviceFinder;
        this.userServices = userServices;
        this.objectMapper = objectMapper;
    }

    public void processMessages() throws uk.gov.service.payments.commons.queue.exception.QueueException {
        java.util.List<uk.gov.pay.adminusers.queue.model.EventMessage> eventMessages = eventSubscriberQueue.retrieveEvents();
        for (uk.gov.pay.adminusers.queue.model.EventMessage message : eventMessages) {
            try {
                uk.gov.pay.adminusers.queue.model.EventType eventType = uk.gov.pay.adminusers.queue.model.EventType.byType(message.getEvent().getEventType());
                logger.info("Retrieved event queue message with id [{}] for resource external id [{}]", message.getQueueMessage().getMessageId(), message.getEvent().getResourceExternalId());
                switch (eventType) {
                    case DISPUTE_CREATED :
                        handleDisputeCreatedMessage(message.getEvent());
                        break;
                    case DISPUTE_EVIDENCE_SUBMITTED :
                        handleDisputeEvidenceSubmittedMessage(message.getEvent());
                        break;
                    case DISPUTE_LOST :
                        handleDisputeLostMessage(message.getEvent());
                        break;
                    case DISPUTE_WON :
                        handleDisputeWonMessage(message.getEvent());
                        break;
                    default :
                        logger.info("Unknown event type: {}", message.getEvent().getEventType());
                }
                eventSubscriberQueue.markMessageAsProcessed(message.getQueueMessage());
            } catch (java.lang.Exception e) {
                io.sentry.Sentry.captureException(e);
                logger.warn("An error occurred handling the event message", net.logstash.logback.argument.StructuredArguments.kv("sqs_message_id", message.getQueueMessage().getMessageId()), net.logstash.logback.argument.StructuredArguments.kv("resource_external_id", message.getEvent().getResourceExternalId()), net.logstash.logback.argument.StructuredArguments.kv("error", e.getMessage()));
            }
        }
    }

    private void handleDisputeEvidenceSubmittedMessage(uk.gov.pay.adminusers.queue.model.Event disputeEvidenceSubmittedEvent) throws com.fasterxml.jackson.core.JsonProcessingException {
        try {
            setupMDC(disputeEvidenceSubmittedEvent);
            var disputeEvidenceSubmittedDetails = objectMapper.treeToValue(disputeEvidenceSubmittedEvent.getEventDetails(), uk.gov.pay.adminusers.queue.model.event.DisputeEvidenceSubmittedDetails.class);
            org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID, disputeEvidenceSubmittedDetails.getGatewayAccountId());
            java.util.Map<java.lang.String, java.lang.String> personalisation = getMinimumRequiredPersonalisation(disputeEvidenceSubmittedDetails.getGatewayAccountId(), disputeEvidenceSubmittedEvent.getParentResourceExternalId());
            sendEmailNotificationToServiceAdmins(disputeEvidenceSubmittedEvent.getEventType(), disputeEvidenceSubmittedDetails.getGatewayAccountId(), personalisation);
        } finally {
            tearDownMDC();
        }
    }

    private void handleDisputeWonMessage(uk.gov.pay.adminusers.queue.model.Event disputeWonEvent) throws com.fasterxml.jackson.core.JsonProcessingException {
        try {
            setupMDC(disputeWonEvent);
            var disputeWonDetails = objectMapper.treeToValue(disputeWonEvent.getEventDetails(), uk.gov.pay.adminusers.queue.model.event.DisputeWonDetails.class);
            org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID, disputeWonDetails.getGatewayAccountId());
            java.util.Map<java.lang.String, java.lang.String> personalisation = getMinimumRequiredPersonalisation(disputeWonDetails.getGatewayAccountId(), disputeWonEvent.getParentResourceExternalId());
            sendEmailNotificationToServiceAdmins(disputeWonEvent.getEventType(), disputeWonDetails.getGatewayAccountId(), personalisation);
        } finally {
            tearDownMDC();
        }
    }

    private void handleDisputeLostMessage(uk.gov.pay.adminusers.queue.model.Event disputeLostEvent) throws com.fasterxml.jackson.core.JsonProcessingException {
        try {
            setupMDC(disputeLostEvent);
            var disputeLostDetails = objectMapper.treeToValue(disputeLostEvent.getEventDetails(), uk.gov.pay.adminusers.queue.model.event.DisputeLostDetails.class);
            org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID, disputeLostDetails.getGatewayAccountId());
            java.util.Map<java.lang.String, java.lang.String> personalisation = getPersonalisationForDisputeLost(disputeLostDetails, disputeLostEvent.getParentResourceExternalId());
            sendEmailNotificationToServiceAdmins(disputeLostEvent.getEventType(), disputeLostDetails.getGatewayAccountId(), personalisation);
        } finally {
            tearDownMDC();
        }
    }

    private java.util.Map<java.lang.String, java.lang.String> getMinimumRequiredPersonalisation(java.lang.String gatewayAccountId, java.lang.String parentResourceExternalId) {
        uk.gov.pay.adminusers.model.Service service = getService(gatewayAccountId);
        uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction transaction = getTransaction(parentResourceExternalId);
        java.lang.String organisationName = ((service.getMerchantDetails() != null) && (service.getMerchantDetails().getName() != null)) ? service.getMerchantDetails().getName() : service.getName();
        return new java.util.HashMap<>(java.util.Map.of("organisationName", organisationName, "serviceName", service.getName(), "serviceReference", transaction.getReference()));
    }

    private java.util.Map<java.lang.String, java.lang.String> getPersonalisationForDisputeLost(uk.gov.pay.adminusers.queue.model.event.DisputeLostDetails details, java.lang.String parentResourceExternalId) {
        var personalisation = getMinimumRequiredPersonalisation(details.getGatewayAccountId(), parentResourceExternalId);
        if (details.getFee() != null) {
            personalisation.put("disputedAmount", uk.gov.pay.adminusers.utils.currency.ConvertToCurrency.convertPenceToPounds.apply(details.getAmount()).toString());
            personalisation.put("disputeFee", uk.gov.pay.adminusers.utils.currency.ConvertToCurrency.convertPenceToPounds.apply(details.getFee()).toString());
        }
        return personalisation;
    }

    private void handleDisputeCreatedMessage(uk.gov.pay.adminusers.queue.model.Event disputeCreatedEvent) throws com.fasterxml.jackson.core.JsonProcessingException {
        try {
            setupMDC(disputeCreatedEvent);
            var disputeCreatedDetails = objectMapper.treeToValue(disputeCreatedEvent.getEventDetails(), uk.gov.pay.adminusers.queue.model.event.DisputeCreatedDetails.class);
            org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID, disputeCreatedDetails.getGatewayAccountId());
            java.lang.String formattedDueDate = disputeCreatedDetails.getEvidenceDueDate().format(uk.gov.pay.adminusers.queue.event.EventMessageHandler.DATE_TIME_FORMATTER);
            java.lang.String formattedPayDueDate = uk.gov.pay.adminusers.utils.date.DisputeEvidenceDueByDateUtil.getPayDueByDate(disputeCreatedDetails.getEvidenceDueDate()).format(uk.gov.pay.adminusers.queue.event.EventMessageHandler.DATE_TIME_FORMATTER);
            java.util.Map<java.lang.String, java.lang.String> personalisation = getMinimumRequiredPersonalisation(disputeCreatedDetails.getGatewayAccountId(), disputeCreatedEvent.getParentResourceExternalId());
            personalisation.put("paymentExternalId", disputeCreatedEvent.getParentResourceExternalId());
            personalisation.put("disputedAmount", uk.gov.pay.adminusers.utils.currency.ConvertToCurrency.convertPenceToPounds.apply(disputeCreatedDetails.getAmount()).toString());
            personalisation.put("sendEvidenceToPayDueDate", formattedPayDueDate);
            personalisation.put("disputeType", uk.gov.pay.adminusers.utils.dispute.DisputeReasonMapper.mapToNotifyEmail(disputeCreatedDetails.getReason()));
            personalisation.putAll(disputeDefinitionDisplay);
            personalisation.replace(disputeCreatedDetails.getReason(), YES);
            sendEmailNotificationToServiceAdmins(disputeCreatedEvent.getEventType(), disputeCreatedDetails.getGatewayAccountId(), personalisation);
        } finally {
            tearDownMDC();
        }
    }

    private uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction getTransaction(java.lang.String parentResourceExternalId) {
        return ledgerService.getTransaction(parentResourceExternalId).orElseThrow(() -> new java.lang.IllegalArgumentException(java.lang.String.format("Transaction not found [payment_external_id: %s]", parentResourceExternalId)));
    }

    private uk.gov.pay.adminusers.model.Service getService(java.lang.String gatewayAccountId) {
        return serviceFinder.byGatewayAccountId(gatewayAccountId).orElseThrow(() -> new java.lang.IllegalArgumentException(java.lang.String.format("Service not found [gateway_account_id: %s]", gatewayAccountId)));
    }

    private void sendEmailNotificationToServiceAdmins(java.lang.String eventType, java.lang.String gatewayAccountId, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        uk.gov.pay.adminusers.model.Service service = getService(gatewayAccountId);
        java.util.List<uk.gov.pay.adminusers.persistence.entity.UserEntity> serviceAdmins = userServices.getAdminUsersForService(service.getId());
        if (!serviceAdmins.isEmpty()) {
            sendDisputeEmailForEvent(eventType, serviceAdmins.stream().map(uk.gov.pay.adminusers.persistence.entity.UserEntity::getEmail).collect(java.util.stream.Collectors.toSet()), personalisation);
            logger.info("Processed notification email for disputed transaction");
        } else {
            throw new java.lang.IllegalStateException(java.lang.String.format("Service has no Admin users [external_id: %s]", service.getExternalId()));
        }
    }

    private void sendDisputeEmailForEvent(java.lang.String eventType, java.util.Set<java.lang.String> adminEmails, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        uk.gov.pay.adminusers.queue.model.EventType disputeEventType = uk.gov.pay.adminusers.queue.model.EventType.valueOf(eventType.toUpperCase());
        switch (disputeEventType) {
            case DISPUTE_CREATED :
                notificationService.sendStripeDisputeCreatedEmail(adminEmails, personalisation);
                break;
            case DISPUTE_LOST :
                notificationService.sendStripeDisputeLostEmail(adminEmails, personalisation);
                break;
            case DISPUTE_WON :
                notificationService.sendStripeDisputeWonEmail(adminEmails, personalisation);
                break;
            case DISPUTE_EVIDENCE_SUBMITTED :
                notificationService.sendStripeDisputeEvidenceSubmittedEmail(adminEmails, personalisation);
                break;
            default :
                logger.warn("Unknown event type: {}", eventType);
        }
    }

    private void setupMDC(uk.gov.pay.adminusers.queue.model.Event event) {
        org.slf4j.MDC.put("dispute_external_id", event.getResourceExternalId());
        org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID, event.getParentResourceExternalId());
        org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID, event.getServiceId());
        org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE, event.getEventType());
    }

    private void tearDownMDC() {
        java.util.List.of(uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID, uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID, uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID, "dispute_external_id", uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE).forEach(org.slf4j.MDC::remove);
    }
}
