package uk.gov.pay.adminusers.fixtures;
public class EventFixtureUtil {
    public static java.lang.String insert(com.amazonaws.services.sqs.AmazonSQS sqsClient, java.lang.String eventType, java.lang.String serviceId, java.lang.Boolean live, java.lang.String resourceExternalId, java.lang.String parentResourceExternalId, java.lang.String eventData) {
        java.lang.String messageBody = java.lang.String.format((((((("{" + "\"resource_external_id\": \"%s\",") + "\"service_id\": \"%s\",") + "\"live\": \"%s\",") + (org.apache.commons.lang3.StringUtils.isBlank(parentResourceExternalId) ? "%s" : "\"parent_resource_external_id\": \"%s\",")) + "\"event_type\":\"%s\",") + "\"event_details\": %s") + "}", resourceExternalId, serviceId, live, parentResourceExternalId == null ? "" : parentResourceExternalId, eventType, eventData);
        com.amazonaws.services.sqs.model.SendMessageResult result = sqsClient.sendMessage(uk.gov.pay.adminusers.infra.SqsTestDocker.getQueueUrl("event-queue"), messageBody);
        return result.getMessageId();
    }

    public static au.com.dius.pact.consumer.dsl.PactDslJsonBody getAsPact(java.lang.String serviceId, java.lang.Boolean live, java.lang.String eventType, java.lang.String resourceExternalId, java.lang.String parentResourceExternalId, com.fasterxml.jackson.databind.JsonNode eventData) {
        au.com.dius.pact.consumer.dsl.PactDslJsonBody message = new au.com.dius.pact.consumer.dsl.PactDslJsonBody();
        message.stringType("event_type", eventType);
        message.stringType("resource_external_id", resourceExternalId);
        message.booleanType("live", live);
        if (!org.apache.commons.lang3.StringUtils.isBlank(parentResourceExternalId)) {
            message.stringType("parent_resource_external_id", parentResourceExternalId);
        }
        if (!org.apache.commons.lang3.StringUtils.isBlank(serviceId)) {
            message.stringType("service_id", serviceId);
        }
        if (live != null) {
            message.booleanType("live", live);
        }
        au.com.dius.pact.consumer.dsl.PactDslJsonBody eventDetailsPact = uk.gov.pay.adminusers.fixtures.EventFixtureUtil.getNestedPact(eventData);
        message.object("event_details", eventDetailsPact);
        return message;
    }

    private static au.com.dius.pact.consumer.dsl.PactDslJsonBody getNestedPact(com.fasterxml.jackson.databind.JsonNode eventData) {
        au.com.dius.pact.consumer.dsl.PactDslJsonBody dslJsonBody = new au.com.dius.pact.consumer.dsl.PactDslJsonBody();
        eventData.fields().forEachRemaining(e -> {
            try {
                if (e.getValue().isObject()) {
                    dslJsonBody.object(e.getKey(), uk.gov.pay.adminusers.fixtures.EventFixtureUtil.getNestedPact(e.getValue()));
                } else if (e.getValue().isArray()) {
                    // We're currently only adding a single example from an array to the pact, and then in the
                    // matchers check that the array has at least one entry matching the example. For stricter
                    // matching, this would need to be modified.
                    com.fasterxml.jackson.databind.node.ArrayNode asJsonArray = ((com.fasterxml.jackson.databind.node.ArrayNode) (e.getValue()));
                    au.com.dius.pact.consumer.dsl.PactDslJsonBody arrayEntryExample = dslJsonBody.minArrayLike(e.getKey(), 1);
                    if (asJsonArray.get(0).isObject()) {
                        asJsonArray.get(0).fields().forEachRemaining(a -> {
                            if (a.getValue().isNumber()) {
                                arrayEntryExample.integerType(a.getKey(), a.getValue().intValue());
                            } else if (a.getValue().isBoolean()) {
                                arrayEntryExample.booleanType(a.getKey(), a.getValue().booleanValue());
                            } else {
                                arrayEntryExample.stringType(a.getKey(), a.getValue().textValue());
                            }
                        });
                    } else {
                        throw new org.apache.commons.lang3.NotImplementedException();
                    }
                    arrayEntryExample.closeObject().closeArray();
                } else if (e.getValue().isNumber()) {
                    dslJsonBody.integerType(e.getKey(), e.getValue().intValue());
                } else if (e.getValue().isBoolean()) {
                    dslJsonBody.booleanType(e.getKey(), e.getValue().booleanValue());
                } else {
                    dslJsonBody.stringType(e.getKey(), e.getValue().textValue());
                }
            } catch (java.lang.Exception ex) {
                dslJsonBody.stringType(e.getKey(), e.getValue().textValue());
            }
        });
        return dslJsonBody;
    }
}
