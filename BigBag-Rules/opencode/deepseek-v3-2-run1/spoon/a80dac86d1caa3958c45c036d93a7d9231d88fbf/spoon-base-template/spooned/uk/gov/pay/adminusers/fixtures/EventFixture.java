package uk.gov.pay.adminusers.fixtures;
public class EventFixture {
    private java.lang.String resourceExternalId = "a-resource-external-id";

    private java.lang.String parentResourceExternalId;

    private java.lang.String eventType = "AN_EVENT_TYPE";

    private com.fasterxml.jackson.databind.JsonNode eventDetails;

    private java.lang.String serviceId = "service_id";

    private java.lang.Boolean live = false;

    private EventFixture() {
    }

    public static uk.gov.pay.adminusers.fixtures.EventFixture anEventFixture() {
        return new uk.gov.pay.adminusers.fixtures.EventFixture();
    }

    public uk.gov.pay.adminusers.fixtures.EventFixture withResourceExternalId(java.lang.String resourceExternalId) {
        this.resourceExternalId = resourceExternalId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.EventFixture withParentResourceExternalId(java.lang.String parentResourceExternalId) {
        this.parentResourceExternalId = parentResourceExternalId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.EventFixture withEventType(java.lang.String eventType) {
        this.eventType = eventType;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.EventFixture withEventDetails(com.fasterxml.jackson.databind.JsonNode eventDetails) {
        this.eventDetails = eventDetails;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.EventFixture withServiceId(java.lang.String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.EventFixture withLive(java.lang.Boolean live) {
        this.live = live;
        return this;
    }

    public uk.gov.pay.adminusers.queue.model.Event build() {
        return new uk.gov.pay.adminusers.queue.model.Event(resourceExternalId, parentResourceExternalId, eventType, eventDetails, serviceId, live);
    }

    public au.com.dius.pact.consumer.dsl.PactDslJsonBody getAsPact() {
        return uk.gov.pay.adminusers.fixtures.EventFixtureUtil.getAsPact(serviceId, live, eventType, resourceExternalId, parentResourceExternalId, eventDetails);
    }
}
