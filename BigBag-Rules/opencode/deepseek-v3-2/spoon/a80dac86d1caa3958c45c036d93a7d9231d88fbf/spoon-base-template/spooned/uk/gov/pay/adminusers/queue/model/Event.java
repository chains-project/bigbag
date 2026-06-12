package uk.gov.pay.adminusers.queue.model;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Event {
    private java.lang.String resourceExternalId;

    private java.lang.String parentResourceExternalId;

    private java.lang.String eventType;

    private com.fasterxml.jackson.databind.JsonNode eventDetails;

    private java.lang.String serviceId;

    private java.lang.Boolean live;

    public Event() {
        // for deserialization
    }

    public Event(java.lang.String resourceExternalId, java.lang.String parentResourceExternalId, java.lang.String eventType, com.fasterxml.jackson.databind.JsonNode eventDetails, java.lang.String serviceId, java.lang.Boolean live) {
        this.resourceExternalId = resourceExternalId;
        this.parentResourceExternalId = parentResourceExternalId;
        this.eventType = eventType;
        this.eventDetails = eventDetails;
        this.serviceId = serviceId;
        this.live = live;
    }

    public java.lang.String getResourceExternalId() {
        return resourceExternalId;
    }

    public java.lang.String getParentResourceExternalId() {
        return parentResourceExternalId;
    }

    public java.lang.String getEventType() {
        return eventType;
    }

    public com.fasterxml.jackson.databind.JsonNode getEventDetails() {
        return eventDetails;
    }

    public java.lang.String getServiceId() {
        return serviceId;
    }

    public java.lang.Boolean getLive() {
        return live;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return ((((((((((((((((("Event{" + "resourceExternalId='") + resourceExternalId) + '\'') + ", parentResourceExternalId='") + parentResourceExternalId) + '\'') + ", eventType='") + eventType) + '\'') + ", eventDetails='") + eventDetails) + '\'') + ", serviceId='") + serviceId) + '\'') + ", live=") + live) + '}';
    }

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o)
            return true;

        if ((o == null) || (getClass() != o.getClass()))
            return false;

        uk.gov.pay.adminusers.queue.model.Event event = ((uk.gov.pay.adminusers.queue.model.Event) (o));
        return ((((java.util.Objects.equals(resourceExternalId, event.resourceExternalId) && java.util.Objects.equals(parentResourceExternalId, event.parentResourceExternalId)) && java.util.Objects.equals(eventType, event.eventType)) && java.util.Objects.equals(eventDetails, event.eventDetails)) && java.util.Objects.equals(serviceId, event.serviceId)) && java.util.Objects.equals(live, event.live);
    }

    @java.lang.Override
    public int hashCode() {
        return java.util.Objects.hash(resourceExternalId, parentResourceExternalId, eventType, eventDetails, serviceId, live);
    }
}
